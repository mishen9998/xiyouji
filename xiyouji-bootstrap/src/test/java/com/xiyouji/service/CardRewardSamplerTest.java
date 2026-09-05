package com.xiyouji.service;

import com.xiyouji.model.Card;
import com.xiyouji.model.enums.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CardRewardSamplerTest {
    private Card card(String name, Rarity rarity, CharacterClass role) {
        return new Card(name, "", CardType.ATTACK, rarity, role, 1);
    }
    @Test void weightedBoundaries() {
        List<Card> pool = List.of(card("c", Rarity.COMMON, null), card("u", Rarity.UNCOMMON, null),
                card("r", Rarity.RARE, null), card("l", Rarity.LEGENDARY, null));
        int[] rolls = {0, 49, 50, 79, 80, 89, 90, 99};
        Rarity[] expected = {Rarity.COMMON, Rarity.COMMON, Rarity.UNCOMMON, Rarity.UNCOMMON,
                Rarity.RARE, Rarity.RARE, Rarity.LEGENDARY, Rarity.LEGENDARY};
        for (int i = 0; i < rolls.length; i++) {
            final int roll = rolls[i];
            Random random = new Random() { public int nextInt(int bound) { return bound == 100 ? roll : 0; } };
            assertEquals(expected[i], CardRewardSampler.draw(pool, CharacterClass.SUN_WUKONG, 1, random).get(0).getRarity());
        }
    }
    @Test void excludesOtherRolesBasicAndCurseAndRefillsOnlyAfterExhaustion() {
        List<Card> pool = List.of(card("a", Rarity.COMMON, null), card("b", Rarity.LEGENDARY, CharacterClass.SUN_WUKONG),
                card("other", Rarity.RARE, CharacterClass.ZHU_BAJIE), card("base", Rarity.BASIC, null), card("curse", Rarity.CURSE, null));
        List<Card> result = CardRewardSampler.draw(pool, CharacterClass.SUN_WUKONG, 5, new Random(7));
        assertEquals(5, result.size());
        assertEquals(2, result.subList(0, 2).stream().map(Card::getName).distinct().count());
        assertTrue(result.stream().allMatch(c -> Set.of("a", "b").contains(c.getName())));
        assertNotSame(result.get(0), pool.get(0));
        assertEquals(5, pool.size());
    }
    @Test void exhaustedRarityRenormalizesAndUniquePoolDoesNotRepeat() {
        List<Card> pool = new ArrayList<>();
        pool.add(card("rare", Rarity.RARE, null));
        for (int i = 0; i < 5; i++) pool.add(card("common" + i, Rarity.COMMON, null));
        Random last = new Random() { public int nextInt(int bound) { return bound - 1; } };
        List<Card> result = CardRewardSampler.draw(pool, CharacterClass.SUN_WUKONG, 5, last);
        assertEquals(Rarity.RARE, result.get(0).getRarity());
        assertEquals(5, result.stream().map(Card::getName).distinct().count());
        assertTrue(result.subList(1, 5).stream().allMatch(c -> c.getRarity() == Rarity.COMMON));
    }
    @Test void emptyPoolTerminates() {
        assertTrue(CardRewardSampler.draw(List.of(), CharacterClass.SUN_WUKONG, 5, new Random()).isEmpty());
    }
}
