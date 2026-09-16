package com.xiyouji.event;

import java.util.List;

/** Versioned content: descriptions never determine game effects. */
public record EventDefinition(String id, String title, String text, List<Option> options) {
    public enum Reward { GOLD, HEAL, HEAL_TO, UPGRADE, CARD, RELIC, REMOVE_BASIC, REMOVE_NON_BASIC, NEXT_BATTLE_BLOCK }
    public record Option(String id, String label, int hpPercent, int hpCost, int goldCost,
                         Reward reward, int amount, int goldReward) { }
}
