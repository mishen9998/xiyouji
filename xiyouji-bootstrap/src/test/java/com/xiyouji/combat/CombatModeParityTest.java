package com.xiyouji.combat;

import com.xiyouji.config.RedisConfig;
import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import com.xiyouji.service.battle.*;
import com.xiyouji.service.room.*;
import com.xiyouji.service.session.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatModeParityTest {
    static Stream<EnemyActionDefinition> actions() {
        return Stream.of(EnemyActionDefinition.attack(100), EnemyActionDefinition.allAttack(80),
                EnemyActionDefinition.multiHit(45, 3), EnemyActionDefinition.defend(),
                EnemyActionDefinition.attackDefend(75), EnemyActionDefinition.strength(2),
                EnemyActionDefinition.status(EnemyActionDefinition.TargetScope.SINGLE, BuffType.WEAK, 1));
    }

    private GameCharacter player() {
        GameCharacter p = new GameCharacter();
        p.setCharacterClass(CharacterClass.values()[0]);
        p.setHp(100);
        p.setMaxHp(100);
        p.setBlock(4);
        p.getBuffs().put(BuffType.POISON, 2);
        return p;
    }

    private Enemy enemy(EnemyActionDefinition action) {
        Enemy enemy = new Enemy("fixture", 100, 20, 5, false, 1);
        enemy.setId(123L);
        enemy.setActionDefinitions(new ArrayList<>(List.of(action, EnemyActionDefinition.attack(100))));
        enemy.addBuff(BuffType.POISON, 2);
        return enemy;
    }

    @ParameterizedTest @MethodSource("actions")
    void soloAndMultiplayerProduceIdenticalDeltaAndNextPhase(EnemyActionDefinition action) {
        Enemy template = enemy(action);
        GameCharacter soloPlayer = player();
        GameCharacter multiPlayer = player();
        BattleState solo = new BattleState(template);
        solo.startBattle("p");
        MultiplayerBattleState multi = new MultiplayerBattleState("room");
        multi.setEnemy(template.copy());
        multi.setPlayers(new ArrayList<>(List.of(new MultiplayerPlayer("p", "player", multiPlayer))));
        MultiplayerTurnCoordinator.lockNextAction(multi, 0);

        EnemyTurnResolver resolver = new EnemyTurnResolver();
        assertEquals(solo.getEnemy().getLockedAction(), multi.getEnemy().getLockedAction());
        assertEquals(resolver.resolve(EnemyCombat.snapshot(solo.getEnemy()), Map.of("p", EnemyCombat.snapshot(soloPlayer)), solo.getEnemy().getLockedAction()),
                resolver.resolve(EnemyCombat.snapshot(multi.getEnemy()), Map.of("p", EnemyCombat.snapshot(multiPlayer)), multi.getEnemy().getLockedAction()));

        solo.endPlayerTurn(soloPlayer);
        new MultiplayerTurnCoordinator(mock(RewardService.class)).endTurn(multi, "p");
        assertEquals(EnemyCombat.snapshot(solo.getEnemy()), EnemyCombat.snapshot(multi.getEnemy()));
        assertEquals(EnemyCombat.snapshot(soloPlayer), EnemyCombat.snapshot(multiPlayer));
        assertEquals(solo.getTurnNumber(), multi.getTurnNumber());
        assertEquals(solo.getEnemy().getLockedAction(), multi.getEnemy().getLockedAction());
        assertEquals(solo.isBattleOver(), multi.isBattleOver());
    }

    @Test void playerPoisonDefeatPreventsNewPhaseInBothModes() {
        GameCharacter soloPlayer = player();
        GameCharacter multiPlayer = player();
        soloPlayer.setHp(1);
        multiPlayer.setHp(1);
        BattleState solo = new BattleState(enemy(EnemyActionDefinition.defend()));
        solo.startBattle("p");
        MultiplayerBattleState multi = new MultiplayerBattleState("room");
        multi.setEnemy(enemy(EnemyActionDefinition.defend()));
        multi.setPlayers(new ArrayList<>(List.of(new MultiplayerPlayer("p", "p", multiPlayer))));
        MultiplayerTurnCoordinator.lockNextAction(multi, 0);
        solo.endPlayerTurn(soloPlayer);
        new MultiplayerTurnCoordinator(mock(RewardService.class)).endTurn(multi, "p");
        assertTrue(solo.isBattleOver());
        assertTrue(multi.isBattleOver());
        assertFalse(solo.isVictory());
        assertFalse(multi.isVictory());
        assertEquals(1, solo.getTurnNumber());
        assertEquals(1, multi.getTurnNumber());
    }

    @Test void playerWeakCardRefreshesDamageWithoutChangingLockedActionTargetsOrCycle() {
        Enemy enemy = enemy(EnemyActionDefinition.multiHit(45, 3));
        GameCharacter player = player();
        player.setEnergy(3);
        Card card = new Card("weak", "", CardType.SKILL, Rarity.COMMON, null, 1);
        card.setWeakTurns(1);
        player.getHand().add(card);
        BattleState state = new BattleState(enemy);
        state.startBattle("p");
        LockedEnemyAction before = state.getEnemy().getLockedAction();
        assertTrue(state.playPlayerCard(player, 0, 0, 0));
        assertEquals(before, state.getEnemy().getLockedAction());
        assertEquals(1, state.getEnemy().getPatternIndex());
        assertEquals(9, before.damagePerHit());
        assertEquals(6, state.getEnemy().getIntentValue(), "One-turn WEAK must affect the immediately following enemy action");
        LockedEnemyAction refreshed = EnemyCombat.currentForecast(state.getEnemy());
        assertEquals(before.targetUserIds(), refreshed.targetUserIds());
        assertEquals(before.hits(), refreshed.hits());
        assertEquals(before.actionType(), refreshed.actionType());
        GameSession session = new GameSession("session", player, List.of());
        session.setBattle(state);
        assertEquals(6, ((Map<?, ?>) new SoloBattleInfoAssembler().toBattleInfo(session).get("enemy")).get("intentValue"));
        player.getBuffs().clear();
        int initialHp = player.getHp();
        var forecast = EnemyCombat.targetDamageForecast(state.getEnemy(), Map.of("p", player)).get("p");
        state.endPlayerTurn(player);
        assertEquals(forecast.hpLoss(), initialHp - player.getHp());
        assertEquals(14, initialHp - player.getHp());
    }

    @Test @SuppressWarnings("unchecked")
    void redisRoundTripPreservesDefinitionsAndLockedTargetsAndBothPublicContracts() {
        MultiplayerBattleState state = new MultiplayerBattleState("room");
        state.setEnemy(enemy(EnemyActionDefinition.allAttack(80)));
        state.getEnemy().setRulesVersion(CombatRules.VERSION);
        state.setPlayers(new ArrayList<>(List.of(new MultiplayerPlayer("a", "A", player()), new MultiplayerPlayer("b", "B", player()))));
        MultiplayerTurnCoordinator.lockNextAction(state, 314);
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) new RedisConfig()
                .redisTemplate(mock(RedisConnectionFactory.class)).getValueSerializer();
        MultiplayerBattleState restored = (MultiplayerBattleState) serializer.deserialize(serializer.serialize(state));
        assertEquals(state.getEnemy().getLockedAction(), restored.getEnemy().getLockedAction());
        assertEquals(state.getEnemy().getActionDefinitions(), restored.getEnemy().getActionDefinitions());
        assertEquals(123L, restored.getEnemy().getId());
        Map<String, Object> info = new MultiplayerBattleInfoAssembler().toBattleInfo(restored);
        Map<String, Object> enemyInfo = (Map<String, Object>) info.get("enemy");
        assertEquals("ATTACK", enemyInfo.get("intent"));
        assertEquals(16, enemyInfo.get("intentValue"));
        assertEquals(List.of("a", "b"), enemyInfo.get("intentTargetUserIds"));
        assertEquals(1, enemyInfo.get("intentHits"));
        assertEquals("ATTACK_ALL", ((Map<?, ?>) enemyInfo.get("intentEffects")).get("actionType"));
        assertInstanceOf(Map.class, enemyInfo.get("buffs"));
        assertTrue(info.containsKey("stateVersion"));

        GameSession session = new GameSession("s", player(), List.of());
        session.setBattle(new BattleState(enemy(EnemyActionDefinition.defend())));
        session.getBattle().startBattle("a");
        GameSession restoredSession = (GameSession) serializer.deserialize(serializer.serialize(session));
        Map<String, Object> soloInfo = new SoloBattleInfoAssembler().toBattleInfo(restoredSession);
        Map<String, Object> soloEnemy = (Map<String, Object>) soloInfo.get("enemy");
        assertEquals(CombatRules.VERSION, soloInfo.get("rulesVersion"));
        assertInstanceOf(List.class, soloEnemy.get("buffs"));
        assertEquals(5, soloEnemy.get("intentValue"));
        assertEquals(5, ((Map<?, ?>) soloEnemy.get("intentEffects")).get("block"));
    }

    @Test void snapshotWithoutNewFieldsKeepsItsAlreadyAnnouncedTargetAndValue() {
        Enemy enemy = new Enemy("legacy", 100, 99, 5, false, 1);
        enemy.setIntent(EnemyIntent.ATTACK);
        enemy.setIntentValue(7);
        var restored = EnemyCombat.restoreLegacyForecast(enemy, List.of("old-target"));
        assertEquals(7, restored.damagePerHit());
        assertEquals(List.of("old-target"), restored.targetUserIds());
        assertEquals(CombatRules.LEGACY_VERSION, EnemyForecastInfo.rulesVersion(enemy));
        assertEquals(0, enemy.getPatternIndex());
    }
}
