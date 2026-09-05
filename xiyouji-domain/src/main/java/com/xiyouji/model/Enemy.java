package com.xiyouji.model;

import com.xiyouji.model.enums.*;
import jakarta.persistence.*;
import java.util.*;

/**
 * 敌人实体
 */
@Entity
@Table(name = "enemies")
public class Enemy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;            // 白骨精、牛魔王等

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private int maxHp;

    @Column(nullable = false)
    private int hp;                 // 当前血量

    private int attack;             // 基础攻击力
    private int defense;            // 基础防御
    private boolean isBoss;         // 是否Boss
    private int level;              // 难度等级 1-3

    @Column(length = 100)
    private String emoji;           // 图标

    // 运行时状态
    @Transient
    private int block;              // 当前格挡
    @Transient
    private int strength;           // 当前力量
    @Transient
    private EnemyIntent intent;     // 当前意图
    @Transient
    private int intentValue;        // 意图数值
    @Transient
    private List<String> movePattern; // 行动模式 [attack, attack_defend, attack]
    @Transient
    private int patternIndex;

    // Buff/Debuff回合计数
    @Transient
    private Map<BuffType, Integer> buffs = new HashMap<>();

    public Enemy() {
        this.block = 0;
        this.strength = 0;
        this.patternIndex = 0;
        this.intent = EnemyIntent.ATTACK;
        this.intentValue = 0;
    }

    public Enemy(String name, int maxHp, int attack, int defense, boolean isBoss, int level) {
        this();
        this.name = name;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.attack = attack;
        this.defense = defense;
        this.isBoss = isBoss;
        this.level = level;
    }

    /** 添加格挡 */
    public void addBlock(int amount) {
        this.block += amount;
    }

    /** 回合开始时重置格挡 */
    public void resetBlock() {
        this.block = 0;
    }

    /** 受到伤害（先扣格挡） */
    public int takeDamage(int damage) {
        if (damage <= 0) return 0;
        int actualDamage = damage;

        // 脆弱效果：受到伤害 +50%
        if (buffs.containsKey(BuffType.VULNERABLE) && buffs.get(BuffType.VULNERABLE) > 0) {
            actualDamage = (int)(actualDamage * 1.5);
        }

        // 先扣格挡
        if (block > 0) {
            if (block >= actualDamage) {
                block -= actualDamage;
                return 0;
            } else {
                actualDamage -= block;
                block = 0;
            }
        }

        hp = Math.max(0, hp - actualDamage);
        return actualDamage;
    }

    /** 获得护盾 */
    public void gainBlock(int amount) {
        int dex = buffs.containsKey(BuffType.DEXTERITY) ? buffs.get(BuffType.DEXTERITY) : 0;
        this.block += (amount + dex);
    }

    /** 计算攻击力（考虑力量和虚弱） */
    public int calculateAttackDamage() {
        int dmg = attack + strength;
        if (buffs.containsKey(BuffType.WEAK) && buffs.get(BuffType.WEAK) > 0) {
            dmg = (int)(dmg * 0.75);
        }
        return Math.max(0, dmg);
    }

    /** 添加Buff/Debuff */
    public void addBuff(BuffType type, int turns) {
        buffs.merge(type, turns, Integer::sum);
        buffs.put(type, Math.min(buffs.get(type), 99)); // 上限99层
    }

    /** 每回合减少Buff计时 */
    public void tickBuffs() {
        List<BuffType> toRemove = new ArrayList<>();
        for (Map.Entry<BuffType, Integer> entry : buffs.entrySet()) {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                toRemove.add(entry.getKey());
            } else {
                buffs.put(entry.getKey(), remaining);
            }
        }
        toRemove.forEach(buffs::remove);
    }

    /** 选择意图 */
    public void chooseIntent() {
        if (movePattern == null || movePattern.isEmpty()) {
            // 默认：随机攻击
            intent = EnemyIntent.ATTACK;
            intentValue = calculateAttackDamage();
            return;
        }
        String move = movePattern.get(patternIndex % movePattern.size());
        patternIndex++;
        switch (move) {
            case "attack" -> {
                intent = EnemyIntent.ATTACK;
                intentValue = calculateAttackDamage();
            }
            case "defend" -> {
                intent = EnemyIntent.DEFEND;
                intentValue = defense + 5;
            }
            case "attack_defend" -> {
                intent = EnemyIntent.ATTACK;
                intentValue = calculateAttackDamage() / 2;
                gainBlock(defense + 3);
            }
            case "buff" -> {
                intent = EnemyIntent.BUFF;
                intentValue = 3;
            }
            default -> {
                intent = EnemyIntent.ATTACK;
                intentValue = calculateAttackDamage();
            }
        }
    }

    public boolean isDead() { return hp <= 0; }

    /** 创建此敌人的副本（用于战斗） */
    public Enemy copy() {
        Enemy e = new Enemy(this.name, this.maxHp, this.attack, this.defense, this.isBoss, this.level);
        e.setHp(this.hp);
        e.setEmoji(this.emoji);
        e.setMovePattern(this.movePattern != null ? new ArrayList<>(this.movePattern) : new ArrayList<>());
        e.setBuffs(new HashMap<>(this.buffs));
        return e;
    }

    // ===== Getters/Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }
    public int getAttack() { return attack; }
    public void setAttack(int attack) { this.attack = attack; }
    public int getDefense() { return defense; }
    public void setDefense(int defense) { this.defense = defense; }
    public boolean isBoss() { return isBoss; }
    public void setBoss(boolean boss) { isBoss = boss; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public int getBlock() { return block; }
    public void setBlock(int block) { this.block = block; }
    public int getStrength() { return strength; }
    public void setStrength(int strength) { this.strength = strength; }
    public EnemyIntent getIntent() { return intent; }
    public void setIntent(EnemyIntent intent) { this.intent = intent; }
    public int getIntentValue() { return intentValue; }
    public void setIntentValue(int intentValue) { this.intentValue = intentValue; }
    public List<String> getMovePattern() { return movePattern; }
    public void setMovePattern(List<String> movePattern) { this.movePattern = movePattern; }
    public int getPatternIndex() { return patternIndex; }
    public void setPatternIndex(int patternIndex) { this.patternIndex = patternIndex; }
    public Map<BuffType, Integer> getBuffs() { return buffs; }
    public void setBuffs(Map<BuffType, Integer> buffs) { this.buffs = buffs; }
}
