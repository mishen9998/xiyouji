package com.xiyouji.combat;

import com.xiyouji.model.Enemy;
import com.xiyouji.model.GameCharacter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Thin domain adapter. Both game modes call this adapter and consume the same CombatDelta. */
public final class EnemyCombat {
    public static final String SOLO_PLAYER = "solo";
    private static final EnemyTurnResolver RESOLVER = new EnemyTurnResolver();
    private EnemyCombat() {}

    public static CombatantState snapshot(Enemy enemy) {
        return new CombatantState(enemy.getHp(), enemy.getBlock(), enemy.getStrength(), enemy.getBuffs());
    }
    public static CombatantState snapshot(GameCharacter player) {
        return new CombatantState(player.getHp(), player.getBlock(), player.getStrength(), player.getBuffs());
    }

    public static LockedEnemyAction currentForecast(Enemy enemy) {
        return enemy.getLockedAction() == null ? null : RESOLVER.refreshForecast(enemy.getLockedAction(), snapshot(enemy));
    }

    public static Map<String, EnemyTurnResolver.DamageForecast> targetDamageForecast(Enemy enemy, Map<String, GameCharacter> players) {
        if (enemy.getLockedAction() == null) return Map.of();
        Map<String, CombatantState> states = new LinkedHashMap<>();
        players.forEach((id, player) -> states.put(id, snapshot(player)));
        return RESOLVER.forecastDamage(snapshot(enemy), states, enemy.getLockedAction());
    }

    /** Validate the entire cycle before entering combat, not halfway through a battle. */
    public static void validate(Enemy enemy) { definitions(enemy); }

    private static List<EnemyActionDefinition> definitions(Enemy enemy) {
        if (enemy.getActionDefinitions() != null && !enemy.getActionDefinitions().isEmpty()) {
            return List.copyOf(enemy.getActionDefinitions());
        }
        if (enemy.getMovePattern() == null || enemy.getMovePattern().isEmpty()) {
            // Unversioned old templates have no persisted pattern. T2 supplies the versioned catalog.
            return List.of(EnemyActionDefinition.attack(100));
        }
        return enemy.getMovePattern().stream().map(EnemyActionDefinition::legacyMove).toList();
    }

    /** Call exactly once when opening a player phase, after deaths/status ticks have settled. */
    public static LockedEnemyAction lockNextAction(Enemy enemy, List<String> livingUserIds, long seed) {
        List<EnemyActionDefinition> definitions = definitions(enemy);
        int index = Math.floorMod(enemy.getPatternIndex(), definitions.size());
        LockedEnemyAction action = RESOLVER.preview(definitions.get(index), enemy.getAttack(), enemy.getDefense(),
                snapshot(enemy), livingUserIds, seed);
        enemy.setPatternIndex(enemy.getPatternIndex() + 1);
        enemy.setLockedAction(action);
        enemy.setIntent(action.legacyIntent());
        enemy.setIntentValue(action.legacyIntentValue());
        return action;
    }

    /** Existing snapshots have an intent and target but no locked packet. Preserve that forecast. */
    public static LockedEnemyAction restoreLegacyForecast(Enemy enemy, List<String> existingTargetIds) {
        if (enemy.getLockedAction() != null) return enemy.getLockedAction();
        var intent = enemy.getIntent();
        if (intent == null) throw new IllegalStateException("Enemy snapshot has no intent");
        int value = Math.max(0, enemy.getIntentValue());
        LockedEnemyAction action = switch (intent) {
            case ATTACK -> new LockedEnemyAction(EnemyActionType.ATTACK, existingTargetIds, 1, value, 0, 0, Map.of());
            case DEFEND -> new LockedEnemyAction(EnemyActionType.DEFEND, List.of(), 0, 0, value, 0, Map.of());
            case BUFF -> new LockedEnemyAction(EnemyActionType.GAIN_STRENGTH, List.of(), 0, 0, 0,
                    Math.min(value, Math.max(0, CombatRules.MAX_ENEMY_STRENGTH - enemy.getStrength())), Map.of());
            default -> throw new IllegalStateException("Cannot restore legacy enemy intent: " + intent);
        };
        enemy.setLockedAction(action);
        return action;
    }

    public static CombatDelta execute(Enemy enemy, Map<String, GameCharacter> players) {
        if (enemy.getLockedAction() == null) throw new IllegalStateException("Enemy action must be locked before execution");
        Map<String, CombatantState> before = new LinkedHashMap<>();
        players.forEach((id, player) -> before.put(id, snapshot(player)));
        CombatDelta delta = RESOLVER.resolve(snapshot(enemy), before, enemy.getLockedAction());
        CombatantState after = delta.enemy().after();
        enemy.setHp(after.hp());
        enemy.setBlock(after.block());
        enemy.setStrength(after.strength());
        enemy.setBuffs(new HashMap<>(after.buffs()));
        delta.players().forEach((id, change) -> {
            GameCharacter player = players.get(id);
            player.setHp(change.after().hp());
            player.setBlock(change.after().block());
            player.setBuffs(new HashMap<>(change.after().buffs()));
        });
        return delta;
    }
}
