package com.xiyouji.port;

import com.xiyouji.model.Card;
import com.xiyouji.model.enums.CardType;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.model.enums.Rarity;

import java.util.List;
import java.util.Optional;

/** Application-facing catalog port; persistence details stay in infrastructure. */
public interface CardRepositoryPort {
    long count();
    Optional<Card> findById(Long id);
    List<Card> findAll();
    List<Card> findByCharacterClass(CharacterClass characterClass);
    List<Card> findByCharacterClassOrCharacterClassIsNull(CharacterClass characterClass);
    List<Card> findByType(CardType type);
    List<Card> findByRarity(Rarity rarity);
    List<Card> findByName(String name);
    Card save(Card card);
}
