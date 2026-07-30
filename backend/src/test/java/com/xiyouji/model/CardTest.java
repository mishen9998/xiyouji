package com.xiyouji.model;

import com.xiyouji.model.enums.CardType;
import com.xiyouji.model.enums.Rarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Card 模型纯单元测试（不需要 Spring 上下文）
 */
@DisplayName("Card 模型单元测试")
class CardTest {

    /** 创建一个用于测试的样本卡牌：挥棒，cost=1，damage=10，block=5 */
    private Card createSampleCard() {
        Card card = new Card("挥棒", "造成6点伤害", CardType.ATTACK, Rarity.BASIC, null, 1);
        card.setDamage(10);
        card.setBlock(5);
        return card;
    }

    @Test
    @DisplayName("copy() 创建独立的深度副本，修改副本不影响原对象")
    void testCopy_createsDeepCopy() {
        Card original = createSampleCard();
        original.setDamage(10);
        original.setBlock(5);

        Card copy = original.copy();

        // 修改副本
        copy.setDamage(99);
        copy.setBlock(88);

        // 原对象不受影响
        assertEquals(10, original.getDamage(), "原对象的 damage 不应被修改");
        assertEquals(5, original.getBlock(), "原对象的 block 不应被修改");
        // 副本已修改
        assertEquals(99, copy.getDamage(), "副本的 damage 应已修改");
        assertEquals(88, copy.getBlock(), "副本的 block 应已修改");
        // 副本的基本属性应与原对象一致
        assertEquals(original.getName(), copy.getName(), "副本的 name 应与原对象一致");
        assertEquals(original.getType(), copy.getType(), "副本的 type 应与原对象一致");
        assertEquals(original.getCost(), copy.getCost(), "副本的 cost 应与原对象一致");
    }

    @Test
    @DisplayName("upgrade() 后 damage 增加 3")
    void testUpgrade_increasesDamage() {
        Card card = createSampleCard();
        card.setDamage(10);
        assertFalse(card.isUpgraded(), "升级前 upgraded 应为 false");

        card.upgrade();

        assertEquals(13, card.getDamage(), "升级后 damage 应增加 3（10 -> 13）");
        assertTrue(card.isUpgraded(), "升级后 upgraded 应为 true");
    }

    @Test
    @DisplayName("upgrade() 后 block 增加 3")
    void testUpgrade_increasesBlock() {
        Card card = createSampleCard();
        card.setBlock(5);
        assertFalse(card.isUpgraded());

        card.upgrade();

        assertEquals(8, card.getBlock(), "升级后 block 应增加 3（5 -> 8）");
        assertTrue(card.isUpgraded(), "升级后 upgraded 应为 true");
    }

    @Test
    @DisplayName("upgrade() 只能执行一次")
    void testUpgrade_onlyOnce() {
        Card card = createSampleCard();
        card.setDamage(10);
        card.setBlock(5);

        card.upgrade();
        int damageAfterFirstUpgrade = card.getDamage();
        int blockAfterFirstUpgrade = card.getBlock();
        assertTrue(card.isUpgraded(), "第一次升级后 upgraded 应为 true");

        // 第二次升级应无效
        card.upgrade();

        assertEquals(damageAfterFirstUpgrade, card.getDamage(), "第二次升级不应再增加 damage");
        assertEquals(blockAfterFirstUpgrade, card.getBlock(), "第二次升级不应再增加 block");
        assertTrue(card.isUpgraded(), "upgraded 仍应为 true");
    }

    @Test
    @DisplayName("template() 重置 upgraded 标志为 false")
    void testTemplate_resetsUpgradedFlag() {
        Card card = createSampleCard();
        card.setDamage(10);
        card.upgrade();
        assertTrue(card.isUpgraded(), "升级后 upgraded 应为 true");
        assertEquals(13, card.getDamage(), "升级后 damage 应为 13");

        Card template = card.template();

        assertFalse(template.isUpgraded(), "template() 应重置 upgraded 为 false");
        assertEquals(13, template.getDamage(), "template 应保留升级后的数值（damage=13）");
        assertEquals(card.getName(), template.getName(), "template 的 name 应一致");
        assertEquals(card.getType(), template.getType(), "template 的 type 应一致");
    }
}
