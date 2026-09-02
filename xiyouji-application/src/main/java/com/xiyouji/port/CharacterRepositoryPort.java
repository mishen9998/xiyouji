package com.xiyouji.port;

import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.enums.CharacterClass;

import java.util.Optional;

/** Application-facing character catalog port. */
public interface CharacterRepositoryPort {
    Optional<GameCharacter> findByCharacterClass(CharacterClass characterClass);
    GameCharacter save(GameCharacter character);
}
