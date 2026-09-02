package com.xiyouji.model.enums;

/**
 * 角色职业
 */
public enum CharacterClass {
    SUN_WUKONG("孙悟空", "齐天大圣，擅长攻击与变化", "#FF4500"),
    ZHU_BAJIE("猪八戒", "天蓬元帅，攻守兼备生命力强", "#FFD700"),
    SHA_SENG("沙僧", "卷帘大将，精于防御与守护", "#4682B4"),
    BAI_LONGMA("白龙马", "西海龙太子，速度与支援见长", "#32CD32"),
    TANG_SANZANG("唐三藏", "金蝉子转世，精通佛法与治愈", "#DDA0DD");

    private final String displayName;
    private final String description;
    private final String color;

    CharacterClass(String displayName, String description, String color) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getColor() { return color; }
}
