package com.xiyouji.combat;

import com.xiyouji.model.*;
import com.xiyouji.port.EnemyRepositoryPort;
import com.xiyouji.service.MapService;
import com.xiyouji.service.room.MultiplayerMapService;
import com.xiyouji.service.battle.EnemyBehaviorGuard;
import com.xiyouji.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncounterCatalogTest {
    static List<Enemy> fixtures() {
        List<Enemy> result = new ArrayList<>();
        long id = 101;
        for (var entry : EnemyContentCatalog.entries()) {
            Enemy enemy = new Enemy(entry.name(), 100, 20, 7, entry.boss(), entry.level());
            enemy.setId(id++);
            enemy.setEmoji("retained");
            EnemyContentCatalog.upgrade(enemy);
            result.add(enemy);
        }
        return result;
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "寅将军|ATTACK:100:1;DEFEND:0:0;ATTACK:150:1",
        "熊山君|DEFEND:0:0;ATTACK:100:1;MULTI_HIT:45:3",
        "白衣秀士|ATTACK:100:1;APPLY_STATUS:WEAK:1:SINGLE;ATTACK_DEFEND:75:1",
        "黑熊精|ATTACK:100:1;ATTACK_DEFEND:75:1;GAIN_STRENGTH:2;ATTACK:150:1",
        "红孩儿|ATTACK:100:1;ATTACK_ALL:80:1;GAIN_STRENGTH:2;ATTACK:150:1",
        "黄风怪|ATTACK_DEFEND:75:1;APPLY_STATUS:WEAK:2:ALL;ATTACK:100:1",
        "如意真仙|APPLY_STATUS:VULNERABLE:1:SINGLE;DEFEND:0:0;MULTI_HIT:45:3;ATTACK:100:1",
        "牛魔王|ATTACK_ALL:80:1;DEFEND:0:0;GAIN_STRENGTH:2;MULTI_HIT:50:3",
        "黄狮精|ATTACK:100:1;ATTACK_DEFEND:75:1;ATTACK:150:1;GAIN_STRENGTH:1",
        "七狮|ATTACK_ALL:70:1;MULTI_HIT:45:3;DEFEND:0:0;APPLY_STATUS:WEAK:1:ALL",
        "铁背苍狼怪|APPLY_STATUS:WEAK:1:SINGLE;ATTACK_DEFEND:75:1;APPLY_STATUS:VULNERABLE:1:SINGLE;ATTACK:150:1",
        "大鹏|MULTI_HIT:50:3;APPLY_STATUS:WEAK:2:ALL;ATTACK_ALL:100:1;GAIN_STRENGTH:2"
    })
    void twelveExactCyclesAndAllForecasts(String name, String expected) {
        var definitions = EnemyContentCatalog.require(name).actions();
        assertEquals(expected, String.join(";", definitions.stream().map(a -> {
            if (a.actionType() == EnemyActionType.GAIN_STRENGTH) return a.actionType() + ":" + a.strengthGain();
            if (a.actionType() == EnemyActionType.APPLY_STATUS) {
                var status = a.statusEffects().entrySet().iterator().next();
                return a.actionType() + ":" + status.getKey() + ":" + status.getValue() + ":" + a.targetScope();
            }
            return a.actionType() + ":" + a.damagePercent() + ":" + a.hits();
        }).toList()));
        for (int party : List.of(1, 2, 5)) {
            Enemy enemy = fixtures().stream().filter(e -> e.getName().equals(name)).findFirst().orElseThrow();
            List<String> players = new ArrayList<>();
            for (int i = 0; i < party; i++) players.add("p" + i);
            for (int i = 0; i < definitions.size() * 2; i++) {
                var definition = definitions.get(i % definitions.size());
                var locked = EnemyCombat.lockNextAction(enemy, players, 123);
                assertEquals(definition.actionType(), locked.actionType());
                assertEquals(definition.hits(), locked.hits());
                assertEquals(20 * definition.damagePercent() / 100, locked.damagePerHit());
                assertEquals(definition.statusEffects(), locked.statusEffects());
                assertEquals(definition.strengthGain(), locked.strengthGain());
                assertEquals(definition.actionType() == EnemyActionType.DEFEND
                        || definition.actionType() == EnemyActionType.ATTACK_DEFEND ? 7 : 0, locked.block());
                assertEquals(switch (definition.targetScope()) { case ALL -> party; case SINGLE -> 1; case SELF -> 0; },
                        locked.targetUserIds().size());
                assertEquals(locked, EnemyCombat.currentForecast(enemy));
                assertEquals(0, enemy.getBlock(), "A new forecast never grants block");
            }
        }
    }

    @Test void rosterPreservesAll63EntriesAndUpgradeIsIdempotent() {
        var enemies = fixtures();
        assertEquals(63, enemies.size());
        assertEquals(35, enemies.stream().filter(e -> !e.isBoss()).count());
        assertEquals(28, enemies.stream().filter(Enemy::isBoss).count());
        assertEquals(63, enemies.stream().map(Enemy::getContentKey).distinct().count());
        for (Enemy enemy : enemies) {
            Long id = enemy.getId();
            var actions = enemy.getActionDefinitions();
            assertFalse(EnemyContentCatalog.upgrade(enemy));
            assertSame(actions, enemy.getActionDefinitions());
            assertEquals(id, enemy.getId());
            assertEquals(100, enemy.getHp());
            assertEquals(20, enemy.getAttack());
            assertEquals("retained", enemy.getEmoji());
            EnemyCombat.validate(enemy);
        }
    }

    @Test void fixedSeedChoosesSeventyThirtyPoolsWithoutLevelLeakOrBossFallback() {
        List<Enemy> enemies = fixtures();
        Enemy intruder = new Enemy("unknown future enemy", 1, 1, 0, false, 1);
        intruder.setId(999L);
        enemies.add(intruder);
        EncounterCatalog catalog = new EncounterCatalog(enemies);
        var reverse = new ArrayList<>(enemies);
        Collections.reverse(reverse);
        EncounterCatalog replay = new EncounterCatalog(reverse);
        for (int layer = 1; layer <= 3; layer++) {
            int focus = 0;
            for (long seed = 0; seed < 10000; seed++) {
                var selection = catalog.select(layer, false, seed);
                if (new Random(seed).nextInt(100) < 70) {
                    assertEquals(EncounterCatalog.Pool.FOCUS, selection.pool());
                    focus++;
                } else assertEquals(EncounterCatalog.Pool.COMPATIBILITY, selection.pool());
                assertEquals(selection.template().getId(), replay.select(layer, false, seed).template().getId());
                assertFalse(selection.template().isBoss());
                assertTrue(selection.template().getLevel() <= layer);
                assertNotEquals(999L, selection.template().getId());
            }
            assertTrue(focus > 6800 && focus < 7200, "Observed focus count: " + focus);
            assertEquals(3, catalog.pool(layer, EncounterCatalog.Pool.FOCUS).size());
            assertEquals(EncounterCatalog.chapter(layer).boss(), catalog.select(layer, true, 7).template().getName());
            assertTrue(catalog.pool(layer, EncounterCatalog.Pool.COMPATIBILITY).stream().allMatch(e -> !e.isBoss()));
        }
        assertEquals(32, catalog.pool(3, EncounterCatalog.Pool.COMPATIBILITY).size());
        assertTrue(catalog.pool(3, EncounterCatalog.Pool.COMPATIBILITY).stream().anyMatch(e -> e.getLevel() == 1));
        assertThrows(IllegalArgumentException.class, () -> catalog.select(4, false, 1));
        assertThrows(IllegalStateException.class, () -> new EncounterCatalog(List.of(intruder)).select(3, false, 1));
    }

    @Test void entireMapReplaysSeedAndUsesFixedBossIdsWithoutMutatingTemplates() {
        var enemies = fixtures();
        EnemyRepositoryPort repo = mock(EnemyRepositoryPort.class);
        when(repo.findAll()).thenReturn(enemies);
        MapService maps = new MapService(repo);
        MultiplayerMapService multiMaps = new MultiplayerMapService(repo);
        for (int layer = 1; layer <= 3; layer++) {
            var first = maps.generateLayer(layer, 20260917);
            var second = maps.generateLayer(layer, 20260917);
            assertEquals(first.stream().map(n -> n.getId() + n.getType() + n.getEnemyId() + n.getConnections()).toList(),
                    second.stream().map(n -> n.getId() + n.getType() + n.getEnemyId() + n.getConnections()).toList());
            assertEquals(27, first.stream().map(MapNode::getRow).distinct().count());
            var boss = first.stream().filter(n -> "BOSS".equals(n.getType())).findFirst().orElseThrow();
            var expected = enemies.stream().filter(e -> e.getName().equals(EncounterCatalog.chapter(boss.getLayer()).boss())).findFirst().orElseThrow();
            assertEquals(expected.getId().toString(), boss.getEnemyId());
            var multi = multiMaps.generateLayer(layer, 20260917);
            var multiReplay = multiMaps.generateLayer(layer, 20260917);
            assertEquals(multi.stream().map(n -> n.getId() + n.getType() + n.getEnemyId() + n.getConnections()).toList(),
                    multiReplay.stream().map(n -> n.getId() + n.getType() + n.getEnemyId() + n.getConnections()).toList());
            assertEquals(27, multi.stream().map(MapNode::getRow).distinct().count());
            assertEquals(expected.getId().toString(), multi.stream().filter(n -> "BOSS".equals(n.getType()))
                    .findFirst().orElseThrow().getEnemyId());
            for (MapNode node : multi) {
                if (!"BATTLE".equals(node.getType())) continue;
                Enemy selected = enemies.stream().filter(e -> e.getId().toString().equals(node.getEnemyId())).findFirst().orElseThrow();
                assertFalse(selected.isBoss());
                assertTrue(selected.getLevel() <= layer);
            }
        }
        assertTrue(enemies.stream().allMatch(e -> e.getMaxHp() == 100 && e.getAttack() == 20 && e.getPatternIndex() == 0));
    }

    @Test void missingOrUnknownCycleHasDiagnosticErrorBeforeCombat() {
        Enemy enemy = new Enemy("corrupt", 10, 1, 1, false, 1);
        enemy.setId(17L);
        var missing = assertThrows(BusinessException.class, () -> EnemyBehaviorGuard.validate(enemy));
        assertEquals("ENEMY_BEHAVIOR_INVALID", missing.getErrorCode());
        assertTrue(missing.getMessage().contains("id=17"));
        enemy.setMovePattern(List.of("attack", "SUMMON_UNKNOWN"));
        assertTrue(assertThrows(BusinessException.class, () -> EnemyBehaviorGuard.validate(enemy)).getMessage().contains("SUMMON_UNKNOWN"));
        enemy.setRulesVersion("future-v9000");
        assertTrue(assertThrows(BusinessException.class, () -> EnemyBehaviorGuard.validate(enemy)).getMessage().contains("future-v9000"));
    }
}
