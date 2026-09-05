package com.xiyouji.service;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.Enemy;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.enums.CardType;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.model.enums.Rarity;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.port.CharacterRepositoryPort;
import com.xiyouji.port.EnemyRepositoryPort;
import com.xiyouji.service.room.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * MultiplayerBattleService 单元测试
 * 验证5人PvE协作战斗的核心流程：开始战斗、抢出牌、结束回合、敌人行动、胜负判定
 */
@DisplayName("多人战斗系统测试")
@ExtendWith(MockitoExtension.class)
class MultiplayerBattleServiceTest {

    @Mock private RoomService roomService;
    @Mock private MultiplayerBattleStore battleStore;
    @Mock private CharacterRepositoryPort characterRepo;
    @Mock private CardRepositoryPort cardRepo;
    @Mock private EnemyRepositoryPort enemyRepo;
    @Mock private RoomEventPublisher broadcaster;
    @Mock private DistributedLockService lockService;

    @InjectMocks
    private MultiplayerBattleService battleService;

    /** 用真实内存Map模拟BattleStore，使多次操作能共享状态 */
    private final Map<String, MultiplayerBattleState> storeMap = new HashMap<>();
    private Room testRoom;

    private static final String ROOM_CODE = "TEST1234";
    private static final String HOST_ID = "host_user";

    @BeforeEach
    void setUp() {
        storeMap.clear();
        // 单元测试直接执行锁内逻辑；分布式锁的跨实例行为由集成测试覆盖。
        lenient().doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(lockService).executeWithLock(anyString(), anyLong(), any(Supplier.class));
        lenient().doAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return null;
                })
                .when(lockService).executeWithLock(anyString(), anyLong(), any(Runnable.class));

        // 用Answer让battleStore.save/get操作真实map
        lenient().doAnswer(inv -> {
            MultiplayerBattleState s = inv.getArgument(0);
            storeMap.put(s.getRoomCode(), s);
            return null;
        }).when(battleStore).save(any());
        lenient().when(battleStore.get(anyString())).thenAnswer(inv ->
                storeMap.get(inv.getArgument(0)));
        lenient().when(battleStore.exists(anyString())).thenAnswer(inv ->
                storeMap.containsKey(inv.getArgument(0)));

        // broadcaster 静默
        lenient().doNothing().when(broadcaster).broadcastBattleUpdate(anyString(), any());
        lenient().doNothing().when(broadcaster).broadcastSystemMessage(anyString(), anyString());

        // 模拟Room
        Room room = new Room(ROOM_CODE, HOST_ID);
        testRoom = room;
        // startBattle 的真实前置条件是：房间已经进入地图探索，且当前节点是战斗节点。
        // 测试直接构造这个状态，避免绕过房间服务导致测试与生产状态机不一致。
        room.setStatus(RoomStatus.IN_MAP);
        room.setFloor(1);
        MapNode battleNode = new MapNode("test-node", 1, 1, 0,
                GameConstants.NODE_BATTLE, "测试战斗");
        battleNode.setAccessible(true);
        room.setCurrentNode(battleNode);
        for (int i = 0; i < 5; i++) {
            RoomPlayer rp = new RoomPlayer("user_" + i, "player_" + i);
            rp.setReady(true);
            rp.setCharacterClass(CharacterClass.values()[i]);
            room.getPlayers().add(rp);
        }
        lenient().when(roomService.getRoomEntity(anyString())).thenReturn(room);
        lenient().when(roomService.canStart(anyString())).thenReturn(true);
        lenient().doNothing().when(roomService).markInBattle(anyString());
    }

    private void setupCharacterAndEnemyMocks() {
        // 角色模板
        lenient().when(characterRepo.findByCharacterClass(any(CharacterClass.class)))
                .thenAnswer(inv -> {
                    CharacterClass cc = inv.getArgument(0);
                    GameCharacter template = new GameCharacter();
                    template.setCharacterClass(cc);
                    template.setMaxHp(80);
                    template.setStartingGold(100);
                    return Optional.of(template);
                });

        // 基础卡牌
        Card attackCard = new Card("挥棒", "造成6点伤害", CardType.ATTACK, Rarity.BASIC, null, 1);
        attackCard.setDamage(6);
        Card defendCard = new Card("格挡", "获得5点格挡", CardType.DEFENSE, Rarity.BASIC, null, 1);
        defendCard.setBlock(5);
        lenient().when(cardRepo.findByName("挥棒")).thenReturn(List.of(attackCard));
        lenient().when(cardRepo.findByName("格挡")).thenReturn(List.of(defendCard));

        // startBattle 从房间持久化的牌组初始化战斗，测试需要提供与生产相同的初始牌组。
        for (RoomPlayer player : testRoom.getPlayers()) {
            for (int i = 0; i < GameConstants.INITIAL_HAND_SIZE; i++) {
                player.getDeck().add(attackCard.copy());
                player.getDeck().add(defendCard.copy());
            }
        }

        // 敌人
        Enemy enemy = new Enemy("黑风怪", 50, 10, 2, false, 1);
        enemy.setMovePattern(List.of("attack", "defend"));
        lenient().when(enemyRepo.findByLevel(anyInt())).thenReturn(List.of(enemy));
    }

    @Test
    @DisplayName("startBattle: 创建5人战斗，含敌人和初始手牌")
    void testStartBattle() {
        setupCharacterAndEnemyMocks();

        MultiplayerBattleState state = battleService.startBattle(ROOM_CODE, HOST_ID);

        assertNotNull(state);
        assertEquals(ROOM_CODE, state.getRoomCode());
        assertEquals(5, state.getPlayers().size());
        assertEquals(1, state.getTurnNumber());
        assertTrue(state.isPlayerTurn());
        assertFalse(state.isBattleOver());
        assertNotNull(state.getEnemy());
        // 每个玩家应有初始手牌
        for (MultiplayerPlayer p : state.getPlayers()) {
            assertEquals(GameConstants.INITIAL_HAND_SIZE, p.getCharacter().getHand().size());
            assertEquals(GameConstants.MAX_ENERGY, p.getCharacter().getEnergy());
            assertTrue(p.isAlive());
            assertFalse(p.isEndedTurn());
        }
        // 敌人HP应被缩放（5人 → 3倍）
        assertEquals(150, state.getEnemy().getMaxHp());
        verify(roomService).markInBattle(ROOM_CODE);
    }

    @Test
    @DisplayName("startBattle: 非房主无法开始")
    void testStartBattle_nonHost_throws() {
        setupCharacterAndEnemyMocks();
        assertThrows(InvalidActionException.class,
                () -> battleService.startBattle(ROOM_CODE, "not_host"));
    }

    @Test
    @DisplayName("playCard: 出攻击牌减少敌人HP")
    void testPlayCard_reducesEnemyHp() {
        setupCharacterAndEnemyMocks();
        MultiplayerBattleState state = battleService.startBattle(ROOM_CODE, HOST_ID);

        // 玩家0出牌——手牌已洗牌，需找到攻击牌
        MultiplayerPlayer p0 = state.getPlayers().get(0);
        int attackIdx = -1;
        for (int j = 0; j < p0.getCharacter().getHand().size(); j++) {
            Card c = p0.getCharacter().getHand().get(j);
            if (c.getDamage() > 0) {
                attackIdx = j;
                break;
            }
        }
        assertTrue(attackIdx >= 0, "手牌中应至少有一张攻击牌");

        int enemyHpBefore = state.getEnemy().getHp();
        battleService.playCard(ROOM_CODE, p0.getUserId(), attackIdx);

        // 敌人HP应减少（6点基础伤害 + 力量0 = 6）
        assertTrue(state.getEnemy().getHp() < enemyHpBefore,
                "出攻击牌后敌人HP应减少: before=" + enemyHpBefore + " after=" + state.getEnemy().getHp());
        // 能量应减少
        assertEquals(GameConstants.MAX_ENERGY - 1, p0.getCharacter().getEnergy());
    }

    @Test
    @DisplayName("endTurn: 单人结束回合不触发敌人行动")
    void testEndTurn_singlePlayer_doesNotTriggerEnemy() {
        setupCharacterAndEnemyMocks();
        MultiplayerBattleState state = battleService.startBattle(ROOM_CODE, HOST_ID);

        battleService.endTurn(ROOM_CODE, "user_0");

        // 只有一个玩家结束，不应触发敌人回合
        assertTrue(state.isPlayerTurn());
        assertEquals(1, state.getTurnNumber());
        // 该玩家应标记为endedTurn
        assertTrue(state.getPlayers().get(0).isEndedTurn());
    }

    @Test
    @DisplayName("endTurn: 所有玩家结束后触发敌人回合并开始新回合")
    void testEndTurn_allPlayers_triggersEnemyTurn() {
        setupCharacterAndEnemyMocks();
        MultiplayerBattleState state = battleService.startBattle(ROOM_CODE, HOST_ID);

        // 所有5个玩家结束回合
        for (int i = 0; i < 5; i++) {
            battleService.endTurn(ROOM_CODE, "user_" + i);
        }

        // 应触发敌人回合后开始新回合
        assertEquals(2, state.getTurnNumber());
        assertTrue(state.isPlayerTurn());
        // 所有玩家endedTurn应重置
        for (MultiplayerPlayer p : state.getPlayers()) {
            assertFalse(p.isEndedTurn());
        }
        // 敌人应执行了攻击（玩家应受到伤害，除非敌人意图是defend）
        // 敌人movePattern=[attack, defend]，第一回合意图应是attack
    }

    @Test
    @DisplayName("playCard: 已结束回合的玩家不能出牌")
    void testPlayCard_afterEndTurn_throws() {
        setupCharacterAndEnemyMocks();
        MultiplayerBattleState state = battleService.startBattle(ROOM_CODE, HOST_ID);

        battleService.endTurn(ROOM_CODE, "user_0");

        assertThrows(InvalidActionException.class,
                () -> battleService.playCard(ROOM_CODE, "user_0", 0));
    }

    @Test
    @DisplayName("playCard: 能量不足时不能出牌")
    void testPlayCard_notEnoughEnergy_throws() {
        setupCharacterAndEnemyMocks();
        MultiplayerBattleState state = battleService.startBattle(ROOM_CODE, HOST_ID);

        // 消耗所有能量（3点能量，每张牌1点，出3次）
        String userId = state.getPlayers().get(0).getUserId();
        battleService.playCard(ROOM_CODE, userId, 0);
        battleService.playCard(ROOM_CODE, userId, 0);
        battleService.playCard(ROOM_CODE, userId, 0);

        // 第4次应失败（能量不足）
        assertThrows(InvalidActionException.class,
                () -> battleService.playCard(ROOM_CODE, userId, 0));
    }

    @Test
    @DisplayName("getBattleInfo: 返回完整的战斗状态Map")
    void testGetBattleInfo() {
        setupCharacterAndEnemyMocks();
        battleService.startBattle(ROOM_CODE, HOST_ID);

        Map<String, Object> info = battleService.getBattleInfo(ROOM_CODE);

        assertNotNull(info);
        assertEquals(ROOM_CODE, info.get("roomCode"));
        assertEquals(1, info.get("turnNumber"));
        assertTrue((boolean) info.get("playerTurn"));
        assertNotNull(info.get("enemy"));
        assertEquals(5, ((List<?>) info.get("players")).size());
    }

    @Test
    void victoryOffersFiveAndMixedClaimSkipCompletesWithoutDuplicateRewards() {
        setupCharacterAndEnemyMocks();
        Card reward = new Card("reward", "", CardType.ATTACK, Rarity.COMMON, null, 1);
        when(cardRepo.findByCharacterClassOrCharacterClassIsNull(any())).thenReturn(List.of(reward));
        MultiplayerBattleState state = battleService.startBattle(ROOM_CODE, HOST_ID);
        state.getEnemy().setHp(1);
        // Reward settlement must not depend on whether the shuffled first card is defense.
        Card finishingAttack = new Card("finisher", "", CardType.ATTACK, Rarity.BASIC, null, 1);
        finishingAttack.setDamage(6);
        state.findPlayer("user_0").getCharacter().getHand().set(0, finishingAttack);
        battleService.playCard(ROOM_CODE, "user_0", 0);
        assertTrue(state.isVictory());
        assertEquals(5, state.getRewards().size());
        state.getRewards().values().forEach(cards -> assertEquals(5, cards.size()));
        int before = state.findPlayer("user_0").getCharacter().getDeck().size();
        battleService.claimReward(ROOM_CODE, "user_0", "reward");
        assertEquals(before + 1, state.findPlayer("user_0").getCharacter().getDeck().size());
        assertFalse(state.isRewardsHandled());
        for (int i = 1; i < 5; i++) {
            String user = "user_" + i;
            int size = state.findPlayer(user).getCharacter().getDeck().size();
            battleService.skipReward(ROOM_CODE, user, -1, null);
            assertEquals(size, state.findPlayer(user).getCharacter().getDeck().size());
            assertEquals("__SKIPPED__", state.getClaimedRewards().get(user));
        }
        assertTrue(state.isRewardsHandled());
        assertThrows(InvalidActionException.class, () -> battleService.claimReward(ROOM_CODE, "user_0", "reward"));
        assertEquals(before + 1, state.findPlayer("user_0").getCharacter().getDeck().size());
    }
}
