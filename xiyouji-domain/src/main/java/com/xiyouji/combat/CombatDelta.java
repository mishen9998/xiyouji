package com.xiyouji.combat;

import java.util.List;
import java.util.Map;

/** Complete, comparable result of one enemy phase and the following player-start poison tick. */
public record CombatDelta(Change enemy, Map<String, Change> players, List<Hit> hits,
                          int enemyPoisonDamage, Outcome outcome) {
    public enum Outcome { CONTINUE, VICTORY, DEFEAT }
    public record Change(CombatantState before, CombatantState after) {}
    public record Hit(String targetUserId, int hitNumber, int damage, int hpLost, int blockLost) {}
    public CombatDelta {
        players = Map.copyOf(players);
        hits = List.copyOf(hits);
    }
}
