package com.xiyouji.model.enums;

/**
 * Buff/Debuff类型
 */
public enum BuffType {
    STRENGTH("力量", true),       // +攻击力
    WEAK("虚弱", false),         // -25%攻击伤害
    VULNERABLE("易伤", false),   // +50%受到伤害
    BLOCK("格挡", true),         // 护盾
    DEXTERITY("敏捷", true),     // +防御
    POISON("中毒", false),       // 每回合扣血
    REGENERATION("回春", true),   // 每回合回血
    RAGE("愤怒", true),          // +力量（孙悟空专属）
    FORTIFY("金刚", true),       // +大量格挡（沙僧专属）
    GLUTTONY("贪食", true),      // +回血（猪八戒专属）
    SWIFT("腾云", true),         // +抽牌（白龙马专属）
    BURN("灼烧", false),         // 火焰伤害（敌方debuff）
    FROZEN("冰冻", false);       // 冰冻

    private final String displayName;
    private final boolean isBuff; // true=buff, false=debuff

    BuffType(String displayName, boolean isBuff) {
        this.displayName = displayName;
        this.isBuff = isBuff;
    }

    public String getDisplayName() { return displayName; }
    public boolean isBuff() { return isBuff; }
}
