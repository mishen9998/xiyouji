package com.xiyouji.repository;

import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.enums.CharacterClass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CharacterRepository extends JpaRepository<GameCharacter, Long> {
    Optional<GameCharacter> findByCharacterClass(CharacterClass characterClass);
}
