package com.xiyouji.repository;

import com.xiyouji.model.Card;
import com.xiyouji.model.enums.CardType;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.model.enums.Rarity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {
    List<Card> findByCharacterClass(CharacterClass characterClass);
    List<Card> findByCharacterClassOrCharacterClassIsNull(CharacterClass characterClass);
    List<Card> findByType(CardType type);
    List<Card> findByRarity(Rarity rarity);
    List<Card> findByName(String name);
}
