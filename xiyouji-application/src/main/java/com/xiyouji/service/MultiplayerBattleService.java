package com.xiyouji.service;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.BusinessException;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.Enemy;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.enums.BuffType;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.model.enums.EnemyIntent;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.port.CharacterRepositoryPort;
import com.xiyouji.port.EnemyRepositoryPort;
import com.xiyouji.service.room.MultiplayerBattleState;
import com.xiyouji.service.room.MultiplayerBattleStore;
import com.xiyouji.service.room.MultiplayerPlayer;
import com.xiyouji.service.room.Room;
import com.xiyouji.service.room.RoomEventPublisher;
import com.xiyouji.service.room.RoomLockKeys;
import com.xiyouji.service.room.RoomPlayer;
import com.xiyouji.service.room.RoomService;
import com.xiyouji.service.room.DistributedLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

/**
 * 多人战斗服务
 *
 * 实现5人PvE协作战斗（唐僧师徒五人），参考杀戮尖塔2的"抢出牌"机制：
 *   - 所有存活玩家在同一回合内可自由出牌，谁先点击谁先出（FIFO抢占）
 *   - 使用按房间粒度的 Redisson 分布式锁保证线程安全，同时只有一张卡牌被结算
 *   - 玩家可等待队友先施加debuff再出牌以打出更高伤害
 *   - 每个玩家各自结束回合，所有存活玩家都结束后敌人执行回合
 *
 * 手牌隔离：每个玩家有独立的手牌/抽牌堆/弃牌堆
 * 敌人共享：所有玩家攻击同一敌人，debuff全局生效
 */
@Service
public class MultiplayerBattleService {

    private static final Logger log = LoggerFactory.getLogger(MultiplayerBattleService.class);

    /** 战斗日志最大条数 */
    private static final int MAX_LOG = 30;

    private final RoomService roomService;
    private final MultiplayerBattleStore battleStore;
    private final CharacterRepositoryPort characterRepo;
    private final CardRepositoryPort cardRepo;
    private final EnemyRepositoryPort enemyRepo;
    private final RoomEventPublisher broadcaster;
    private final DistributedLockService lockService;
    private final IdempotencyStore idempotencyStore;

    private final SecureRandom random = new SecureRandom();

    public MultiplayerBattleService(RoomService roomService,
                                    MultiplayerBattleStore battleStore,
                                    CharacterRepositoryPort characterRepo,
                                    CardRepositoryPort cardRepo,
                                    EnemyRepositoryPort enemyRepo,
                                    RoomEventPublisher broadcaster,
                                    DistributedLockService lockService,
                                    IdempotencyStore idempotencyStore) {
        this.roomService = roomService;
        this.battleStore = battleStore;
        this.characterRepo = characterRepo;
        this.cardRepo = cardRepo;
        this.enemyRepo = enemyRepo;
        this.broadcaster = broadcaster;
        this.lockService = lockService;
        this.idempotencyStore = idempotencyStore;
    }

    // ===== 公开API =====

    /**
     * 开始多人战斗
     * 从地图战斗节点触发，为每个玩家从持久化状态初始化角色。
     *
     * @param roomCode   房间码
     * @param requesterId 发起者用户ID（必须是房主）
     * @return 初始战斗状态
     */
    public MultiplayerBattleState startBattle(String roomCode, String requesterId) {
        return startBattle(roomCode, requesterId, -1, null);
    }

    /** Idempotent variant used by HTTP clients that send X-Idempotency-Key. */
    public MultiplayerBattleState startBattle(String roomCode, String requesterId, String idempotencyKey) {
        return startBattle(roomCode, requesterId, -1, idempotencyKey);
    }

    public MultiplayerBattleState startBattle(String roomCode, String requesterId,
                                              long expectedVersion, String idempotencyKey) {
        String key = commandKey("start", roomCode, requesterId, idempotencyKey);
        String fingerprint = CommandGuard.fingerprint("POST", "/multiplayer/battle/" + roomCode + "/start", "");
        IdempotencyStore.Entry entry = begin(key, fingerprint);
        if (entry != null && entry.completed()) {
            MultiplayerBattleState existing = battleStore.get(roomCode);
            if (existing != null) return existing;
        }
        try {
            MultiplayerBattleState result = withRoomLock(roomCode,
                    () -> startBattleUnderLock(roomCode, requesterId, expectedVersion));
            complete(key, fingerprint, "");
            return result;
        } catch (RuntimeException error) {
            abort(key);
            throw error;
        }
    }

    private MultiplayerBattleState startBattleUnderLock(String roomCode, String requesterId,
                                                        long expectedVersion) {
        Room room = roomService.getRoomEntity(roomCode);
        if (expectedVersion >= 0) {
            CommandGuard.checkVersion("room:" + roomCode, expectedVersion, room.getStateVersion());
        }

        // 验证：必须是房主才能开始
        if (!room.getHostUserId().equals(requesterId)) {
            throw new InvalidActionException("只有房主才能开始战斗");
        }
        // A retry after a network timeout must not create a second battle for
        // the same room. The room lock makes this check atomic with save().
        // Keep the authorization check before this shortcut so another player
        // cannot read a battle by calling the start endpoint.
        if (battleStore.exists(roomCode)) {
            return battleStore.get(roomCode);
        }
        // 验证：房间当前在地图探索阶段
        if (room.getStatus() != com.xiyouji.service.room.RoomStatus.IN_MAP) {
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

        // 构建战斗状态
        MultiplayerBattleState state = new MultiplayerBattleState(roomCode);
        state.setEnemy(enemy);
        state.setPlayers(players);
        state.setTurnNumber(1);
        state.setPlayerTurn(true);
        state.setBattleOver(false);
        state.setVictory(false);

        // 敌人选择初始意图和攻击目标
        enemy.chooseIntent();
        state.setTargetPlayerIndex(state.randomAlivePlayerIndex(random));

        state.addLog("战斗开始！遭遇 " + enemy.getName());
        state.addLog("敌人意图攻击: " + players.get(state.getTargetPlayerIndex()).getUsername());

        // 标记房间为战斗中
        roomService.markInBattle(roomCode);

        battleStore.save(state);
        broadcaster.broadcastBattleUpdate(roomCode, toBattleInfo(state));
        broadcaster.broadcastSystemMessage(roomCode, "战斗开始！");

        log.info("Multiplayer battle started: room={}, players={}, node={}",
                roomCode, players.size(), currentNode.getId());
        return state;
    }

    /**
     * 出牌（抢出牌机制：谁先请求谁先出）
     * 使用房间级分布式锁保证同一房间同时只有一张卡牌被结算。
     *
     * @param roomCode  房间码
     * @param userId    玩家ID
     * @param handIndex 手牌索引
     * @return 更新后的战斗状态
     */
    public MultiplayerBattleState playCard(String roomCode, String userId, int handIndex) {
        return playCard(roomCode, userId, handIndex, -1, null);
    }

    public MultiplayerBattleState playCard(String roomCode, String userId, int handIndex,
                                           long expectedVersion, String idempotencyKey) {
        String key = commandKey("play", roomCode, userId, idempotencyKey);
        String fingerprint = CommandGuard.fingerprint("POST", "/multiplayer/battle/" + roomCode + "/play",
                String.valueOf(handIndex));
        IdempotencyStore.Entry entry = begin(key, fingerprint);
        if (entry != null && entry.completed()) {
            MultiplayerBattleState existing = battleStore.get(roomCode);
            if (existing != null) return existing;
        }
        try {
            MultiplayerBattleState result = withRoomLock(roomCode, () -> {
            MultiplayerBattleState state = getBattleOrThrow(roomCode);
            if (expectedVersion >= 0) {
                CommandGuard.checkVersion("battle:" + roomCode, expectedVersion, state.getStateVersion());
            }

            MultiplayerPlayer player = state.findPlayer(userId);
            if (player == null) {
                throw new InvalidActionException("你不在该战斗中");
            }
            if (!player.isAlive()) {
                throw new InvalidActionException("你已经阵亡，无法出牌");
            }
            if (!state.isPlayerTurn() || state.isBattleOver()) {
                throw new InvalidActionException("当前不是你的回合或战斗已结束");
            }
            if (player.isEndedTurn()) {
                throw new InvalidActionException("你已经结束了本回合");
            }

            GameCharacter gc = player.getCharacter();
            if (handIndex < 0 || handIndex >= gc.getHand().size()) {
                throw new InvalidActionException("无效的手牌索引: " + handIndex);
            }

            Card card = gc.getHand().get(handIndex);
            if (gc.getEnergy() < card.getCost()) {
                throw new InvalidActionException("能量不足");
            }

            // 调用 GameCharacter.playCard 执行卡牌效果
            // 包含：扣能量、施加 debuff 到敌人、计算伤害（含力量/脆弱/虚弱）、格挡、抽牌、治疗等
            boolean success = gc.playCard(card, state.getEnemy());
            if (!success) {
                throw new InvalidActionException("出牌失败");
            }

            state.addLog(player.getUsername() + " 使用了 " + card.getName());

            // 检查敌人是否死亡
            if (state.getEnemy().isDead()) {
                state.setBattleOver(true);
                state.setVictory(true);
                state.setPlayerTurn(false);
                state.addLog("击败了 " + state.getEnemy().getName() + "！胜利！");
                generateRewards(state);
                log.info("Battle won: room={}", roomCode);
            }

            battleStore.save(state);
            broadcaster.broadcastBattleUpdate(roomCode, toBattleInfo(state));
            return state;
            });
            complete(key, fingerprint, "");
            return result;
        } catch (RuntimeException error) {
            abort(key);
            throw error;
        }
    }

    /**
     * 结束自己的回合
     * 当所有存活玩家都结束回合后，触发敌人回合。
     *
     * @param roomCode 房间码
     * @param userId   玩家ID
     * @return 更新后的战斗状态
     */
    public MultiplayerBattleState endTurn(String roomCode, String userId) {
        return endTurn(roomCode, userId, -1, null);
    }

    public MultiplayerBattleState endTurn(String roomCode, String userId,
                                          long expectedVersion, String idempotencyKey) {
        String key = commandKey("endturn", roomCode, userId, idempotencyKey);
        String fingerprint = CommandGuard.fingerprint("POST", "/multiplayer/battle/" + roomCode + "/endturn", "");
        IdempotencyStore.Entry entry = begin(key, fingerprint);
        if (entry != null && entry.completed()) {
            MultiplayerBattleState existing = battleStore.get(roomCode);
            if (existing != null) return existing;
        }
        try {
            MultiplayerBattleState result = withRoomLock(roomCode, () -> {
            MultiplayerBattleState state = getBattleOrThrow(roomCode);
            if (expectedVersion >= 0) {
                CommandGuard.checkVersion("battle:" + roomCode, expectedVersion, state.getStateVersion());
            }

            MultiplayerPlayer player = state.findPlayer(userId);
            if (player == null) {
                throw new InvalidActionException("你不在该战斗中");
            }
            if (!player.isAlive()) {
                throw new InvalidActionException("你已经阵亡");
            }
            if (!state.isPlayerTurn() || state.isBattleOver()) {
                throw new InvalidActionException("当前无法结束回合");
            }
            if (player.isEndedTurn()) {
                throw new InvalidActionException("你已经结束了本回合");
            }

            // 标记该玩家结束回合，弃掉手牌
            player.setEndedTurn(true);
            player.getCharacter().endTurn();
            state.addLog(player.getUsername() + " 结束了回合");

            // 检查是否所有存活玩家都已结束
            if (state.allAlivePlayersEndedTurn()) {
                // 执行敌人回合
                executeEnemyTurn(state);

                // 如果战斗未结束，开始新一轮玩家回合
                if (!state.isBattleOver()) {
                    startPlayerTurn(state);
                }
            }

            battleStore.save(state);
            broadcaster.broadcastBattleUpdate(roomCode, toBattleInfo(state));
            return state;
            });
            complete(key, fingerprint, "");
            return result;
        } catch (RuntimeException error) {
            abort(key);
            throw error;
        }
    }

    /** 获取战斗状态信息 */
    public Map<String, Object> getBattleInfo(String roomCode) {
        MultiplayerBattleState state = getBattleOrThrow(roomCode);
        return toBattleInfo(state);
    }

    /** 获取战斗状态实体（供内部使用） */
    public MultiplayerBattleState getBattleState(String roomCode) {
        return getBattleOrThrow(roomCode);
    }

    // ===== 内部方法 =====

    /** 在房间级分布式锁保护下执行状态变更。 */
    private <T> T withRoomLock(String roomCode, Supplier<T> action) {
        return lockService.executeWithLock(RoomLockKeys.forRoom(roomCode), 5, action);
    }

    private String commandKey(String operation, String roomCode, String userId, String idempotencyKey) {
        if (idempotencyStore == null || idempotencyKey == null || idempotencyKey.isBlank()) return null;
        return operation + ":" + roomCode + ":" + userId + ":" + idempotencyKey;
    }

    private IdempotencyStore.Entry begin(String key, String fingerprint) {
        return key == null ? null : CommandGuard.begin(idempotencyStore, key, fingerprint);
    }

    private void complete(String key, String fingerprint, String value) {
        if (key != null) idempotencyStore.complete(key, fingerprint, value, CommandGuard.TTL);
    }

    private void abort(String key) {
        if (key != null) idempotencyStore.remove(key);
    }

    /** 获取战斗状态，不存在则抛异常 */
    private MultiplayerBattleState getBattleOrThrow(String roomCode) {
        MultiplayerBattleState state = battleStore.get(roomCode);
        if (state == null) {
            throw new BusinessException("BATTLE_NOT_FOUND", "战斗不存在或已结束", 404);
        }
        return state;
    }

    /**
     * 执行敌人回合
     * 1. 中毒伤害 2. 敌人按意图行动 3. 重置敌人格挡 4. tick敌人buff
     * 5. 检查玩家死亡 6. 选择新意图和目标
     */
    private void executeEnemyTurn(MultiplayerBattleState state) {
        state.setPlayerTurn(false);
        Enemy enemy = state.getEnemy();

        state.addLog("--- 敌人回合 ---");

        // 1. 中毒伤害
        Integer poison = enemy.getBuffs().get(BuffType.POISON);
        if (poison != null && poison > 0) {
            int poisonDmg = poison;
            enemy.takeDamage(poisonDmg);
            state.addLog(enemy.getName() + " 受到中毒伤害 " + poisonDmg);
            if (enemy.isDead()) {
                state.setBattleOver(true);
                state.setVictory(true);
                state.addLog("击败了 " + enemy.getName() + "！胜利！");
                generateRewards(state);
                return;
            }
        }

        // 2. 敌人按意图行动
        EnemyIntent intent = enemy.getIntent();
        int intentValue = enemy.getIntentValue();
        MultiplayerPlayer target = state.getPlayers().get(state.getTargetPlayerIndex());

        if (target != null && target.isAlive()) {
            switch (intent) {
                case ATTACK -> {
                    int actualDmg = target.getCharacter().takeDamage(intentValue);
                    state.addLog(enemy.getName() + " 攻击 " + target.getUsername() + "，造成 " + actualDmg + " 伤害");
                    if (target.getCharacter().isDead()) {
                        target.setAlive(false);
                        state.addLog(target.getUsername() + " 阵亡了！");
                    }
                }
                case DEFEND -> {
                    enemy.gainBlock(intentValue);
                    state.addLog(enemy.getName() + " 进入防御姿态，获得 " + intentValue + " 格挡");
                }
                case BUFF -> {
                    enemy.addBuff(BuffType.STRENGTH, intentValue);
                    state.addLog(enemy.getName() + " 蓄力，力量+" + intentValue);
                }
                default -> state.addLog(enemy.getName() + " 在观望");
            }
        }

        // 3. 重置敌人格挡
        enemy.resetBlock();

        // 4. tick敌人buff（减少debuff回合数）
        enemy.tickBuffs();

        // 5. 检查所有玩家是否阵亡
        if (state.alivePlayerCount() == 0) {
            state.setBattleOver(true);
            state.setVictory(false);
            state.addLog("全军覆没...战斗失败");
            log.info("Battle lost: room={}", state.getRoomCode());
            return;
        }

        // 6. 选择新意图和攻击目标
        enemy.chooseIntent();
        state.setTargetPlayerIndex(state.randomAlivePlayerIndex(random));
        MultiplayerPlayer newTarget = state.getPlayers().get(state.getTargetPlayerIndex());
        state.addLog(enemy.getName() + " 意图攻击: " + (newTarget != null ? newTarget.getUsername() : "?"));
    }

    /**
     * 开始新一轮玩家回合
     * 为每个存活玩家：回能量、重置格挡、抽牌、tick buff
     */
    private void startPlayerTurn(MultiplayerBattleState state) {
        state.setPlayerTurn(true);
        state.setTurnNumber(state.getTurnNumber() + 1);

        for (MultiplayerPlayer player : state.getPlayers()) {
            if (player.isAlive()) {
                GameCharacter gc = player.getCharacter();
                gc.startTurn();   // 回能量、重置格挡、抽drawNextTurn张牌
                gc.drawCards(GameConstants.INITIAL_HAND_SIZE);  // 抽5张新手牌
                gc.tickBuffs();   // 减少debuff回合数

                // 检查中毒死亡
                if (gc.isDead()) {
                    player.setAlive(false);
                    state.addLog(player.getUsername() + " 因中毒阵亡！");
                }
                player.setEndedTurn(false);
            }
        }

        // 如果全员因中毒死亡
        if (state.alivePlayerCount() == 0) {
            state.setBattleOver(true);
            state.setVictory(false);
            state.addLog("全军覆没...战斗失败");
            return;
        }

        state.addLog("--- 第 " + state.getTurnNumber() + " 回合 ---");
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
                Enemy template = enemyRepo.findById(enemyId).orElse(null);
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
        return createEnemyForRoom(floor, playerCount);
    }

    /**
     * 为房间创建敌人（回退用，按楼层随机选择）
     */
    private Enemy createEnemyForRoom(int floor, int playerCount) {
        // 尝试按楼层查找敌人
        List<Enemy> candidates = enemyRepo.findByLevel(floor);
        if (candidates.isEmpty()) {
            candidates = enemyRepo.findAll();
        }
        if (candidates.isEmpty()) {
            // 数据库无敌人数据，创建默认敌人
            Enemy fallback = new Enemy("黑风妖", 80, 10, 2, false, 1);
            List<String> pattern = new ArrayList<>();
            pattern.add("attack");
            pattern.add("attack");
            pattern.add("defend");
            fallback.setMovePattern(pattern);
            Enemy combat = fallback.copy();
            scaleEnemy(combat, playerCount);
            return combat;
        }

        // 随机选一个敌人模板
        Enemy template = candidates.get(random.nextInt(candidates.size()));
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

    // ===== 奖励与楼层推进 =====

    /**
     * 战斗胜利后为每个存活玩家生成5张随机卡牌奖励
     * 奖励数量 = 存活玩家数（每人3选1）
     */
    private void generateRewards(MultiplayerBattleState state) {
        Map<String, List<Card>> rewards = new LinkedHashMap<>();
        for (MultiplayerPlayer p : state.getPlayers()) {
            if (p.isAlive()) {
                rewards.put(p.getUserId(), generateRewardCards(p.getCharacter().getCharacterClass()));
            }
        }
        state.setRewards(rewards);
        state.setRewardsPhase(true);
        state.setRewardsHandled(false);
        state.addLog("战斗胜利！每位存活玩家可从5张卡牌中选择1张");
        log.info("Rewards generated: room={}, alivePlayers={}", state.getRoomCode(), rewards.size());
    }

    /** 为指定角色生成5张随机卡牌（非基础卡，优先该职业+通用） */
    private List<Card> generateRewardCards(CharacterClass charClass) {
        return CardRewardSampler.draw(cardRepo.findByCharacterClassOrCharacterClassIsNull(charClass),
                charClass, GameConstants.CARD_REWARD_COUNT, random);
    }

    /**
     * 玩家领取奖励（从5张中选1张加入牌组）
     */
    public MultiplayerBattleState claimReward(String roomCode, String userId, String cardName) {
        return claimReward(roomCode, userId, cardName, null);
    }

    /** Idempotent variant used by HTTP clients that send X-Idempotency-Key. */
    public MultiplayerBattleState claimReward(String roomCode, String userId, String cardName,
                                              String idempotencyKey) {
        return claimReward(roomCode, userId, cardName, -1, idempotencyKey);
    }

    public MultiplayerBattleState claimReward(String roomCode, String userId, String cardName,
                                              long expectedVersion, String idempotencyKey) {
        return resolveReward(roomCode, userId, cardName, false, expectedVersion, idempotencyKey);
    }

    public MultiplayerBattleState skipReward(String roomCode, String userId,
                                              long expectedVersion, String idempotencyKey) {
        return resolveReward(roomCode, userId, null, true, expectedVersion, idempotencyKey);
    }

    private MultiplayerBattleState resolveReward(String roomCode, String userId, String cardName,
                                                 boolean skip, long expectedVersion, String idempotencyKey) {
        String key = commandKey(skip ? "skip-reward" : "claim-reward", roomCode, userId, idempotencyKey);
        String fingerprint = CommandGuard.fingerprint("POST", "/multiplayer/battle/" + roomCode + (skip ? "/skip-reward" : "/claim-reward"), cardName);
        IdempotencyStore.Entry entry = begin(key, fingerprint);
        if (entry != null && entry.completed()) {
            MultiplayerBattleState existing = battleStore.get(roomCode);
            if (existing != null) return existing;
        }
        try {
            MultiplayerBattleState result = withRoomLock(roomCode, () -> {
            MultiplayerBattleState state = getBattleOrThrow(roomCode);
            if (expectedVersion >= 0) {
                CommandGuard.checkVersion("battle:" + roomCode, expectedVersion, state.getStateVersion());
            }
            if (!state.isRewardsPhase()) {
                throw new InvalidActionException("当前不在领奖阶段");
            }
            if (state.getClaimedRewards().containsKey(userId)) {
                throw new InvalidActionException("你已经领取过奖励了");
            }
            List<Card> options = state.getRewards().get(userId);
            if (options == null || (!skip && options.isEmpty())) {
                throw new InvalidActionException("你没有可领取的奖励");
            }

            // 找到选择的卡牌
            Card chosen = skip ? null : options.stream()
                    .filter(c -> c.getName().equals(cardName))
                    .findFirst()
                    .orElseThrow(() -> new InvalidActionException("无效的卡牌选择: " + cardName));

            // 加入玩家牌组
            MultiplayerPlayer player = state.findPlayer(userId);
            if (player == null) {
                throw new InvalidActionException("你不在该战斗中");
            }
            if (chosen != null) player.getCharacter().addCard(chosen.copy());

            state.getClaimedRewards().put(userId, skip ? "__SKIPPED__" : cardName);
            state.addLog(player.getUsername() + (skip ? " 跳过了卡牌奖励" : " 选择了卡牌: " + cardName));

            // 检查是否所有人都领取完毕
            boolean allClaimed = state.getRewards().keySet().stream()
                    .allMatch(uid -> state.getClaimedRewards().containsKey(uid));
            if (allClaimed) {
                state.setRewardsHandled(true);
                state.addLog("所有玩家已处理奖励，房主可进入下一层");
            }

            battleStore.save(state);
            broadcaster.broadcastBattleUpdate(roomCode, toBattleInfo(state));
            return state;
            });
            complete(key, fingerprint, "");
            return result;
        } catch (RuntimeException error) {
            abort(key);
            throw error;
        }
    }

    /**
     * 战斗结束、领完奖励后，房主返回地图探索
     * 同步GameCharacter状态到RoomPlayer，恢复房间为IN_MAP状态。
     * 如果是Boss节点，进入下一层地图。
     */
    public Map<String, Object> returnToMap(String roomCode, String requesterId) {
        return returnToMap(roomCode, requesterId, -1, null);
    }

    public Map<String, Object> returnToMap(String roomCode, String requesterId,
                                           long expectedVersion, String idempotencyKey) {
        String key = commandKey("next-floor", roomCode, requesterId, idempotencyKey);
        String fingerprint = CommandGuard.fingerprint("POST", "/multiplayer/battle/" + roomCode + "/next-floor", "");
        IdempotencyStore.Entry entry = begin(key, fingerprint);
        if (entry != null && entry.completed()) {
            return Map.of("message", "操作已完成", "completed",
                    roomService.getRoomEntity(roomCode).getStatus() == com.xiyouji.service.room.RoomStatus.FINISHED);
        }
        try {
            Map<String, Object> result = withRoomLock(roomCode, () -> {
            MultiplayerBattleState state = getBattleOrThrow(roomCode);
            if (expectedVersion >= 0) {
                CommandGuard.checkVersion("battle:" + roomCode, expectedVersion, state.getStateVersion());
            }
            Room room = roomService.getRoomEntity(roomCode);

            if (!room.getHostUserId().equals(requesterId)) {
                throw new InvalidActionException("只有房主才能继续");
            }
            if (!state.isVictory()) {
                throw new InvalidActionException("战斗未胜利");
            }
            if (!state.isRewardsHandled() && state.isRewardsPhase()) {
                throw new InvalidActionException("请等待所有玩家领取奖励");
            }

            // 同步GameCharacter状态到RoomPlayer（HP、金币、牌组、遗物）
            for (MultiplayerPlayer mp : state.getPlayers()) {
                RoomPlayer rp = room.getPlayers().stream()
                        .filter(p -> p.getUserId().equals(mp.getUserId()))
                        .findFirst()
                        .orElse(null);
                if (rp != null) {
                    rp.syncFromCharacter(mp.getCharacter());
                }
            }

            Map<String, Object> outcome = new HashMap<>();
            MapNode currentNode = room.getCurrentNode();

            // 如果是Boss节点，进入下一层
            if (currentNode != null && GameConstants.NODE_BOSS.equals(currentNode.getType())) {
                boolean success = roomService.nextLayer(roomCode, requesterId).get("message") != null;
                // nextLayer内部已设置room状态和floor
                if (room.getStatus() == com.xiyouji.service.room.RoomStatus.FINISHED) {
                    outcome.put("completed", true);
                    outcome.put("message", "恭喜通关！西天取经圆满！");
                } else {
                    outcome.put("nextLayer", true);
                    outcome.put("floor", room.getFloor());
                    outcome.put("message", "进入第 " + room.getFloor() + " 层");
                }
            } else {
                // 普通战斗，恢复地图探索
                roomService.markInMap(roomCode);
                outcome.put("nextLayer", false);
                outcome.put("message", "返回地图探索");
            }

            // 清理战斗状态
            battleStore.remove(roomCode);

            // 广播
            broadcaster.broadcastRoomUpdate(roomCode, roomService.getRoom(roomCode));
            broadcaster.broadcastSystemMessage(roomCode, outcome.get("message").toString());

            log.info("Return to map: room={}, nextLayer={}", roomCode, outcome.get("nextLayer"));
            return outcome;
            });
            complete(key, fingerprint, "");
            return result;
        } catch (RuntimeException error) {
            abort(key);
            throw error;
        }
    }

    /**
     * 兼容旧API：房主进入下一层（现为返回地图探索）
     * @deprecated 使用 returnToMap 替代
     */
    @Deprecated
    public Map<String, Object> nextFloor(String roomCode, String requesterId) {
        return returnToMap(roomCode, requesterId);
    }

    // ===== 战斗状态转Map（供WebSocket广播和HTTP响应） =====

    /**
     * 将战斗状态转为JSON友好的Map结构
     * 包含所有玩家的手牌信息（支持队友间卡牌可见，便于协作）
     */
    public Map<String, Object> toBattleInfo(MultiplayerBattleState state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("roomCode", state.getRoomCode());
        info.put("stateVersion", state.getStateVersion());
        info.put("turnNumber", state.getTurnNumber());
        info.put("playerTurn", state.isPlayerTurn());
        info.put("battleOver", state.isBattleOver());
        info.put("victory", state.isVictory());

        // 敌人信息
        Enemy enemy = state.getEnemy();
        Map<String, Object> enemyInfo = new LinkedHashMap<>();
        enemyInfo.put("name", enemy.getName());
        enemyInfo.put("hp", enemy.getHp());
        enemyInfo.put("maxHp", enemy.getMaxHp());
        enemyInfo.put("block", enemy.getBlock());
        enemyInfo.put("strength", enemy.getStrength());
        enemyInfo.put("emoji", enemy.getEmoji());
        enemyInfo.put("intent", enemy.getIntent() != null ? enemy.getIntent().name() : "ATTACK");
        enemyInfo.put("intentValue", enemy.getIntentValue());
        enemyInfo.put("isBoss", enemy.isBoss());
        enemyInfo.put("buffs", enemy.getBuffs() != null ? enemy.getBuffs() : Map.of());
        enemyInfo.put("targetPlayerIndex", state.getTargetPlayerIndex());
        info.put("enemy", enemyInfo);

        // 玩家信息列表
        List<Map<String, Object>> playerList = new ArrayList<>();
        for (int i = 0; i < state.getPlayers().size(); i++) {
            MultiplayerPlayer p = state.getPlayers().get(i);
            GameCharacter gc = p.getCharacter();
            Map<String, Object> pInfo = new LinkedHashMap<>();
            pInfo.put("index", i);
            pInfo.put("userId", p.getUserId());
            pInfo.put("username", p.getUsername());
            pInfo.put("characterClass", gc.getCharacterClass() != null ? gc.getCharacterClass().name() : null);
            pInfo.put("hp", gc.getHp());
            pInfo.put("maxHp", gc.getMaxHp());
            pInfo.put("block", gc.getBlock());
            pInfo.put("energy", gc.getEnergy());
            pInfo.put("maxEnergy", gc.getCurrentMaxEnergy());
            pInfo.put("strength", gc.getStrength());
            pInfo.put("dexterity", gc.getDexterity());
            pInfo.put("endedTurn", p.isEndedTurn());
            pInfo.put("alive", p.isAlive());
            pInfo.put("deckSize", gc.getDeck() != null ? gc.getDeck().size() : 0);
            pInfo.put("drawPileSize", gc.getDrawPile() != null ? gc.getDrawPile().size() : 0);
            pInfo.put("discardPileSize", gc.getDiscardPile() != null ? gc.getDiscardPile().size() : 0);
            pInfo.put("buffs", gc.getBuffs() != null ? gc.getBuffs() : Map.of());

            // 手牌详情（含索引，支持队友间可见便于协作）
            List<Map<String, Object>> hand = new ArrayList<>();
            if (gc.getHand() != null) {
                for (int j = 0; j < gc.getHand().size(); j++) {
                    Card card = gc.getHand().get(j);
                    Map<String, Object> cardInfo = new LinkedHashMap<>();
                    cardInfo.put("index", j);
                    cardInfo.put("name", card.getName());
                    cardInfo.put("type", card.getType() != null ? card.getType().name() : null);
                    cardInfo.put("cost", card.getCost());
                    cardInfo.put("damage", card.getDamage());
                    cardInfo.put("block", card.getBlock());
                    cardInfo.put("emoji", card.getEmoji());
                    cardInfo.put("exhaust", card.isExhaust());
                    cardInfo.put("description", card.getDescription());
                    hand.add(cardInfo);
                }
            }
            pInfo.put("hand", hand);
            playerList.add(pInfo);
        }
        info.put("players", playerList);
        info.put("alivePlayerCount", state.alivePlayerCount());
        info.put("playersEndedTurn", state.getPlayers().stream()
                .filter(MultiplayerPlayer::isEndedTurn).count());

        // 奖励阶段信息
        info.put("rewardsPhase", state.isRewardsPhase());
        info.put("rewardsHandled", state.isRewardsHandled());
        if (state.isRewardsPhase() && !state.getRewards().isEmpty()) {
            // 每个玩家的奖励选项（只发自己的，避免作弊）
            Map<String, Object> rewardsInfo = new LinkedHashMap<>();
            for (Map.Entry<String, List<Card>> entry : state.getRewards().entrySet()) {
                List<Map<String, Object>> cardList = new ArrayList<>();
                for (Card c : entry.getValue()) {
                    Map<String, Object> ci = new LinkedHashMap<>();
                    ci.put("name", c.getName());
                    ci.put("type", c.getType() != null ? c.getType().name() : null);
                    ci.put("cost", c.getCost());
                    ci.put("damage", c.getDamage());
                    ci.put("block", c.getBlock());
                    ci.put("emoji", c.getEmoji());
                    ci.put("description", c.getDescription());
                    cardList.add(ci);
                }
                rewardsInfo.put(entry.getKey(), cardList);
            }
            info.put("rewards", rewardsInfo);
            info.put("claimedRewards", state.getClaimedRewards());
        }

        // 战斗日志（最近20条）
        List<String> log = state.getCombatLog();
        int start = Math.max(0, log.size() - 20);
        info.put("combatLog", log.subList(start, log.size()));

        return info;
    }
}
