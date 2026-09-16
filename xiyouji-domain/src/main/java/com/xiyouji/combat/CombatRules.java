package com.xiyouji.combat;

import com.xiyouji.model.enums.BuffType;
import java.util.HashMap;
import java.util.Map;

/** The shared damage and duration rules used by cards, enemies and status effects. */
public final class CombatRules {
    public static final String VERSION = "combat-v2";
    public static final String LEGACY_VERSION = "legacy-v1";
    public static final int MAX_ENEMY_STRENGTH = 6;
    private CombatRules() {}

    public record Damage(int hp, int block, int hpLost) {}

    public static Damage damage(int hp, int block, Map<BuffType, Integer> buffs, int amount, boolean trueDamage) {
        int incoming = Math.max(0, amount);
        if (!trueDamage && buffs.getOrDefault(BuffType.VULNERABLE, 0) > 0) incoming = incoming * 3 / 2;
        int absorbed = trueDamage ? 0 : Math.min(Math.max(0, block), incoming);
        int remainingHp = Math.max(0, hp - (incoming - absorbed));
        return new Damage(remainingHp, block - absorbed, hp - remainingHp);
    }

    /** Timed effects expire after their owner's action; permanent stats never tick. */
    public static Map<BuffType, Integer> tick(Map<BuffType, Integer> buffs, boolean includePoison) {
        Map<BuffType, Integer> next = new HashMap<>();
        buffs.forEach((type, value) -> {
            int remaining = type == BuffType.STRENGTH || type == BuffType.DEXTERITY
                    || (type == BuffType.POISON && !includePoison) ? value : value - 1;
            if (remaining > 0) next.put(type, remaining);
        });
        return next;
    }
}
