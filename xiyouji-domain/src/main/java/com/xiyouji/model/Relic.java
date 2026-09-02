package com.xiyouji.model;

import com.xiyouji.model.enums.*;
import jakarta.persistence.*;

/**
 * 遗物实体
 */
@Entity
@Table(name = "relics")
public class Relic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelicTier tier;

    @Enumerated(EnumType.STRING)
    private CharacterClass characterClass; // null=通用遗物

    @Column(length = 100)
    private String emoji;

    private String effect;          // JSON描述的效果

    public Relic() {}

    public Relic(String name, String description, RelicTier tier, String emoji) {
        this.name = name;
        this.description = description;
        this.tier = tier;
        this.emoji = emoji;
    }

    // ===== Getters/Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public RelicTier getTier() { return tier; }
    public void setTier(RelicTier tier) { this.tier = tier; }
    public CharacterClass getCharacterClass() { return characterClass; }
    public void setCharacterClass(CharacterClass characterClass) { this.characterClass = characterClass; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }
}
