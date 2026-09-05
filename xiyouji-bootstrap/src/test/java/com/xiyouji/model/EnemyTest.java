package com.xiyouji.model;

import com.xiyouji.model.enums.BuffType;
import com.xiyouji.model.enums.EnemyIntent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enemy 模型纯单元测试（不需要 Spring 上下文）
 */
@DisplayName("Enemy 模型单元测试")
class EnemyTest {

    /** 创建一个用于测试的样本敌人：白骨精，maxHp=100，attack=20，defense=5 */
    private Enemy createSampleEnemy() {
        return new Enemy("白骨精", 100, 20, 5, false, 1);
    }

    @Test
    @DisplayName("takeDamage 正确扣血")
    void testTakeDamage_reducesHp() {
        Enemy enemy = createSampleEnemy(); // hp=100, block=0

        int actualDamage = enemy.takeDamage(30);

        assertEquals(70, enemy.getHp(), "扣除 30 伤害后 hp 应为 70");
        assertEquals(30, actualDamage, "实际造成的伤害应为 30");
        assertFalse(enemy.isDead(), "敌人不应死亡");
    }

    @Test
    @DisplayName("takeDamage 格挡优先吸收伤害")
    void testTakeDamage_blockedByBlock() {
        Enemy enemy = createSampleEnemy(); // hp=100
        enemy.setBlock(20);

        int actualDamage = enemy.takeDamage(30);

        // 格挡吸收 20，穿透 10
        assertEquals(90, enemy.getHp(), "格挡吸收 20 后，hp 应扣 10（100 -> 90）");
        assertEquals(0, enemy.getBlock(), "格挡应被完全消耗");
        assertEquals(10, actualDamage, "实际穿透伤害应为 10");
    }

    @Test
    @DisplayName("takeDamage 脆弱增加 50% 伤害")
    void testTakeDamage_amplifiedByVulnerable() {
        Enemy enemy = createSampleEnemy(); // hp=100
        assertEquals("脆弱", BuffType.VULNERABLE.getDisplayName(), "9.5 规则的玩家展示名称应为脆弱");
        enemy.addBuff(BuffType.VULNERABLE, 2);

        // 基础伤害 20，脆弱 +50% = 30
        int actualDamage = enemy.takeDamage(20);

        assertEquals(70, enemy.getHp(), "脆弱下 20 伤害变为 30，hp 应为 70（100 - 30）");
        assertEquals(30, actualDamage, "脆弱下实际伤害应为 30（20 * 1.5）");
    }

    @Test
    @DisplayName("calculateAttackDamage 虚弱减少 25% 攻击")
    void testCalculateAttackDamage_reducedByWeak() {
        Enemy enemy = createSampleEnemy(); // attack=20, strength=0
        enemy.addBuff(BuffType.WEAK, 2);

        int damage = enemy.calculateAttackDamage();

        // 20 * 0.75 = 15
        assertEquals(15, damage, "虚弱下攻击力应为 20 * 0.75 = 15");
    }

    @Test
    @DisplayName("addBuff 支持 Buff 叠加")
    void testAddBuff_stacks() {
        Enemy enemy = createSampleEnemy();

        enemy.addBuff(BuffType.WEAK, 2);
        enemy.addBuff(BuffType.WEAK, 3);

        assertEquals(5, enemy.getBuffs().get(BuffType.WEAK), "两次添加虚弱（2+3）应叠加为 5 层");
    }

    @Test
    @DisplayName("tickBuffs 每回合递减并移除过期 Buff")
    void testTickBuffs_decrementsAndRemoves() {
        Enemy enemy = createSampleEnemy();
        enemy.addBuff(BuffType.WEAK, 2);

        // 第一次 tick：2 -> 1
        enemy.tickBuffs();
        assertEquals(1, enemy.getBuffs().get(BuffType.WEAK), "第一次 tick 后虚弱应剩余 1 层");

        // 第二次 tick：1 -> 0，移除
        enemy.tickBuffs();
        assertFalse(enemy.getBuffs().containsKey(BuffType.WEAK), "第二次 tick 后虚弱应被移除");
    }

    @Test
    @DisplayName("chooseIntent 按 movePattern 循环执行")
    void testChooseIntent_cycles() {
        Enemy enemy = createSampleEnemy(); // attack=20
        enemy.setMovePattern(List.of("attack", "defend", "buff"));

        // 第一次：attack
        enemy.chooseIntent();
        assertEquals(EnemyIntent.ATTACK, enemy.getIntent(), "第 1 次意图应为 ATTACK");
        assertEquals(20, enemy.getIntentValue(), "attack 意图值应等于 calculateAttackDamage()=20");

        // 第二次：defend
        enemy.chooseIntent();
        assertEquals(EnemyIntent.DEFEND, enemy.getIntent(), "第 2 次意图应为 DEFEND");
        assertEquals(10, enemy.getIntentValue(), "defend 意图值应等于 defense+5=10");

        // 第三次：buff
        enemy.chooseIntent();
        assertEquals(EnemyIntent.BUFF, enemy.getIntent(), "第 3 次意图应为 BUFF");
        assertEquals(3, enemy.getIntentValue(), "buff 意图值应为 3");

        // 第四次：循环回 attack
        enemy.chooseIntent();
        assertEquals(EnemyIntent.ATTACK, enemy.getIntent(), "第 4 次应循环回 ATTACK");
    }
}
