package com.xiyouji.service.battle;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.combat.CombatRules;
import com.xiyouji.combat.EncounterCatalog;
import com.xiyouji.exception.BusinessException;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.Enemy;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.MapNode;
import com.xiyouji.port.CharacterRepositoryPort;
import com.xiyouji.port.EnemyRepositoryPort;
import com.xiyouji.service.CommandGuard;
import com.xiyouji.service.room.MultiplayerBattleState;
import com.xiyouji.service.room.MultiplayerBattleStore;
import com.xiyouji.service.room.MultiplayerPlayer;
import com.xiyouji.service.room.Room;
import com.xiyouji.service.room.RoomPlayer;
import com.xiyouji.service.room.RoomService;
import com.xiyouji.service.room.RoomStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 多人战斗启动器
 * 从地图战斗节点初始化一场战斗：校验房主/节点、从 RoomPlayer 持久化状态构建玩家角色、
 * 创建并缩放敌人、构建战斗状态并标记房间进入战斗。
 * 不负责 save/广播（由门面按 created 标记决定）。
 */
@Service
public class MultiplayerBattleStarter {

    private static final Logger log = LoggerFactory.getLogger(MultiplayerBattleStarter.class);

    private final RoomService roomService;
    private final MultiplayerBattleStore battleStore;
    private final CharacterRepositoryPort characterRepo;
    private final EnemyRepositoryPort enemyRepo;
    private final SecureRandom random = new SecureRandom();

    public MultiplayerBattleStarter(RoomService roomService,
                                    MultiplayerBattleStore battleStore,
                                    CharacterRepositoryPort characterRepo,
                                    EnemyRepositoryPort enemyRepo) {
        this.roomService = roomService;
        this.battleStore = battleStore;
        this.characterRepo = characterRepo;
        this.enemyRepo = enemyRepo;
    }

    /** 启动结果：构建的战斗状态 + 是否为本次新创建（false 表示重试短路返回了已有战斗） */
    public record StartResult(MultiplayerBattleState state, boolean created) {
    }

    /**
     * 在房间级锁内执行开场校验与战斗构建。调用方负责 save 与广播。
     */
    public StartResult start(String roomCode, String requesterId, long expectedVersion) {
        Room room = roomService.getRoomEntity(roomCode);
        if (expectedVersion >= 0) {
            CommandGuard.checkVersion("room:" + roomCode, expectedVersion, room.getStateVersion());
        }

        // 验证：必须是房主才能开始
        if (!room.getHostUserId().equals(requesterId)) {
            throw new InvalidActionException("只有房主才能开始战斗");
        }
        // 网络超时重试不得为同一房间创建第二场战斗。房间锁保证此检查与 save 原子。
        // 授权检查放在此短路之前，防止其他玩家通过 start 端点读取战斗。
        if (battleStore.exists(roomCode)) {
            return new StartResult(battleStore.get(roomCode), false);
        }
        // 验证：房间当前在地图探索阶段
        if (room.getStatus() != RoomStatus.IN_MAP) {
            throw new InvalidActionException("当前不在地图探索阶段，无法开始战斗");
        }
        // 验证：当前节点是战斗/Boss节点
        MapNode currentNode = room.getCurrentNode();
        if (currentNode == null || (!GameConstants.NODE_BATTLE.equals(currentNode.getType())
                && !GameConstants.NODE_BOSS.equals(currentNode.getType()))) {
            throw new InvalidActionException("当前节点不是战斗节点");
        }

        // 从持久化状态初始化每个玩家的角色
        List<MultiplayerPlayer> players = new ArrayList<>();
        for (var roomPlayer : room.getPlayers()) {
            GameCharacter gc = initCharacterFromRoomPlayer(roomPlayer);
            players.add(new MultiplayerPlayer(roomPlayer.getUserId(), roomPlayer.getUsername(), gc));
        }

        // 创建敌人：优先使用地图节点指定的enemyId
        Enemy enemy = createEnemyForNode(currentNode, room.getFloor(), players.size());
        enemy.setEncounterId(currentNode.getId());
        EnemyBehaviorGuard.validate(enemy);
        enemy.setRulesVersion(CombatRules.VERSION);

        // 构建战斗状态
        MultiplayerBattleState state = new MultiplayerBattleState(roomCode);
        state.setStoryInstanceId(roomCode + ":" + currentNode.getId());
        state.setStoryFloor(room.getFloor());
        state.setEnemy(enemy);
        state.setPlayers(players);
        state.setTurnNumber(1);
        state.setPlayerTurn(true);
        state.setBattleOver(false);
        state.setVictory(false);

        // 敌人选择初始意图和攻击目标
        MultiplayerTurnCoordinator.lockNextAction(state, random.nextLong());

        state.addLog("战斗开始！遭遇 " + enemy.getName());
        state.addLog("敌人意图攻击: " + players.get(state.getTargetPlayerIndex()).getUsername());

        // 标记房间为战斗中
        roomService.markInBattle(roomCode);

        log.info("Multiplayer battle started: room={}, players={}, node={}",
                roomCode, players.size(), currentNode.getId());
        return new StartResult(state, true);
    }

    /**
     * 从 RoomPlayer 的持久化状态初始化 GameCharacter
     * 继承HP、金币、牌组、遗物，而非每次满血重置。
     */
    private GameCharacter initCharacterFromRoomPlayer(RoomPlayer roomPlayer) {
        GameCharacter template = characterRepo.findByCharacterClass(roomPlayer.getCharacterClass())
                .orElseThrow(() -> new BusinessException("CHARACTER_NOT_FOUND",
                        "角色不存在: " + roomPlayer.getCharacterClass(), 404));

        GameCharacter gc = new GameCharacter();
        gc.setCharacterClass(roomPlayer.getCharacterClass());
        gc.setMaxHp(roomPlayer.getMaxHp() > 0 ? roomPlayer.getMaxHp() : template.getMaxHp());
        gc.setHp(roomPlayer.getHp() > 0 ? roomPlayer.getHp() : template.getMaxHp());
        gc.setGold(roomPlayer.getGold());
        gc.setMaxEnergy(GameConstants.MAX_ENERGY);
        gc.setFloor(0);

        // 继承牌组（深拷贝避免修改持久化数据）
        for (Card card : roomPlayer.getDeck()) {
            gc.addCard(card.copy());
        }

        // 继承遗物
        gc.getRelics().addAll(roomPlayer.getRelics());

        // 初始化战斗状态：回满能量、清空手牌/buff、构建抽牌堆并洗牌
        gc.initBattle();
        gc.addBlock(roomPlayer.getNextBattleBlock());

        // 设置初始能量并抽5张初始手牌
        gc.setEnergy(gc.getMaxEnergy());
        gc.drawCards(GameConstants.INITIAL_HAND_SIZE);

        return gc;
    }

    /**
     * 为地图节点创建敌人
     * 优先使用节点指定的enemyId，回退到按楼层随机选择
     */
    private Enemy createEnemyForNode(MapNode node, int floor, int playerCount) {
        // 优先使用节点指定的敌人
        if (node.getEnemyId() != null && !node.getEnemyId().isEmpty()) {
            try {
                Long enemyId = Long.valueOf(node.getEnemyId());
                Enemy template = EnemyBehaviorGuard.read(() -> enemyRepo.findById(enemyId)).orElse(null);
                if (template != null) {
                    Enemy combat = template.copy();
                    boolean isBoss = GameConstants.NODE_BOSS.equals(node.getType());
                    if (isBoss) {
                        combat.setBoss(true);
                    }
                    scaleEnemy(combat, playerCount);
                    return combat;
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid enemyId on node {}: {}", node.getId(), node.getEnemyId());
            }
        }

        // 回退：按楼层随机选择
        return createEnemyForRoom(floor, playerCount, GameConstants.NODE_BOSS.equals(node.getType()));
    }

    /**
     * 为房间创建敌人（回退用，按楼层随机选择）
     */
    private Enemy createEnemyForRoom(int floor, int playerCount, boolean boss) {
        Enemy template = new EncounterCatalog(EnemyBehaviorGuard.read(enemyRepo::findAll))
                .select(floor, boss, random.nextLong()).template();
        Enemy combat = template.copy();

        // 按玩家数量缩放
        scaleEnemy(combat, playerCount);

        return combat;
    }

    /** 按玩家数量缩放敌人属性（5人时HP×3，攻击+2） */
    private void scaleEnemy(Enemy enemy, int playerCount) {
        int hpScale = Math.max(1, playerCount - 1); // 5人→4→HP×(1+4×0.5)=3倍
        double multiplier = 1.0 + hpScale * 0.5;
        int scaledMaxHp = (int) (enemy.getMaxHp() * multiplier);
        enemy.setMaxHp(scaledMaxHp);
        enemy.setHp(scaledMaxHp);
        enemy.setAttack(enemy.getAttack() + Math.max(0, playerCount - 2));
    }
}
