package com.xiyouji.model;

import com.xiyouji.model.enums.*;
import jakarta.persistence.*;
import java.util.Objects;

/**
 * 卡牌实体
 */
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;            // 卡牌名称：如意金箍棒

    @Column(length = 500)
    private String description;     // 效果描述

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardType type;          // 攻击/技能/防御/能力/状态

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rarity rarity;          // 稀有度

    @Enumerated(EnumType.STRING)
    private CharacterClass characterClass; // null=通用卡牌

    private int cost;               // 能量消耗
    private int damage;             // 伤害值
    private int block;              // 格挡值
    private int drawCards;          // 抽牌数
    private int healAmount;         // 回血
    private int strengthBonus;      // 力量加成
    private int dexterityBonus;     // 敏捷加成
    private int vulnerableTurns;    // 施加易伤回合
    private int weakTurns;          // 施加虚弱回合
    private int poisonAmount;       // 中毒层数
    private int drawNextTurn;       // 下回合额外抽牌数
    private int energyNextTurn;     // 下回合额外能量
    private boolean exhaust;        // 是否消耗
    private boolean upgradeable;    // 是否可升级
    private String upgradeName;     // 升级后卡牌名（引用另一张卡）

    @Column(length = 100)
    private String flavorText;      // 卡牌风味文字

    @Column(length = 100)
    private String emoji;           // 图标表情

    // 升级字段（持久化到数据库）
    private boolean upgraded;
    private int damageUpgrade;
    private int blockUpgrade;
    private int costReduction;

    public Card() {}

    public Card(String name, String description, CardType type, Rarity rarity,
                CharacterClass characterClass, int cost) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.rarity = rarity;
        this.characterClass = characterClass;
        this.cost = cost;
    }

    /** 升级此卡牌 — 属性对应上升3点 */
    public void upgrade() {
        if (upgraded) return;
        this.upgraded = true;
        // 主属性各+3
        if (this.damage > 0) this.damage += 3;
        if (this.block > 0) this.block += 3;
        // 辅助效果也提升
        if (this.healAmount > 0) this.healAmount += 3;
        if (this.strengthBonus > 0) this.strengthBonus += 1;
        if (this.dexterityBonus > 0) this.dexterityBonus += 1;
        if (this.vulnerableTurns > 0) this.vulnerableTurns += 1;
        if (this.weakTurns > 0) this.weakTurns += 1;
        if (this.poisonAmount > 0) this.poisonAmount += 2;
        if (costReduction > 0) this.cost = Math.max(0, this.cost - costReduction);
    }

    /** 创建此卡牌的深度副本（用于牌堆——完全独立，不影响原卡牌模板） */
    public Card copy() {
        Card c = new Card(name, description, type, rarity, characterClass, cost);
        c.damage = this.damage;
        c.block = this.block;
        c.drawCards = this.drawCards;
        c.healAmount = this.healAmount;
        c.strengthBonus = this.strengthBonus;
        c.dexterityBonus = this.dexterityBonus;
        c.vulnerableTurns = this.vulnerableTurns;
        c.weakTurns = this.weakTurns;
        c.poisonAmount = this.poisonAmount;
        c.drawNextTurn = this.drawNextTurn;
        c.energyNextTurn = this.energyNextTurn;
        c.exhaust = this.exhaust;
        c.upgradeable = this.upgradeable;
        c.flavorText = this.flavorText;
        c.emoji = this.emoji;
        c.damageUpgrade = this.damageUpgrade;
        c.blockUpgrade = this.blockUpgrade;
        c.costReduction = this.costReduction;
        c.upgraded = this.upgraded;
        return c;
    }

    /** 创建此卡牌的模板拷贝（保留原始数据，用于重新初始化） */
    public Card template() {
        Card c = copy();
        c.upgraded = false;
        return c;
    }

    // ========== Getters / Setters ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public CardType getType() { return type; }
    public void setType(CardType type) { this.type = type; }
    public Rarity getRarity() { return rarity; }
    public void setRarity(Rarity rarity) { this.rarity = rarity; }
    public CharacterClass getCharacterClass() { return characterClass; }
    public void setCharacterClass(CharacterClass characterClass) { this.characterClass = characterClass; }
    public int getCost() { return cost; }
    public void setCost(int cost) { this.cost = cost; }
    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = damage; }
    public int getBlock() { return block; }
    public void setBlock(int block) { this.block = block; }
    public int getDrawCards() { return drawCards; }
    public void setDrawCards(int drawCards) { this.drawCards = drawCards; }
    public int getHealAmount() { return healAmount; }
    public void setHealAmount(int healAmount) { this.healAmount = healAmount; }
    public int getStrengthBonus() { return strengthBonus; }
    public void setStrengthBonus(int strengthBonus) { this.strengthBonus = strengthBonus; }
    public int getDexterityBonus() { return dexterityBonus; }
    public void setDexterityBonus(int dexterityBonus) { this.dexterityBonus = dexterityBonus; }
    public int getVulnerableTurns() { return vulnerableTurns; }
    public void setVulnerableTurns(int vulnerableTurns) { this.vulnerableTurns = vulnerableTurns; }
    public int getWeakTurns() { return weakTurns; }
    public void setWeakTurns(int weakTurns) { this.weakTurns = weakTurns; }
    public int getPoisonAmount() { return poisonAmount; }
    public void setPoisonAmount(int poisonAmount) { this.poisonAmount = poisonAmount; }
    public int getDrawNextTurn() { return drawNextTurn; }
    public void setDrawNextTurn(int drawNextTurn) { this.drawNextTurn = drawNextTurn; }
    public int getEnergyNextTurn() { return energyNextTurn; }
    public void setEnergyNextTurn(int energyNextTurn) { this.energyNextTurn = energyNextTurn; }
    public boolean isExhaust() { return exhaust; }
    public void setExhaust(boolean exhaust) { this.exhaust = exhaust; }
    public boolean isUpgradeable() { return upgradeable; }
    public void setUpgradeable(boolean upgradeable) { this.upgradeable = upgradeable; }
    public String getUpgradeName() { return upgradeName; }
    public void setUpgradeName(String upgradeName) { this.upgradeName = upgradeName; }
    public String getFlavorText() { return flavorText; }
    public void setFlavorText(String flavorText) { this.flavorText = flavorText; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public boolean isUpgraded() { return upgraded; }
    public void setUpgraded(boolean upgraded) { this.upgraded = upgraded; }
    public int getDamageUpgrade() { return damageUpgrade; }
    public void setDamageUpgrade(int damageUpgrade) { this.damageUpgrade = damageUpgrade; }
    public int getBlockUpgrade() { return blockUpgrade; }
    public void setBlockUpgrade(int blockUpgrade) { this.blockUpgrade = blockUpgrade; }
    public int getCostReduction() { return costReduction; }
    public void setCostReduction(int costReduction) { this.costReduction = costReduction; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card card)) return false;
        return Objects.equals(id, card.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
