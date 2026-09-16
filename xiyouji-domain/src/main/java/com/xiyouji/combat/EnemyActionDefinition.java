package com.xiyouji.combat;

import com.xiyouji.model.enums.BuffType;
import java.util.Map;
import java.util.Objects;

/** Versioned content can persist these values without depending on a framework.
 * Damage keeps the existing calculateAttackDamage ordering: floor WEAK first,
 * then floor the action's percentage of (attack + strength).
 * DEFEND and ATTACK_DEFEND always use the encounter's Enemy.defense.
 */
public record EnemyActionDefinition(EnemyActionType actionType, TargetScope targetScope,
                                    int damagePercent, int hits, int strengthGain,
                                    Map<BuffType, Integer> statusEffects) {
    public enum TargetScope { SINGLE, ALL, SELF }

    public EnemyActionDefinition {
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(targetScope, "targetScope");
        statusEffects = Map.copyOf(statusEffects == null ? Map.of() : statusEffects);
        if (damagePercent < 0 || hits < 0 || strengthGain < 0
                || statusEffects.entrySet().stream().anyMatch(e -> e.getValue() <= 0
                || (e.getKey() != BuffType.WEAK && e.getKey() != BuffType.VULNERABLE && e.getKey() != BuffType.POISON))) {
            throw new IllegalArgumentException("Invalid enemy action values: " + actionType);
        }
        boolean attack = switch (actionType) {
            case ATTACK, ATTACK_ALL, MULTI_HIT, ATTACK_DEFEND -> true;
            default -> false;
        };
        if ((attack && (hits < 1 || damagePercent < 1)) || (!attack && (hits != 0 || damagePercent != 0))
                || (actionType == EnemyActionType.MULTI_HIT && hits < 2)
                || (attack && actionType != EnemyActionType.MULTI_HIT && hits != 1)
                || (actionType == EnemyActionType.ATTACK_ALL && targetScope != TargetScope.ALL)
                || ((actionType == EnemyActionType.DEFEND || actionType == EnemyActionType.GAIN_STRENGTH) && targetScope != TargetScope.SELF)
                || ((attack || actionType == EnemyActionType.APPLY_STATUS) && targetScope == TargetScope.SELF)
                || (actionType != EnemyActionType.GAIN_STRENGTH && strengthGain != 0)
                || (actionType == EnemyActionType.GAIN_STRENGTH && strengthGain < 1)
                || (actionType == EnemyActionType.APPLY_STATUS && statusEffects.isEmpty())
                || (actionType != EnemyActionType.APPLY_STATUS && !statusEffects.isEmpty())) {
            throw new IllegalArgumentException("Inconsistent enemy action: " + actionType);
        }
    }

    public static EnemyActionDefinition attack(int percent) {
        return new EnemyActionDefinition(EnemyActionType.ATTACK, TargetScope.SINGLE, percent, 1, 0, Map.of());
    }
    public static EnemyActionDefinition allAttack(int percent) {
        return new EnemyActionDefinition(EnemyActionType.ATTACK_ALL, TargetScope.ALL, percent, 1, 0, Map.of());
    }
    public static EnemyActionDefinition multiHit(int percent, int hits) {
        return new EnemyActionDefinition(EnemyActionType.MULTI_HIT, TargetScope.SINGLE, percent, hits, 0, Map.of());
    }
    public static EnemyActionDefinition defend() {
        return new EnemyActionDefinition(EnemyActionType.DEFEND, TargetScope.SELF, 0, 0, 0, Map.of());
    }
    public static EnemyActionDefinition attackDefend(int percent) {
        return new EnemyActionDefinition(EnemyActionType.ATTACK_DEFEND, TargetScope.SINGLE, percent, 1, 0, Map.of());
    }
    public static EnemyActionDefinition strength(int amount) {
        return new EnemyActionDefinition(EnemyActionType.GAIN_STRENGTH, TargetScope.SELF, 0, 0, amount, Map.of());
    }
    public static EnemyActionDefinition status(TargetScope scope, BuffType type, int stacks) {
        return new EnemyActionDefinition(EnemyActionType.APPLY_STATUS, scope, 0, 0, 0, Map.of(type, stacks));
    }

    /** Existing unversioned catalog vocabulary; a missing pattern is handled by its catalog. */
    public static EnemyActionDefinition legacyMove(String move) {
        return switch (move) {
            case "attack" -> attack(100);
            case "defend" -> defend();
            case "attack_defend" -> attackDefend(50);
            case "buff" -> strength(3);
            default -> throw new IllegalArgumentException("Unknown enemy move: " + move);
        };
    }
}
