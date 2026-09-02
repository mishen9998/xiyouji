package com.xiyouji.service.session;

import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import java.util.*;

/**
 * 战斗状态 - 封装单场战斗的所有数据
 */
public class BattleState {

    private String enemyId;
    private long stateVersion;
    private Enemy enemy;
    private int turnNumber;
    private boolean playerTurn;
    private boolean battleOver;
    private boolean victory;
    private boolean rewardsHandled;
    private List<Card> cardRewards;  // 战斗胜利后的卡牌奖励列表（供 chooseCardReward 使用）
    private List<String> combatLog;
    private int cardsPlayedThisTurn;
    private boolean skillUsedThisTurn;
    private int totalDamageDealt;
    private int totalBlockGained;

    public BattleState() {
        this.turnNumber = 1;
        this.playerTurn = true;
        this.battleOver = false;
        this.victory = false;
        this.combatLog = new ArrayList<>();
        this.cardsPlayedThisTurn = 0;
        this.skillUsedThisTurn = false;
        this.totalDamageDealt = 0;
        this.totalBlockGained = 0;
    }

    public BattleState(Enemy enemy) {
        this();
        this.enemy = enemy.copy();
        this.enemyId = String.valueOf(enemy.getId());
    }

    /** 战斗开始 */
    public void startBattle() {
        combatLog.add("⚔️ " + enemy.getName() + " 出现了！");
        enemy.chooseIntent();
    }

    /** 执行敌人回合 */
    public void executeEnemyTurn(GameCharacter player) {
        combatLog.add("--- 敌人回合 #" + turnNumber + " ---");

        // 中毒伤害
        if (enemy.getBuffs().containsKey(BuffType.POISON)) {
            int poison = enemy.getBuffs().get(BuffType.POISON);
            enemy.setHp(Math.max(0, enemy.getHp() - poison));
            combatLog.add("☠️ 中毒造成 " + poison + " 点伤害");
        }

        if (enemy.isDead()) {
            battleOver = true;
            victory = true;
            combatLog.add("🏆 敌人被毒死了！");
            return;
        }

        switch (enemy.getIntent()) {
            case ATTACK -> {
                int dmg = enemy.getIntentValue();
                int actualDmg = player.takeDamage(dmg);
                combatLog.add(enemy.getEmoji() == null ? "" : enemy.getEmoji() + " " +
                        enemy.getName() + " 造成 " + actualDmg + " 点伤害");
            }
            case DEFEND -> {
                int block = enemy.getIntentValue();
                enemy.gainBlock(block);
                combatLog.add(enemy.getName() + " 获得 " + block + " 点格挡");
            }
            case BUFF -> {
                enemy.addBuff(BuffType.STRENGTH, enemy.getIntentValue());
                combatLog.add(enemy.getName() + " 获得 " + enemy.getIntentValue() + " 点力量");
            }
            default -> combatLog.add(enemy.getName() + " 在观察你…");
        }

        enemy.resetBlock();
        enemy.tickBuffs();

        if (player.isDead()) {
            battleOver = true;
            victory = false;
            combatLog.add("💀 你被打败了！");
            return;
        }

        enemy.chooseIntent();
        playerTurn = true;
        turnNumber++;
        cardsPlayedThisTurn = 0;
        skillUsedThisTurn = false;
        player.startTurn();
        player.tickBuffs();

        // 遗物效果：龙鳞甲
        if (player.getRelics().stream().anyMatch(r -> "龙鳞甲".equals(r.getName()))) {
            player.drawCards(1);
        }

        // 遗物效果：风火轮
        if (player.getRelics().stream().anyMatch(r -> "风火轮".equals(r.getName()))) {
            player.setMaxEnergy(player.getMaxEnergy() + 1);
        }

        // 遗物效果：锦襕袈裟（唐三藏专属）— 每回合开始回复2点生命
        if (player.getRelics().stream().anyMatch(r -> "锦襕袈裟".equals(r.getName()))) {
            player.heal(2);
            combatLog.add("🧘 锦襕袈裟回复2点生命值");
        }

        player.drawCards(5);
    }

    /** 玩家打出卡牌 — 传入额外伤害/格挡加成，不修改卡牌自身 */
    public boolean playPlayerCard(GameCharacter player, int handIndex, int extraDmg, int extraBlk) {
        if (!playerTurn || battleOver) return false;
        List<Card> hand = player.getHand();
        if (handIndex < 0 || handIndex >= hand.size()) return false;

        Card card = hand.get(handIndex);
        if (player.getEnergy() < card.getCost()) return false;

        boolean success = player.playCard(card, enemy, extraDmg, extraBlk);
        if (success) {
            combatLog.add("🃏 使用: " + (card.getEmoji() != null ? card.getEmoji() + " " : "") +
                    card.getName());

            if (card.getType() == CardType.SKILL) skillUsedThisTurn = true;
            int effectiveDmg = card.getDamage() + extraDmg + player.getStrength();
            if (effectiveDmg > 0) totalDamageDealt += effectiveDmg;
            if (card.getBlock() > 0) totalBlockGained += card.getBlock();
            cardsPlayedThisTurn++;

            // 遗物效果：九齿钉耙
            if (card.getType() == CardType.ATTACK &&
                    player.getRelics().stream().anyMatch(r -> "九齿钉耙".equals(r.getName()))) {
                player.heal(1);
            }

            if (enemy.isDead()) {
                battleOver = true;
                victory = true;
                combatLog.add("🏆 " + enemy.getName() + " 被击败了！");
            }
        }
        return success;
    }

    /** 玩家结束回合 */
    public void endPlayerTurn(GameCharacter player) {
        if (!playerTurn || battleOver) return;
        playerTurn = false;
        player.endTurn();
        executeEnemyTurn(player);
    }

    /** 创建敌人的独立副本 */
    public Enemy copy() { return enemy; }

    // ===== Getters/Setters =====
    public String getEnemyId() { return enemyId; }
    public void setEnemyId(String enemyId) { this.enemyId = enemyId; }
    public long getStateVersion() { return stateVersion; }
    public void setStateVersion(long stateVersion) { this.stateVersion = stateVersion; }
    public Enemy getEnemy() { return enemy; }
    public void setEnemy(Enemy enemy) { this.enemy = enemy; }
    public int getTurnNumber() { return turnNumber; }
    public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }
    public boolean isPlayerTurn() { return playerTurn; }
    public void setPlayerTurn(boolean playerTurn) { this.playerTurn = playerTurn; }
    public boolean isBattleOver() { return battleOver; }
    public void setBattleOver(boolean battleOver) { this.battleOver = battleOver; }
    public boolean isVictory() { return victory; }
    public void setVictory(boolean victory) { this.victory = victory; }
    public boolean isRewardsHandled() { return rewardsHandled; }
    public void setRewardsHandled(boolean rewardsHandled) { this.rewardsHandled = rewardsHandled; }
    public List<Card> getCardRewards() { return cardRewards; }
    public void setCardRewards(List<Card> cardRewards) { this.cardRewards = cardRewards; }
    public List<String> getCombatLog() { return combatLog; }
    public void setCombatLog(List<String> combatLog) { this.combatLog = combatLog; }
    public int getCardsPlayedThisTurn() { return cardsPlayedThisTurn; }
    public void setCardsPlayedThisTurn(int cardsPlayedThisTurn) { this.cardsPlayedThisTurn = cardsPlayedThisTurn; }
    public boolean isSkillUsedThisTurn() { return skillUsedThisTurn; }
    public void setSkillUsedThisTurn(boolean skillUsedThisTurn) { this.skillUsedThisTurn = skillUsedThisTurn; }
    public int getTotalDamageDealt() { return totalDamageDealt; }
    public void setTotalDamageDealt(int totalDamageDealt) { this.totalDamageDealt = totalDamageDealt; }
    public int getTotalBlockGained() { return totalBlockGained; }
    public void setTotalBlockGained(int totalBlockGained) { this.totalBlockGained = totalBlockGained; }
}
