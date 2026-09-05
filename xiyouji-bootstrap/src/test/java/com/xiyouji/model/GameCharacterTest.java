package com.xiyouji.model;

import com.xiyouji.model.enums.CardType;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.model.enums.Rarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GameCharacter 单元测试
 */
@DisplayName("GameCharacter 单元测试")
class GameCharacterTest {

    private GameCharacter createSampleCharacter() {
        GameCharacter gc = new GameCharacter();
        gc.setCharacterClass(CharacterClass.SUN_WUKONG);
        gc.setMaxHp(80);
        gc.setHp(80);
        gc.setMaxEnergy(3);
        gc.setEnergy(3);
        return gc;
    }

    private Card createCard(String name, int cost, int damage, int block) {
        Card card = new Card(name, "测试", CardType.ATTACK, Rarity.BASIC, null, cost);
        card.setDamage(damage);
        card.setBlock(block);
        return card;
    }

    @Test
    @DisplayName("initBattle 重置战斗状态但保留HP")
    void testInitBattle_resetsState() {
        GameCharacter gc = createSampleCharacter();
        gc.setStrength(5);
        gc.setDexterity(3);
        gc.setBlock(10);
        gc.setHp(50);
        gc.getHand().add(createCard("手牌中的卡", 1, 5, 0));
        gc.getDiscardPile().add(createCard("弃牌堆中的卡", 1, 5, 0));

        gc.initBattle();

        assertEquals(0, gc.getStrength(), "力量应重置为 0");
        assertEquals(0, gc.getDexterity(), "敏捷应重置为 0");
        assertEquals(0, gc.getBlock(), "格挡应重置为 0");
        assertTrue(gc.getHand().isEmpty(), "手牌应清空");
        assertTrue(gc.getDiscardPile().isEmpty(), "弃牌堆应清空");
        assertTrue(gc.getExhaustPile().isEmpty(), "消耗堆应清空");
        assertTrue(gc.getBuffs().isEmpty(), "Buff 应清空");
        assertEquals(50, gc.getHp(), "hp 应保留为上一场战斗剩余值");
        assertEquals(80, gc.getMaxHp(), "maxHp 不应改变");
        assertEquals(3, gc.getEnergy(), "能量应设为 maxEnergy");
        assertEquals(0, gc.getDrawNextTurn(), "drawNextTurn 应重置为 0");
        assertEquals(0, gc.getEnergyNextTurn(), "energyNextTurn 应重置为 0");
    }

    @Test
    @DisplayName("takeDamage 正确扣除血量")
    void testTakeDamage_basic() {
        GameCharacter gc = createSampleCharacter();
        int lost = gc.takeDamage(20);
        assertEquals(20, lost, "实际伤害应为 20");
        assertEquals(60, gc.getHp(), "hp 应为 80-20=60");
        assertEquals(0, gc.getBlock(), "block 应为 0");
    }

    @Test
    @DisplayName("takeDamage 格挡优先吸收伤害")
    void testTakeDamage_blockAbsorbs() {
        GameCharacter gc = createSampleCharacter();
        gc.setBlock(15);
        int lost = gc.takeDamage(20);
        assertEquals(5, lost, "格挡吸收15后实际伤害应为 5");
        assertEquals(75, gc.getHp(), "hp 应为 80-5=75");
        assertEquals(0, gc.getBlock(), "block 应被消耗为 0");
    }

    @Test
    @DisplayName("takeDamage 格挡完全吸收")
    void testTakeDamage_blockFullAbsorb() {
        GameCharacter gc = createSampleCharacter();
        gc.setBlock(30);
        int lost = gc.takeDamage(20);
        assertEquals(0, lost, "格挡完全吸收应无实际伤害");
        assertEquals(80, gc.getHp(), "hp 不应变化");
        assertEquals(10, gc.getBlock(), "block 应剩余 10");
    }

    @Test
    @DisplayName("heal 治疗不超过 maxHp")
    void testHeal_capped() {
        GameCharacter gc = createSampleCharacter();
        gc.setHp(70);
        gc.heal(20);
        assertEquals(80, gc.getHp(), "治疗不应超过 maxHp 80");
    }

    @Test
    @DisplayName("heal 正常治疗")
    void testHeal_normal() {
        GameCharacter gc = createSampleCharacter();
        gc.setHp(50);
        gc.heal(15);
        assertEquals(65, gc.getHp(), "hp 应为 50+15=65");
    }

    @Test
    @DisplayName("gainBlock 含敏捷加成")
    void testGainBlock_withDexterity() {
        GameCharacter gc = createSampleCharacter();
        gc.setDexterity(2);
        gc.gainBlock(5);
        assertEquals(7, gc.getBlock(), "格挡应为 5+2(敏捷)=7");
    }

    @Test
    @DisplayName("playCard 攻击牌造成伤害")
    void testPlayCard_attack() {
        GameCharacter gc = createSampleCharacter();
        Card card = createCard("攻击", 1, 6, 0);
        gc.getHand().add(card);
        Enemy enemy = new Enemy("小妖", 30, 10, 2, false, 1);

        boolean result = gc.playCard(card, enemy);

        assertTrue(result, "出牌应成功");
        assertEquals(2, gc.getEnergy(), "能量应扣除 1");
        assertTrue(gc.getHand().isEmpty(), "手牌应已出牌");
        assertEquals(24, enemy.getHp(), "敌人 hp 应为 30-6=24");
    }

    @Test
    @DisplayName("playCard 能量不足时失败")
    void testPlayCard_insufficientEnergy() {
        GameCharacter gc = createSampleCharacter();
        gc.setEnergy(0);
        Card card = createCard("攻击", 1, 6, 0);
        gc.getHand().add(card);
        Enemy enemy = new Enemy("小妖", 30, 10, 2, false, 1);

        boolean result = gc.playCard(card, enemy);

        assertFalse(result, "能量不足应出牌失败");
        assertEquals(0, gc.getEnergy(), "能量不应变化");
        assertEquals(1, gc.getHand().size(), "手牌不应减少");
    }

    @Test
    @DisplayName("playCard 力量加成即时生效")
    void testPlayCard_strengthBonusImmediate() {
        GameCharacter gc = createSampleCharacter();
        // 先放一张 +2 力量的卡
        Card buffCard = new Card("蓄力", "加力量", CardType.POWER, Rarity.UNCOMMON, null, 1);
        buffCard.setStrengthBonus(2);
        gc.getHand().add(buffCard);
        Enemy enemy = new Enemy("小妖", 100, 0, 2, false, 1);
        gc.playCard(buffCard, enemy);

        // 再放一张攻击牌，应享受 +2 力量
        Card attackCard = createCard("攻击", 1, 6, 0);
        gc.getHand().add(attackCard);
        gc.playCard(attackCard, enemy);

        assertEquals(92, enemy.getHp(), "敌人 hp 应为 100-6-2(力量)=92");
    }

    @Test
    @DisplayName("drawCards 抽牌堆耗尽时自动洗入弃牌堆")
    void testDrawCards_recycleDiscardPile() {
        GameCharacter gc = createSampleCharacter();
        for (int i = 0; i < 3; i++) {
            gc.addCard(createCard("卡" + i, 1, 5, 0));
        }
        gc.initBattle();

        // 抽完所有3张
        gc.drawCards(3);
        assertEquals(3, gc.getHand().size(), "应抽到3张牌");
        assertEquals(0, gc.getDrawPile().size(), "抽牌堆应空");

        // 结束回合，手牌进弃牌堆
        gc.endTurn();
        assertEquals(3, gc.getDiscardPile().size(), "弃牌堆应有3张");

        // 再抽2张，应从弃牌堆洗入
        gc.drawCards(2);
        assertEquals(2, gc.getHand().size(), "应抽到2张牌");
    }

    @Test
    @DisplayName("endTurn 手牌进入弃牌堆")
    void testEndTurn() {
        GameCharacter gc = createSampleCharacter();
        gc.getHand().add(createCard("卡A", 1, 5, 0));
        gc.getHand().add(createCard("卡B", 1, 5, 0));

        gc.endTurn();

        assertTrue(gc.getHand().isEmpty(), "手牌应清空");
        assertEquals(2, gc.getDiscardPile().size(), "弃牌堆应有2张");
    }

    @Test
    @DisplayName("isDead 血量为0时返回true")
    void testIsDead() {
        GameCharacter gc = createSampleCharacter();
        assertFalse(gc.isDead(), "满血不应死亡");
        gc.setHp(0);
        assertTrue(gc.isDead(), "血量为0应死亡");
    }

    @Test
    @DisplayName("takeDamage 脆弱状态增加50%伤害")
    void testTakeDamage_vulnerable() {
        GameCharacter gc = createSampleCharacter();
        gc.getBuffs().put(com.xiyouji.model.enums.BuffType.VULNERABLE, 2);
        int lost = gc.takeDamage(10);
        assertEquals(15, lost, "脆弱下10点伤害应为15");
        assertEquals(65, gc.getHp(), "hp 应为 80-15=65");
    }
}
