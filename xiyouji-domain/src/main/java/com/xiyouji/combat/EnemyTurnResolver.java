package com.xiyouji.combat;

import com.xiyouji.model.enums.BuffType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Pure shared rules: preview is deterministic for a seed; resolve never draws randomness or mutates input. */
public final class EnemyTurnResolver {
    public LockedEnemyAction preview(EnemyActionDefinition definition, int attack, int defense,
                                     CombatantState enemy, List<String> livingUserIds, long seed) {
        List<String> candidates = livingUserIds.stream().distinct().sorted().toList();
        List<String> targets = switch (definition.targetScope()) {
            case SELF -> List.of();
            case ALL -> candidates;
            case SINGLE -> candidates.isEmpty() ? List.of() : List.of(candidates.get(new Random(seed).nextInt(candidates.size())));
        };
        int baseAttackDamage = Math.max(0, attack) + Math.min(CombatRules.MAX_ENEMY_STRENGTH, Math.max(0, enemy.strength()));
        int block = definition.actionType() == EnemyActionType.DEFEND || definition.actionType() == EnemyActionType.ATTACK_DEFEND
                ? Math.max(0, defense) : 0;
        int strength = Math.min(definition.strengthGain(), Math.max(0, CombatRules.MAX_ENEMY_STRENGTH - enemy.strength()));
        return refreshForecast(new LockedEnemyAction(definition.actionType(), targets, definition.hits(), 0, block, strength,
                definition.statusEffects(), baseAttackDamage, definition.damagePercent()), enemy);
    }

    /** Numeric updates caused by cards are not another action/target draw. */
    public LockedEnemyAction refreshForecast(LockedEnemyAction action, CombatantState enemy) {
        // Legacy packets only have an already-computed number; do not apply WEAK twice.
        if (action.hits() > 0 && action.damagePercent() == 0) return action;
        int damage = action.baseAttackDamage();
        if (enemy.buffs().getOrDefault(BuffType.WEAK, 0) > 0) damage = damage * 3 / 4;
        damage = damage * action.damagePercent() / 100;
        return new LockedEnemyAction(action.actionType(), action.targetUserIds(), action.hits(), damage,
                action.block(), action.strengthGain(), action.statusEffects(), action.baseAttackDamage(), action.damagePercent());
    }

    public record DamageForecast(int damagePerHit, int hits, int blockAbsorbed, int hpLoss) {}

    /** Current recipient-specific numbers; shares the exact damage primitive with execution. */
    public Map<String, DamageForecast> forecastDamage(CombatantState enemy, Map<String, CombatantState> players, LockedEnemyAction locked) {
        LockedEnemyAction action = refreshForecast(locked, enemy);
        Map<String, DamageForecast> result = new LinkedHashMap<>();
        for (String id : action.targetUserIds()) {
            CombatantState player = players.get(id);
            if (player == null || !player.alive()) continue;
            int hp = player.hp();
            int block = player.block();
            int perHit = CombatRules.damage(Integer.MAX_VALUE, 0, player.buffs(), action.damagePerHit(), false).hpLost();
            for (int i = 0; i < action.hits() && hp > 0; i++) {
                CombatRules.Damage damage = CombatRules.damage(hp, block, player.buffs(), action.damagePerHit(), false);
                hp = damage.hp();
                block = damage.block();
            }
            result.put(id, new DamageForecast(perHit, action.hits(), player.block() - block, player.hp() - hp));
        }
        return Map.copyOf(result);
    }

    public CombatDelta resolve(CombatantState enemy, Map<String, CombatantState> players, LockedEnemyAction action) {
        action = refreshForecast(action, enemy);
        Map<String, CombatDelta.Change> changes = new LinkedHashMap<>();
        players.forEach((id, p) -> changes.put(id, new CombatDelta.Change(p, p)));
        List<CombatDelta.Hit> hits = new ArrayList<>();
        // Old enemy block expires only now, after the entire preceding player phase.
        CombatRules.Damage poison = CombatRules.damage(enemy.hp(), 0, enemy.buffs(), enemy.buffs().getOrDefault(BuffType.POISON, 0), true);
        CombatantState afterEnemy = new CombatantState(poison.hp(), 0, enemy.strength(), enemy.buffs());
        if (!afterEnemy.alive()) {
            return new CombatDelta(new CombatDelta.Change(enemy, afterEnemy), changes, hits, poison.hpLost(), CombatDelta.Outcome.VICTORY);
        }

        // Seven actions use the same locked numeric effect packet. No intent-dependent alternate engine.
        for (String id : action.targetUserIds()) {
            CombatantState target = players.get(id);
            if (target == null || !target.alive()) continue;
            int hp = target.hp();
            int block = target.block();
            for (int hit = 1; hit <= action.hits() && hp > 0; hit++) {
                CombatRules.Damage dealt = CombatRules.damage(hp, block, target.buffs(), action.damagePerHit(), false);
                hits.add(new CombatDelta.Hit(id, hit, action.damagePerHit(), dealt.hpLost(), block - dealt.block()));
                hp = dealt.hp();
                block = dealt.block();
            }
            changes.put(id, new CombatDelta.Change(target, new CombatantState(hp, block, target.strength(), target.buffs())));
        }

        afterEnemy = new CombatantState(afterEnemy.hp(), action.block(),
                Math.min(CombatRules.MAX_ENEMY_STRENGTH, afterEnemy.strength() + action.strengthGain()), CombatRules.tick(enemy.buffs(), true));
        for (Map.Entry<String, CombatDelta.Change> entry : changes.entrySet()) {
            CombatantState before = entry.getValue().before();
            CombatantState after = entry.getValue().after();
            if (!after.alive()) continue;
            // Expire statuses from the completed player phase, THEN apply new enemy statuses.
            Map<BuffType, Integer> buffs = new HashMap<>(CombatRules.tick(after.buffs(), false));
            if (action.targetUserIds().contains(entry.getKey())) {
                action.statusEffects().forEach((type, value) -> buffs.merge(type, value, (a, b) -> Math.min(99, a + b)));
            }
            // Player poison is true damage at the next player phase boundary, before healing/draw.
            CombatRules.Damage playerPoison = CombatRules.damage(after.hp(), after.block(), buffs, buffs.getOrDefault(BuffType.POISON, 0), true);
            buffs.computeIfPresent(BuffType.POISON, (type, value) -> value > 1 ? value - 1 : null);
            entry.setValue(new CombatDelta.Change(before, new CombatantState(playerPoison.hp(), after.block(), after.strength(), buffs)));
        }
        CombatDelta.Outcome outcome = changes.values().stream().noneMatch(c -> c.after().alive())
                ? CombatDelta.Outcome.DEFEAT : CombatDelta.Outcome.CONTINUE;
        return new CombatDelta(new CombatDelta.Change(enemy, afterEnemy), changes, hits, poison.hpLost(), outcome);
    }
}
