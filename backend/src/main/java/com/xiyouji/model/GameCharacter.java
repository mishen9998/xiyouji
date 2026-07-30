package com.xiyouji.model;

import com.xiyouji.model.enums.*;
import jakarta.persistence.*;
import java.util.*;

/**
 * 角色/玩家实体
 */
@Entity
@Table(name = "characters")
public class GameCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private CharacterClass characterClass;

    @Column(nullable = false)
    private int maxHp;

    @Column(nullable = false)
    private int startingGold;

    @Column(length = 500)
    private String startingDeck; // 逗号分隔的卡牌ID

    @Column(length = 200)
    private String startingRelic; // 初始遗物

    @Column(length = 500)
    private String description;

    @Column(length = 100)
    private String emoji;

    // 运行时（不持久化）
    @Transient
    private int hp;
    @Transient
    private int maxEnergy = 3;
    @Transient
    private int energy;
    @Transient
    private int gold;
    @Transient
    private int block;
    @Transient
    private int strength;
    @Transient
    private int dexterity;
    @Transient
    private int drawNextTurn;       // 下回合额外抽牌数（由卡牌效果产生）
    @Transient
    private int energyNextTurn;     // 下回合额外能量（由卡牌效果产生）
    @Transient
    private List<Card> deck = new ArrayList<>();
    @Transient
    private List<Card> hand = new ArrayList<>();
    @Transient
    private List<Card> discardPile = new ArrayList<>();
    @Transient
    private List<Card> exhaustPile = new ArrayList<>();
    @Transient
    private List<Card> drawPile = new ArrayList<>();
    @Transient
    private List<Relic> relics = new ArrayList<>();
    @Transient
    private Map<BuffType, Integer> buffs = new HashMap<>();
    @Transient
    private int floor;

    public GameCharacter() {}

    /** 初始化战斗状态 — HP继承上一场战斗的剩余值，不重置为满血 */
    public void initBattle() {
        // 不重置 maxHp 和 hp — HP 在战斗之间继承
        this.energy = maxEnergy;
        this.block = 0;
        // 力量/敏捷为战斗内属性，每场战斗重置（不跨战斗叠加）
        this.strength = 0;
        this.dexterity = 0;
        this.drawNextTurn = 0;
        this.energyNextTurn = 0;
        this.hand.clear();
        this.discardPile.clear();
        this.exhaustPile.clear();
        this.buffs.clear();
        // 洗牌
        this.drawPile = new ArrayList<>(deck);
        Collections.shuffle(drawPile);
    }

    /** 回合开始 */
    public void startTurn() {
        this.energy = maxEnergy + energyNextTurn;
        this.energyNextTurn = 0;
        this.block = 0;
        // 应用上回合累积的下回合抽牌加成
        if (drawNextTurn > 0) {
            drawCards(drawNextTurn);
            drawNextTurn = 0;
        }
    }

    /** 抽牌 — 抽牌堆不够时自动将弃牌堆洗入抽牌堆，补足数量 */
    public List<Card> drawCards(int count) {
        List<Card> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // 抽牌堆空了 → 把弃牌堆洗进来
            if (drawPile.isEmpty()) {
                if (!discardPile.isEmpty()) {
                    drawPile.addAll(discardPile);
                    discardPile.clear();
                    Collections.shuffle(drawPile);
                } else {
                    // 两堆都空，无牌可抽
                    break;
                }
            }
            Card c = drawPile.remove(0);
            if (hand.size() < 10) {
                hand.add(c);
                drawn.add(c);
            } else {
                // 手牌满了，抽到的卡放入弃牌堆
                discardPile.add(c);
            }
        }
        return drawn;
    }

    /** 使用卡牌（基础版） */
    public boolean playCard(Card card, Enemy target) {
        return playCard(card, target, 0, 0);
    }

    /** 使用卡牌 — 带额外伤害/格挡加成，不修改卡牌自身数值 */
    public boolean playCard(Card card, Enemy target, int extraDamage, int extraBlock) {
        // 使用引用相等移除指定卡牌实例（不能用 List.remove(card)，
        // 因为 Card.equals 基于 id，而运行时副本 id 均为 null，会误删首张相同卡牌）
        int idx = -1;
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i) == card) { idx = i; break; }
        }
        if (idx < 0) return false;
        if (energy < card.getCost()) return false;
        energy -= card.getCost();
        hand.remove(idx);

        // 先施加力量/敏捷加成，使当回合打出的 buff 即时生效
        strength += card.getStrengthBonus();
        dexterity += card.getDexterityBonus();

        // ★ 先施加Debuff，再计算伤害 — 这样同回合后续攻击牌能吃到易伤收益
        // 易伤/虚弱/中毒在伤害计算前施加到目标身上
        if (card.getVulnerableTurns() > 0) target.addBuff(BuffType.VULNERABLE, card.getVulnerableTurns());
        if (card.getWeakTurns() > 0) target.addBuff(BuffType.WEAK, card.getWeakTurns());
        if (card.getPoisonAmount() > 0) target.addBuff(BuffType.POISON, card.getPoisonAmount());

        // 伤害计算 = (卡牌伤害+额外) + 力量，虚弱时×0.75
        int baseDmg = card.getDamage() + extraDamage;
        int totalDamage = baseDmg + (baseDmg > 0 ? strength : 0);
        if (totalDamage > 0 && buffs.containsKey(BuffType.WEAK) && buffs.get(BuffType.WEAK) > 0) {
            totalDamage = (int)(totalDamage * 0.75);
        }
        target.takeDamage(totalDamage);

        // 格挡 = 卡牌格挡+额外，gainBlock含敏捷加成
        int baseBlk = card.getBlock() + extraBlock;
        gainBlock(baseBlk);

        if (card.getDrawCards() > 0) drawCards(card.getDrawCards());

        // 治疗
        if (card.getHealAmount() > 0) heal(card.getHealAmount());

        // 下回合生效的加成（累加，可由多张卡牌叠加）
        if (card.getDrawNextTurn() > 0) drawNextTurn += card.getDrawNextTurn();
        if (card.getEnergyNextTurn() > 0) energyNextTurn += card.getEnergyNextTurn();

        if (card.isExhaust()) {
            exhaustPile.add(card);
        } else {
            discardPile.add(card);
        }
        return true;
    }

    public void addBlock(int amount) {
        this.block += amount;
    }

    public void gainBlock(int amount) {
        this.block += (amount + dexterity);
    }

    public int takeDamage(int damage) {
        if (damage <= 0) return 0;
        int actualDamage = damage;
        if (buffs.containsKey(BuffType.VULNERABLE) && buffs.get(BuffType.VULNERABLE) > 0) {
            actualDamage = (int)(actualDamage * 1.5);
        }
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

    public void heal(int amount) {
        this.hp = Math.min(maxHp, hp + amount);
    }

    /** 结束回合：手牌进弃牌堆 */
    public void endTurn() {
        discardPile.addAll(hand);
        hand.clear();
    }

    public void addCard(Card card) {
        deck.add(card);
    }

    public void removeCard(Card card) {
        deck.remove(card);
        hand.remove(card);
        drawPile.remove(card);
        discardPile.remove(card);
        exhaustPile.remove(card);
    }

    public boolean isDead() { return hp <= 0; }

    public void tickBuffs() {
        List<BuffType> toRemove = new ArrayList<>();
        for (Map.Entry<BuffType, Integer> entry : buffs.entrySet()) {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) toRemove.add(entry.getKey());
            else buffs.put(entry.getKey(), remaining);
        }
        toRemove.forEach(buffs::remove);
    }

    // ===== Getters/Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CharacterClass getCharacterClass() { return characterClass; }
    public void setCharacterClass(CharacterClass characterClass) { this.characterClass = characterClass; }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }
    public int getStartingGold() { return startingGold; }
    public void setStartingGold(int startingGold) { this.startingGold = startingGold; }
    public String getStartingDeck() { return startingDeck; }
    public void setStartingDeck(String startingDeck) { this.startingDeck = startingDeck; }
    public String getStartingRelic() { return startingRelic; }
    public void setStartingRelic(String startingRelic) { this.startingRelic = startingRelic; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public int getMaxEnergy() { return maxEnergy; }
    public void setMaxEnergy(int maxEnergy) { this.maxEnergy = maxEnergy; }
    public int getEnergy() { return energy; }
    public void setEnergy(int energy) { this.energy = energy; }
    public int getGold() { return gold; }
    public void setGold(int gold) { this.gold = gold; }
    public int getBlock() { return block; }
    public void setBlock(int block) { this.block = block; }
    public int getStrength() { return strength; }
    public int getDexterity() { return dexterity; }
    public void setDexterity(int dexterity) { this.dexterity = dexterity; }
    public void setStrength(int strength) { this.strength = strength; }
    public int getDrawNextTurn() { return drawNextTurn; }
    public void setDrawNextTurn(int drawNextTurn) { this.drawNextTurn = drawNextTurn; }
    public int getEnergyNextTurn() { return energyNextTurn; }
    public void setEnergyNextTurn(int energyNextTurn) { this.energyNextTurn = energyNextTurn; }
    public List<Card> getDeck() { return deck; }
    public void setDeck(List<Card> deck) { this.deck = deck; }
    public List<Card> getHand() { return hand; }
    public void setHand(List<Card> hand) { this.hand = hand; }
    public List<Card> getDiscardPile() { return discardPile; }
    public void setDiscardPile(List<Card> discardPile) { this.discardPile = discardPile; }
    public List<Card> getExhaustPile() { return exhaustPile; }
    public void setExhaustPile(List<Card> exhaustPile) { this.exhaustPile = exhaustPile; }
    public List<Card> getDrawPile() { return drawPile; }
    public void setDrawPile(List<Card> drawPile) { this.drawPile = drawPile; }
    public List<Relic> getRelics() { return relics; }
    public void setRelics(List<Relic> relics) { this.relics = relics; }
    public Map<BuffType, Integer> getBuffs() { return buffs; }
    public void setBuffs(Map<BuffType, Integer> buffs) { this.buffs = buffs; }
    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }
}
