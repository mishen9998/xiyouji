package com.xiyouji.combat;

import com.xiyouji.model.enums.BuffType;
import java.util.Map;

/** Value-only snapshot. No entity, framework, RNG or storage dependency. */
public record CombatantState(int hp, int block, int strength, Map<BuffType, Integer> buffs) {
    public CombatantState {
        buffs = Map.copyOf(buffs == null ? Map.of() : buffs);
    }
    public boolean alive() { return hp > 0; }
}
