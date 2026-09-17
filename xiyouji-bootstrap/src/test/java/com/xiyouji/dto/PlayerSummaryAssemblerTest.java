package com.xiyouji.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyouji.model.Card;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.enums.CardType;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.model.enums.Rarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSummaryAssemblerTest {
    private final PlayerSummaryAssembler assembler = new PlayerSummaryAssembler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deckIncludesTheCurrentServerDescriptionWithoutChangingExistingFields() throws Exception {
        GameCharacter player = new GameCharacter();
        player.setCharacterClass(CharacterClass.SUN_WUKONG);
        Card card = new Card("挥棒", "造成6点伤害。", CardType.ATTACK, Rarity.COMMON, CharacterClass.SUN_WUKONG, 1);
        card.setDamage(6);
        player.getDeck().add(card);

        var json = mapper.readTree(mapper.writeValueAsString(assembler.toPlayerSummary(player)));
        var deckCard = json.path("deck").get(0);
        assertEquals("造成6点伤害。", deckCard.path("description").asText());
        assertEquals("挥棒", deckCard.path("name").asText());
        assertEquals("ATTACK", deckCard.path("type").asText());
        assertEquals(6, deckCard.path("damage").asInt());
        assertEquals(1, deckCard.path("cost").asInt());
        assertEquals(1, json.path("deckSize").asInt());
        assertFalse(deckCard.has("id"));

        // The actual saved description is forwarded, never replaced by front-end lore.
        card.setDescription("存档内的自定义效果说明。");
        assertEquals(card.getDescription(), assembler.toPlayerSummary(player).getDeck().get(0).getDescription());
    }

    @Test
    void legacyMissingDescriptionRemainsOptional() throws Exception {
        GameCharacter player = new GameCharacter();
        player.setCharacterClass(CharacterClass.SUN_WUKONG);
        player.getDeck().add(new Card("旧牌", null, CardType.SKILL, Rarity.COMMON, null, 0));
        var json = mapper.readTree(mapper.writeValueAsString(assembler.toPlayerSummary(player)));
        assertFalse(json.path("deck").get(0).has("description"));
    }
}
