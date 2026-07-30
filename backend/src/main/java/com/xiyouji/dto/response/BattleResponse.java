package com.xiyouji.dto.response;

import java.util.List;

/**
 * 战斗状态响应DTO
 */
public class BattleResponse {

    private boolean inBattle;
    private int turnNumber;
    private boolean playerTurn;
    private boolean battleOver;
    private boolean victory;
    private Object player;
    private Object enemy;
    private List<String> combatLog;
    private Object rewards;

    public BattleResponse() {
    }

    public boolean isInBattle() {
        return inBattle;
    }

    public void setInBattle(boolean inBattle) {
        this.inBattle = inBattle;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    public boolean isPlayerTurn() {
        return playerTurn;
    }

    public void setPlayerTurn(boolean playerTurn) {
        this.playerTurn = playerTurn;
    }

    public boolean isBattleOver() {
        return battleOver;
    }

    public void setBattleOver(boolean battleOver) {
        this.battleOver = battleOver;
    }

    public boolean isVictory() {
        return victory;
    }

    public void setVictory(boolean victory) {
        this.victory = victory;
    }

    public Object getPlayer() {
        return player;
    }

    public void setPlayer(Object player) {
        this.player = player;
    }

    public Object getEnemy() {
        return enemy;
    }

    public void setEnemy(Object enemy) {
        this.enemy = enemy;
    }

    public List<String> getCombatLog() {
        return combatLog;
    }

    public void setCombatLog(List<String> combatLog) {
        this.combatLog = combatLog;
    }

    public Object getRewards() {
        return rewards;
    }

    public void setRewards(Object rewards) {
        this.rewards = rewards;
    }
}
