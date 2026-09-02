package com.xiyouji.repository;

import com.xiyouji.model.Card;
import com.xiyouji.model.enums.CardType;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.model.enums.Rarity;
import com.xiyouji.port.CardRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data adapter for the application card catalog port. */
public interface CardJpaRepository extends JpaRepository<Card, Long>, CardRepositoryPort {
    List<Card> findByCharacterClass(CharacterClass characterClass);
    List<Card> findByCharacterClassOrCharacterClassIsNull(CharacterClass characterClass);
    List<Card> findByType(CardType type);
    List<Card> findByRarity(Rarity rarity);
    List<Card> findByName(String name);
}
