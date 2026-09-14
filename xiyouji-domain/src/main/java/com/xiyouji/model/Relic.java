package com.xiyouji.model;

import com.xiyouji.model.enums.*;

/**
 * 遗物实体
 * JPA 映射契约见 META-INF/orm.xml（领域类不携带持久化注解）
 */
public class Relic {

    private Long id;

    private String name;

    private String description;

    private RelicTier tier;

    private CharacterClass characterClass; // null=通用遗物

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
