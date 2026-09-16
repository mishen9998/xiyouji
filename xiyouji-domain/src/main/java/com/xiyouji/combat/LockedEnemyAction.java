package com.xiyouji.combat;

import com.xiyouji.model.enums.BuffType;
import com.xiyouji.model.enums.EnemyIntent;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable action choice, persisted with the battle before any player can act.
 * damagePerHit is attack damage before the recipient's vulnerability and block.
 * baseAttackDamage and damagePercent never change during the player phase;
 * damagePerHit is refreshed deterministically for current WEAK, without selecting another move/target.
 * Dead/missing locked targets are skipped at execution; no replacement is drawn.
 */
public record LockedEnemyAction(EnemyActionType actionType, List<String> targetUserIds,
                                int hits, int damagePerHit, int block, int strengthGain,
                                Map<BuffType, Integer> statusEffects, int baseAttackDamage, int damagePercent) {
    public LockedEnemyAction {
        Objects.requireNonNull(actionType, "actionType");
        targetUserIds = List.copyOf(targetUserIds);
        statusEffects = Map.copyOf(statusEffects);
        if (hits < 0 || damagePerHit < 0 || block < 0 || strengthGain < 0 || baseAttackDamage < 0 || damagePercent < 0) {
            throw new IllegalArgumentException("Negative locked action value");
        }
    }

    /** Adapter for the legacy already-announced numeric intent. */
    public LockedEnemyAction(EnemyActionType type, List<String> targets, int hits, int damage,
                             int block, int strength, Map<BuffType, Integer> effects) {
        this(type, targets, hits, damage, block, strength, effects, 0, 0);
    }

    public EnemyIntent legacyIntent() {
        return switch (actionType) {
            case DEFEND -> EnemyIntent.DEFEND;
            case GAIN_STRENGTH -> EnemyIntent.BUFF;
            case APPLY_STATUS -> EnemyIntent.DEBUFF;
            default -> EnemyIntent.ATTACK;
        };
    }

    public int legacyIntentValue() {
        return switch (actionType) {
            case DEFEND -> block;
            case GAIN_STRENGTH -> strengthGain;
            case APPLY_STATUS -> statusEffects.values().stream().mapToInt(Integer::intValue).sum();
            default -> damagePerHit;
        };
    }
}
