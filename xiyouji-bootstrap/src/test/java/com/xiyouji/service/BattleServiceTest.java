package com.xiyouji.service;

import com.xiyouji.model.Card;
import com.xiyouji.model.Enemy;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.Relic;
import com.xiyouji.model.enums.CardType;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.model.enums.Rarity;
import com.xiyouji.model.enums.RelicTier;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.port.EnemyRepositoryPort;
import com.xiyouji.service.session.BattleState;
import com.xiyouji.service.session.GameSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BattleService 单元测试
 * 使用 @ExtendWith(MockitoExtension.class) 和 @Mock/@InjectMocks
 */
@DisplayName("BattleService 单元测试")
@ExtendWith(MockitoExtension.class)
class BattleServiceTest {

    @Mock
    private GameService gameService;

    @Mock
    private EnemyRepositoryPort enemyRepo;

    @Mock
    private CardRepositoryPort cardRepo;

    @InjectMocks
    private BattleService battleService;

    @Test
    @DisplayName("startBattle 正确初始化战斗")
    void testStartBattle_initializesCorrectly() {
        String sessionId = "test-battle-session";

        // 准备玩家（需要牌组供 initBattle 洗牌和 drawCards 抽牌）
        GameCharacter player = new GameCharacter();
        player.setCharacterClass(CharacterClass.SUN_WUKONG);
        player.setMaxHp(80);
        player.setHp(80);
        player.setMaxEnergy(3);
        Card attackCard = new Card("挥棒", "攻击", CardType.ATTACK, Rarity.BASIC, null, 1);
        attackCard.setDamage(6);
        for (int i = 0; i < 5; i++) {
            player.addCard(attackCard.copy());
        }

        // 准备地图节点（带敌人 ID）
        MapNode node = new MapNode("L1-R0-C0", 1, 0, 0, "BATTLE", "黑风山");
        node.setEnemyId("1");

        GameSession session = new GameSession(sessionId, player, List.of(node));
        session.setCurrentNode(node);

        // 准备敌人模板
        Enemy template = new Enemy("小妖", 30, 10, 2, false, 1);
        template.setId(1L);
        template.setMovePattern(List.of("attack"));

        when(gameService.getSession(sessionId)).thenReturn(session);
        when(enemyRepo.findById(1L)).thenReturn(Optional.of(template));

        BattleState battle = battleService.startBattle(sessionId);

        assertNotNull(battle, "战斗状态不应为 null");
        assertNotNull(battle.getEnemy(), "敌人不应为 null");
        assertEquals("小妖", battle.getEnemy().getName(), "敌人名称应匹配");
        assertTrue(battle.getTurnNumber() >= 1, "回合数应至少为 1");
        assertTrue(battle.isPlayerTurn(), "战斗开始后应为玩家回合");
        assertFalse(battle.isBattleOver(), "战斗不应已结束");
        assertTrue(player.getEnergy() > 0, "玩家应有能量");
        assertFalse(player.getHand().isEmpty(), "玩家应已抽到手牌");
        assertEquals(80, player.getHp(), "玩家 hp 应继承初始值");

        verify(gameService).saveSession(session);
    }

    @Test
    @DisplayName("战斗开始额外能量不会跨战斗累加")
    void testStartBattle_energyRelicIsPerBattleBonus() {
        String sessionId = "test-energy-relic-session";
        GameCharacter player = new GameCharacter();
        player.setCharacterClass(CharacterClass.SUN_WUKONG);
        player.setMaxHp(80);
        player.setHp(80);
        // 模拟旧版本已把上一场 +1 错误写入基础 maxEnergy=4 的会话。
        player.setMaxEnergy(4);
        Relic energyRelic = new Relic("定海神针", "战斗开始时获得1点额外能量。", RelicTier.BOSS, null);
        energyRelic.setEffect("BATTLE_START;ENERGY:1");
        player.getRelics().add(energyRelic);

        MapNode node = new MapNode("L1-R0-C0", 1, 0, 0, "BATTLE", "黑风山");
        node.setEnemyId("1");
        GameSession session = new GameSession(sessionId, player, List.of(node));
        session.setCurrentNode(node);

        Enemy template = new Enemy("小妖", 30, 10, 2, false, 1);
        template.setId(1L);
        template.setMovePattern(List.of("attack"));
        when(gameService.getSession(sessionId)).thenReturn(session);
        when(enemyRepo.findById(1L)).thenReturn(Optional.of(template));

        battleService.startBattle(sessionId);
        assertEquals(3, player.getMaxEnergy(), "遗物不应修改跨战斗基础最大能量");
        assertEquals(4, player.getCurrentMaxEnergy(), "本场应为基础3+遗物1");
        assertEquals(4, player.getEnergy(), "战斗开始应获得4点能量");

        battleService.startBattle(sessionId);
        assertEquals(3, player.getMaxEnergy(), "第二场基础最大能量仍应为3");
        assertEquals(4, player.getCurrentMaxEnergy(), "第二场仍应重新计算为3+1，而不是继续累加");
        assertEquals(4, player.getEnergy(), "第二场战斗开始仍应为4点能量");
    }

    @Test
    @DisplayName("playCard 无效索引不崩溃，返回战斗状态")
    void testPlayCard_invalidIndex_returnsBattle() {
        String sessionId = "test-playcard-session";

        GameCharacter player = new GameCharacter();
        player.setCharacterClass(CharacterClass.SUN_WUKONG);
        player.setMaxHp(80);
        player.setHp(80);
        player.setMaxEnergy(3);
        player.setEnergy(3);
        // 手牌为空

        Enemy enemy = new Enemy("小妖", 30, 10, 2, false, 1);
        BattleState battle = new BattleState(enemy);
        battle.startBattle();

        GameSession session = new GameSession(sessionId, player, List.of());
        session.setBattle(battle);

        when(gameService.getSession(sessionId)).thenReturn(session);

        // 索引 999 远超手牌范围（手牌为空）
        BattleState result = battleService.playCard(sessionId, 999);

        assertNotNull(result, "应返回战斗状态而非崩溃");
        assertSame(battle, result, "应返回同一战斗状态对象");
    }

    @Test
    @DisplayName("endTurn 执行敌人回合")
    void testEndTurn_executesEnemyTurn() {
        String sessionId = "test-endturn-session";

        // 准备玩家（需要牌组供回合开始时抽牌）
        GameCharacter player = new GameCharacter();
        player.setCharacterClass(CharacterClass.SUN_WUKONG);
        player.setMaxHp(80);
        player.setHp(80);
        player.setMaxEnergy(3);
        player.setEnergy(3);
        Card card = new Card("挥棒", "攻击", CardType.ATTACK, Rarity.BASIC, null, 1);
        card.setDamage(6);
        for (int i = 0; i < 5; i++) {
            player.addCard(card.copy());
        }
        // 初始化战斗并抽牌
        player.initBattle();
        player.drawCards(5);
        assertFalse(player.getHand().isEmpty(), "前置条件：玩家应已抽到手牌");

        // 准备敌人
        Enemy enemy = new Enemy("小妖", 30, 10, 2, false, 1);
        enemy.setMovePattern(List.of("attack"));
        BattleState battle = new BattleState(enemy);
        battle.startBattle();

        GameSession session = new GameSession(sessionId, player, List.of());
        session.setBattle(battle);

        when(gameService.getSession(sessionId)).thenReturn(session);

        int initialTurn = battle.getTurnNumber();
        assertEquals(1, initialTurn, "前置条件：初始回合数应为 1");

        BattleState result = battleService.endTurn(sessionId);

        assertNotNull(result, "应返回战斗状态");
        assertSame(battle, result, "应返回同一战斗状态对象");
        assertTrue(battle.getTurnNumber() > initialTurn, "结束回合后回合数应增加");
        assertTrue(battle.isPlayerTurn(), "敌人回合结束后应回到玩家回合");
        assertFalse(player.getHand().isEmpty(), "新回合开始后玩家应已抽到手牌");
        // 敌人攻击了玩家（attack=10），玩家 hp 应减少
        assertTrue(player.getHp() < 80, "玩家应受到敌人攻击伤害");

        verify(gameService).saveSession(session);
    }
}
