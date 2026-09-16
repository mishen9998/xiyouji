package com.xiyouji.combat;

import com.xiyouji.model.Enemy;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.enums.BuffType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class EnemyTurnResolverTest {
    private final EnemyTurnResolver resolver = new EnemyTurnResolver();
    record Golden(EnemyActionDefinition definition, int playerHp, int enemyBlock, int enemyStrength, int weak) {}

    static Stream<Golden> goldenActions() {
        return Stream.of(
                new Golden(EnemyActionDefinition.attack(100), 84, 0, 0, 0),
                new Golden(EnemyActionDefinition.allAttack(80), 88, 0, 0, 0),
                new Golden(EnemyActionDefinition.multiHit(45, 3), 77, 0, 0, 0),
                new Golden(EnemyActionDefinition.defend(), 100, 5, 0, 0),
                new Golden(EnemyActionDefinition.attackDefend(75), 89, 5, 0, 0),
                new Golden(EnemyActionDefinition.strength(2), 100, 0, 2, 0),
                new Golden(EnemyActionDefinition.status(EnemyActionDefinition.TargetScope.SINGLE, BuffType.WEAK, 2), 100, 0, 0, 2));
    }

    @ParameterizedTest @MethodSource("goldenActions")
    void sevenActionsMatchFixedGoldenNumbersAndDoNotMutateInput(Golden golden) {
        CombatantState enemy = new CombatantState(100, 70, 0, Map.of());
        CombatantState player = new CombatantState(100, 4, 0, Map.of());
        var action = resolver.preview(golden.definition(), 20, 5, enemy, List.of("p"), 471);
        CombatDelta result = resolver.resolve(enemy, Map.of("p", player), action);
        assertEquals(golden.playerHp(), result.players().get("p").after().hp());
        assertEquals(golden.enemyBlock(), result.enemy().after().block());
        assertEquals(golden.enemyStrength(), result.enemy().after().strength());
        assertEquals(golden.weak(), result.players().get("p").after().buffs().getOrDefault(BuffType.WEAK, 0));
        assertEquals(CombatDelta.Outcome.CONTINUE, result.outcome());
        assertEquals(70, enemy.block());
        assertEquals(100, player.hp());
        assertEquals(result, resolver.resolve(enemy, Map.of("p", player), action));
    }

    @Test void allAttacksAndAllStatusesAffectExactlyTheLockedLivingRoster() {
        CombatantState enemy = new CombatantState(100, 0, 0, Map.of());
        CombatantState player = new CombatantState(100, 0, 0, Map.of());
        var attack = resolver.preview(EnemyActionDefinition.allAttack(80), 20, 5, enemy, List.of("c", "a", "b"), 1);
        assertEquals(List.of("a", "b", "c"), attack.targetUserIds());
        var result = resolver.resolve(enemy, Map.of("a", player, "b", player, "c", player, "late", player), attack);
        for (String id : attack.targetUserIds()) assertEquals(84, result.players().get(id).after().hp());
        assertEquals(100, result.players().get("late").after().hp());
        var status = resolver.preview(EnemyActionDefinition.status(EnemyActionDefinition.TargetScope.ALL, BuffType.WEAK, 2),
                20, 5, enemy, List.of("a", "b"), 1);
        var statusResult = resolver.resolve(enemy, Map.of("a", player, "b", player), status);
        assertTrue(statusResult.players().values().stream().allMatch(c -> c.after().buffs().get(BuffType.WEAK) == 2));
    }

    @Test void deadLockedTargetIsSkippedWithoutRetargetingAndPreviewIsSeedStable() {
        CombatantState enemy = new CombatantState(100, 0, 0, Map.of());
        var action = resolver.preview(EnemyActionDefinition.multiHit(50, 3), 20, 5, enemy, List.of("b", "a"), 22);
        assertEquals(action, resolver.preview(EnemyActionDefinition.multiHit(50, 3), 20, 5, enemy, List.of("a", "b"), 22));
        String target = action.targetUserIds().get(0);
        String other = target.equals("a") ? "b" : "a";
        var result = resolver.resolve(enemy, Map.of(target, new CombatantState(0, 0, 0, Map.of()),
                other, new CombatantState(100, 0, 0, Map.of())), action);
        assertTrue(result.hits().isEmpty());
        assertEquals(100, result.players().get(other).after().hp());
    }

    @Test void poisonIgnoresVulnerabilityAndBlockForBothSidesAndTicksOnce() {
        CombatantState enemy = new CombatantState(100, 90, 0, Map.of(BuffType.POISON, 3, BuffType.VULNERABLE, 2));
        CombatantState player = new CombatantState(100, 90, 0, Map.of(BuffType.POISON, 4, BuffType.VULNERABLE, 2));
        var action = resolver.preview(EnemyActionDefinition.defend(), 20, 5, enemy, List.of("p"), 1);
        var result = resolver.resolve(enemy, Map.of("p", player), action);
        assertEquals(97, result.enemy().after().hp());
        assertEquals(5, result.enemy().after().block());
        assertEquals(2, result.enemy().after().buffs().get(BuffType.POISON));
        assertEquals(96, result.players().get("p").after().hp());
        assertEquals(90, result.players().get("p").after().block());
        assertEquals(3, result.players().get("p").after().buffs().get(BuffType.POISON));
    }

    @Test void enemyPoisonDeathWinsBeforeActingAndPlayerPoisonDeathLosesBeforeNextTurn() {
        var enemy = new CombatantState(3, 99, 0, Map.of(BuffType.POISON, 3));
        var player = new CombatantState(2, 99, 0, Map.of(BuffType.POISON, 3));
        var action = resolver.preview(EnemyActionDefinition.attack(100), 20, 5, enemy, List.of("p"), 0);
        var win = resolver.resolve(enemy, Map.of("p", player), action);
        assertEquals(CombatDelta.Outcome.VICTORY, win.outcome());
        assertTrue(win.hits().isEmpty());
        assertEquals(2, win.players().get("p").after().hp());
        var lose = resolver.resolve(new CombatantState(100, 0, 0, Map.of()), Map.of("p", player), action);
        assertEquals(CombatDelta.Outcome.DEFEAT, lose.outcome());
        assertEquals(0, lose.players().get("p").after().hp());
    }

    @Test void strengthPersistsActuallyAddsDamageAndCapsAtSix() {
        var enemy = new CombatantState(100, 0, 0, Map.of());
        var players = Map.of("p", new CombatantState(100, 0, 0, Map.of()));
        for (int i = 1; i <= 6; i++) {
            var gain = resolver.preview(EnemyActionDefinition.strength(2), 20, 5, enemy, List.of("p"), 0);
            enemy = resolver.resolve(enemy, players, gain).enemy().after();
            assertEquals(Math.min(6, i * 2), enemy.strength());
        }
        assertEquals(26, resolver.preview(EnemyActionDefinition.attack(100), 20, 5, enemy, List.of("p"), 0).damagePerHit());
        assertEquals(0, resolver.preview(EnemyActionDefinition.strength(2), 20, 5, enemy, List.of("p"), 0).strengthGain());
    }

    @Test void defendRemainsDuringPlayerPhaseAndExpiresAtNextEnemyAction() {
        Enemy enemy = new Enemy("guard", 100, 20, 5, false, 1);
        enemy.setActionDefinitions(List.of(EnemyActionDefinition.defend(), EnemyActionDefinition.attack(100)));
        GameCharacter player = new GameCharacter();
        player.setHp(100);
        EnemyCombat.lockNextAction(enemy, List.of("p"), 0);
        EnemyCombat.execute(enemy, Map.of("p", player));
        assertEquals(5, enemy.getBlock());
        EnemyCombat.lockNextAction(enemy, List.of("p"), 0);
        assertEquals(5, enemy.getBlock(), "Planning must not consume block");
        assertEquals(0, enemy.takeDamage(3));
        assertEquals(2, enemy.getBlock());
        EnemyCombat.execute(enemy, Map.of("p", player));
        assertEquals(0, enemy.getBlock());
    }

    @Test void freshlyAppliedOneTurnWeakSurvivesUntilPlayerCanAct() {
        var enemy = new CombatantState(100, 0, 0, Map.of());
        var player = new CombatantState(100, 0, 0, Map.of(BuffType.WEAK, 1));
        var weak = resolver.preview(EnemyActionDefinition.status(EnemyActionDefinition.TargetScope.SINGLE, BuffType.WEAK, 1),
                20, 5, enemy, List.of("p"), 0);
        var first = resolver.resolve(enemy, Map.of("p", player), weak).players().get("p").after();
        assertEquals(1, first.buffs().get(BuffType.WEAK), "The expired old stack must not consume the new stack");
        var defend = resolver.preview(EnemyActionDefinition.defend(), 20, 5, enemy, List.of("p"), 0);
        assertFalse(resolver.resolve(enemy, Map.of("p", first), defend).players().get("p").after().buffs().containsKey(BuffType.WEAK));
    }

    @Test void multiHitUsesVulnerabilityAndConsumesBlockPerHitWithIntegerRounding() {
        var enemy = new CombatantState(100, 0, 2, Map.of(BuffType.WEAK, 1));
        var player = new CombatantState(100, 10, 0, Map.of(BuffType.VULNERABLE, 1));
        var action = resolver.preview(EnemyActionDefinition.multiHit(45, 3), 19, 5, enemy, List.of("p"), 0);
        assertEquals(6, action.damagePerHit());
        var result = resolver.resolve(enemy, Map.of("p", player), action);
        assertEquals(83, result.players().get("p").after().hp());
        assertEquals(List.of(0, 8, 9), result.hits().stream().map(CombatDelta.Hit::hpLost).toList());
    }

    @Test void unknownMoveFailsBeforeBattleAndPreviewDoesNotGrantDefense() {
        Enemy enemy = new Enemy("bad", 100, 20, 5, false, 1);
        enemy.setMovePattern(List.of("attack", "typo"));
        assertThrows(IllegalArgumentException.class, () -> EnemyCombat.validate(enemy));
        assertEquals(0, enemy.getPatternIndex());
        enemy.setMovePattern(List.of("attack_defend"));
        enemy.chooseIntent();
        assertEquals(0, enemy.getBlock());
        assertEquals(5, enemy.getLockedAction().block());
    }
}
