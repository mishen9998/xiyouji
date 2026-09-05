package com.xiyouji.service;

import com.xiyouji.model.Card;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.model.enums.Rarity;
import java.util.*;
import java.util.random.RandomGenerator;

/** Shared weighted reward selection. Each pass exhausts unique cards before allowing repeats. */
public final class CardRewardSampler {
    private CardRewardSampler() {}
    private static final Map<Rarity, Integer> WEIGHTS = Map.of(
            Rarity.COMMON, 50, Rarity.UNCOMMON, 30, Rarity.RARE, 10, Rarity.LEGENDARY, 10);

    public static List<Card> draw(List<Card> cards, CharacterClass role, int count, RandomGenerator random) {
        Map<String, Card> unique = new LinkedHashMap<>();
        for (Card card : cards) {
            if (WEIGHTS.containsKey(card.getRarity())
                    && (card.getCharacterClass() == null || card.getCharacterClass() == role)) {
                unique.putIfAbsent(card.getName(), card);
            }
        }
        List<Card> pool = new ArrayList<>(unique.values());
        List<Card> remaining = new ArrayList<>(pool);
        List<Card> result = new ArrayList<>();
        while (result.size() < count && !pool.isEmpty()) {
            if (remaining.isEmpty()) remaining.addAll(pool);
            Map<Rarity, List<Card>> groups = new EnumMap<>(Rarity.class);
            for (Card card : remaining) groups.computeIfAbsent(card.getRarity(), k -> new ArrayList<>()).add(card);
            int total = groups.keySet().stream().mapToInt(WEIGHTS::get).sum();
            int roll = random.nextInt(total);
            for (var entry : groups.entrySet()) {
                roll -= WEIGHTS.get(entry.getKey());
                if (roll < 0) {
                    List<Card> candidates = entry.getValue();
                    Card chosen = candidates.get(random.nextInt(candidates.size()));
                    result.add(chosen.copy());
                    remaining.removeIf(card -> card == chosen);
                    break;
                }
            }
        }
        return result;
    }
}
