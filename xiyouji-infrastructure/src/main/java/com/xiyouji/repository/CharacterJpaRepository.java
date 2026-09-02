package com.xiyouji.repository;

import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.port.CharacterRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data adapter for the application character catalog port. */
public interface CharacterJpaRepository extends JpaRepository<GameCharacter, Long>, CharacterRepositoryPort {
    Optional<GameCharacter> findByCharacterClass(CharacterClass characterClass);
}
