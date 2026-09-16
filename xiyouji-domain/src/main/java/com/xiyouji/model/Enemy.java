package com.xiyouji.model;

import com.xiyouji.model.enums.*;
import com.xiyouji.combat.*;
import java.util.*;

/**
 * 敌人实体
 * JPA 映射契约见 META-INF/orm.xml（领域类不携带持久化注解；
 * 未列入 orm.xml 的运行时字段不持久化）
 */
public class Enemy {

    private Long id;

    private String name;            // 白骨精、牛魔王等

    private String description;

    private int maxHp;

    private int hp;                 // 当前血量

    private int attack;             // 基础攻击力
    private int defense;            // 基础防御
    private boolean isBoss;         // 是否Boss
    private int level;              // 难度等级 1-3

    private String emoji;           // 图标

    // 运行时状态
    private int block;              // 当前格挡
    private int strength;           // 当前力量
    private EnemyIntent intent;     // 当前意图
    private int intentValue;        // 意图数值
    private List<String> movePattern; // 行动模式 [attack, attack_defend, attack]
    private int patternIndex;
    private List<EnemyActionDefinition> actionDefinitions;
    private LockedEnemyAction lockedAction;
    private String rulesVersion;
    private String contentKey;
    private int contentVersion;
    private String encounterId;

    // Buff/Debuff回合计数
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
        CombatRules.Damage result = CombatRules.damage(hp, block, buffs, damage, false);
        hp = result.hp();
        block = result.block();
        return result.hpLost();
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
        if (type == BuffType.STRENGTH) {
            setStrength(strength + Math.max(0, turns));
            return;
        }
        buffs.merge(type, turns, Integer::sum);
        buffs.put(type, Math.min(buffs.get(type), 99)); // 上限99层
    }

    /** 每回合减少Buff计时 */
    public void tickBuffs() {
        buffs = CombatRules.tick(buffs, true);
    }

    /** 选择意图 */
    public void chooseIntent() {
        EnemyCombat.lockNextAction(this, List.of(EnemyCombat.SOLO_PLAYER), 0);
    }

    public boolean isDead() { return hp <= 0; }

    /** 创建此敌人的副本（用于战斗） */
    public Enemy copy() {
        Enemy e = new Enemy(this.name, this.maxHp, this.attack, this.defense, this.isBoss, this.level);
        e.setId(id);
        e.setDescription(description);
        e.setHp(this.hp);
        e.setEmoji(this.emoji);
        e.setMovePattern(this.movePattern != null ? new ArrayList<>(this.movePattern) : new ArrayList<>());
        e.setBuffs(new HashMap<>(this.buffs));
        e.setActionDefinitions(actionDefinitions == null ? null : new ArrayList<>(actionDefinitions));
        e.setRulesVersion(rulesVersion);
        e.setContentKey(contentKey);
        e.setContentVersion(contentVersion);
        e.setEncounterId(encounterId);
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
    public void setStrength(int strength) { this.strength = Math.max(0, Math.min(CombatRules.MAX_ENEMY_STRENGTH, strength)); }
    public EnemyIntent getIntent() { return intent; }
    public void setIntent(EnemyIntent intent) { this.intent = intent; }
    public int getIntentValue() { return lockedAction == null ? intentValue : EnemyCombat.currentForecast(this).legacyIntentValue(); }
    public void setIntentValue(int intentValue) { this.intentValue = intentValue; }
    public List<String> getMovePattern() { return movePattern; }
    public void setMovePattern(List<String> movePattern) { this.movePattern = movePattern; }
    public int getPatternIndex() { return patternIndex; }
    public void setPatternIndex(int patternIndex) { this.patternIndex = patternIndex; }
    public List<EnemyActionDefinition> getActionDefinitions() { return actionDefinitions; }
    public void setActionDefinitions(List<EnemyActionDefinition> value) { this.actionDefinitions = value; }
    public LockedEnemyAction getLockedAction() { return lockedAction; }
    public void setLockedAction(LockedEnemyAction value) { this.lockedAction = value; }
    public String getRulesVersion() { return rulesVersion; }
    public void setRulesVersion(String value) { this.rulesVersion = value; }
    public String getContentKey() { return contentKey; }
    public void setContentKey(String value) { contentKey = value; }
    public int getContentVersion() { return contentVersion; }
    public void setContentVersion(int value) { contentVersion = value; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String value) { encounterId = value; }
    /** Optional snapshot alias; patternIndex remains the legacy wire-compatible cursor. */
    public int getActionIndex() { return patternIndex; }
    public void setActionIndex(int value) { patternIndex = value; }
    public Map<BuffType, Integer> getBuffs() { return buffs; }
    public void setBuffs(Map<BuffType, Integer> buffs) { this.buffs = buffs; }
}
