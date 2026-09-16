package com.xiyouji.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiyouji.config.RedisConfig;
import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import com.xiyouji.service.battle.*;
import com.xiyouji.service.room.*;
import com.xiyouji.service.session.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyEnemySnapshotTest {
    @SuppressWarnings("unchecked")
    private final RedisSerializer<Object> serializer = (RedisSerializer<Object>) new RedisConfig()
            .redisTemplate(mock(RedisConnectionFactory.class)).getValueSerializer();
    private final ObjectMapper json = new ObjectMapper();

    private GameCharacter player() {
        GameCharacter p = new GameCharacter();
        p.setCharacterClass(CharacterClass.SUN_WUKONG);
        p.setHp(1000);
        p.setMaxHp(1000);
        return p;
    }

    private Enemy legacyEnemy(boolean withPattern) {
        Enemy e = new Enemy("红孩儿", 1000, 20, 7, false, 2);
        e.setId(5L);
        e.setIntent(EnemyIntent.ATTACK);
        e.setIntentValue(7);
        e.setPatternIndex(1);
        if (withPattern) e.setMovePattern(new ArrayList<>(List.of("attack", "attack", "attack", "buff")));
        return e;
    }

    private Object restoreWithoutNewFields(Object state) throws Exception {
        ObjectNode tree = (ObjectNode) json.readTree(serializer.serialize(state));
        ObjectNode enemy = (ObjectNode) tree.get("enemy");
        for (String field : List.of("rulesVersion", "actionIndex", "encounterId", "contentKey",
                "contentVersion", "actionDefinitions", "lockedAction")) enemy.remove(field);
        tree.remove("playerUserId");
        return serializer.deserialize(json.writeValueAsBytes(tree));
    }

    @Test void soloOldSnapshotContinuesItsEntireOriginalCycleUntilNextBattle() throws Exception {
        BattleState state = new BattleState();
        state.setEnemy(legacyEnemy(true));
        state.setStateVersion(19);
        BattleState restored = (BattleState) restoreWithoutNewFields(state);
        assertEquals(CombatRules.LEGACY_VERSION, EnemyForecastInfo.rulesVersion(restored.getEnemy()));
        assertNull(restored.getEnemy().getEncounterId());
        assertEquals(1, restored.getEnemy().getActionIndex());
        var p = player();
        restored.endPlayerTurn(p);
        assertEquals(993, p.getHp(), "The old published number 7 must execute, not base attack 20");
        assertEquals(EnemyActionType.ATTACK, restored.getEnemy().getLockedAction().actionType(),
                "Old turn 2 remains ATTACK; the v2 catalog's ALL80% must not leak into a running snapshot");
        for (int turn = 0; turn < 8; turn++) {
            var action = restored.getEnemy().getLockedAction();
            assertTrue(action.actionType() == EnemyActionType.ATTACK || action.actionType() == EnemyActionType.GAIN_STRENGTH);
            assertEquals(CombatRules.LEGACY_VERSION, restored.getEnemy().getRulesVersion());
            restored = (BattleState) serializer.deserialize(serializer.serialize(restored));
            restored.endPlayerTurn(p);
        }
        assertEquals(19, restored.getStateVersion());
        Enemy nextTemplate = legacyEnemy(true);
        EnemyContentCatalog.upgrade(nextTemplate);
        BattleState next = new BattleState(nextTemplate);
        next.startBattle("player");
        next.endPlayerTurn(p);
        assertEquals(CombatRules.VERSION, next.getEnemy().getRulesVersion());
        assertEquals(EnemyActionType.ATTACK_ALL, next.getEnemy().getLockedAction().actionType());
    }

    @Test void emptyPatternIsAllowedOnlyForRestoredLegacyBattlesAndSurvivesSeveralTurns() throws Exception {
        BattleState state = new BattleState();
        state.setEnemy(legacyEnemy(false));
        BattleState restored = (BattleState) restoreWithoutNewFields(state);
        for (int i = 0; i < 5; i++) restored.endPlayerTurn(player());
        assertEquals(CombatRules.LEGACY_VERSION, restored.getEnemy().getRulesVersion());
        assertEquals(EnemyActionType.ATTACK, restored.getEnemy().getLockedAction().actionType());
        assertThrows(IllegalArgumentException.class, () -> EnemyCombat.validate(restored.getEnemy()),
                "A legacy version tag must not permit an empty template to start a new battle");
        assertThrows(IllegalArgumentException.class, () -> new BattleState(legacyEnemy(false)).startBattle());
    }

    @Test void multiplayerLegacyTargetAndCursorRemainLockedAcrossRedisReloads() throws Exception {
        MultiplayerBattleState state = new MultiplayerBattleState("legacy-room");
        state.setEnemy(legacyEnemy(true));
        state.setPlayers(new ArrayList<>(List.of(new MultiplayerPlayer("a", "A", player()), new MultiplayerPlayer("b", "B", player()))));
        state.setTargetPlayerIndex(1);
        state.setStateVersion(31);
        MultiplayerBattleState restored = (MultiplayerBattleState) restoreWithoutNewFields(state);
        var coordinator = new MultiplayerTurnCoordinator(mock(RewardService.class));
        coordinator.executeEnemyTurn(restored);
        assertEquals(1000, restored.findPlayer("a").getCharacter().getHp());
        assertEquals(993, restored.findPlayer("b").getCharacter().getHp());
        assertEquals(List.of("b"), restored.getEnemy().getLockedAction().targetUserIds());
        coordinator.startPlayerTurn(restored);
        assertEquals(EnemyActionType.ATTACK, restored.getEnemy().getLockedAction().actionType());
        for (int i = 0; i < 5; i++) {
            var before = restored.getEnemy().getLockedAction();
            restored = (MultiplayerBattleState) serializer.deserialize(serializer.serialize(restored));
            assertEquals(before, restored.getEnemy().getLockedAction());
            coordinator.executeEnemyTurn(restored);
            coordinator.startPlayerTurn(restored);
            assertEquals(CombatRules.LEGACY_VERSION, restored.getEnemy().getRulesVersion());
            assertEquals(31, restored.getStateVersion());
        }
    }

    @Test void newSnapshotPreservesContentIdentityAndDoesNotRefreshTheRandomChoice() {
        Enemy e = legacyEnemy(false);
        EnemyContentCatalog.upgrade(e);
        e.setEncounterId("L2-R9-C2");
        BattleState state = new BattleState(e);
        state.startBattle("p");
        var restored = (BattleState) serializer.deserialize(serializer.serialize(state));
        assertEquals(state.getEnemy().getContentKey(), restored.getEnemy().getContentKey());
        assertEquals(2, restored.getEnemy().getContentVersion());
        assertEquals("L2-R9-C2", restored.getEnemy().getEncounterId());
        assertEquals(1, restored.getEnemy().getActionIndex());
        assertEquals(state.getEnemy().getLockedAction(), restored.getEnemy().getLockedAction());
    }
}
