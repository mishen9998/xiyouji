package com.xiyouji.model.enums;

/**
 * 卡牌稀有度
 */
public enum Rarity {
    BASIC("基础", "common"),
    COMMON("普通", "gray"),
    UNCOMMON("罕见", "blue"),
    RARE("稀有", "gold"),
    LEGENDARY("传说", "orange"),
    CURSE("诅咒", "red");

    private final String displayName;
    private final String color;

    Rarity(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
}
