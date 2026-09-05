package com.xiyouji.service.room;

import com.xiyouji.model.Card;
import com.xiyouji.model.Enemy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多人战斗状态
 *
 * 5名玩家（唐僧师徒）共享同一个敌人，进行PvE协作战斗。
 *
 * 回合机制（参考杀戮尖塔2）：
 *   - 玩家回合内，所有存活玩家可自由出牌，谁先点击谁先出（抢出牌/FIFO抢占）
 *   - 玩家可以主动等待队友先施加脆弱/虚弱等 debuff 后再出牌，打出更高伤害
 *   - 每个玩家各自结束回合；当所有存活玩家都结束后，敌人执行其回合
 *   - 敌人回合结束后，所有存活玩家开始新回合（回蓝、抽牌、tick buff）
 *
 * 手牌隔离：每个玩家有独立的手牌、抽牌堆、弃牌堆，互不可见（但可通过WebSocket广播实现队友可见）。
 * 敌人共享：所有玩家攻击同一个敌人，敌人debuff全局生效。
 */
public class MultiplayerBattleState implements Serializable {

    private String roomCode;
    private Enemy enemy;
    private List<MultiplayerPlayer> players = new ArrayList<>();
    private int turnNumber = 1;
    private boolean playerTurn = true;
    private boolean battleOver = false;
    private boolean victory = false;
    /** 敌人本次意图攻击的目标玩家索引 */
    private int targetPlayerIndex = 0;
    private List<String> combatLog = new ArrayList<>();
    private boolean rewardsHandled = false;

    /** 战斗胜利后的卡牌奖励：key=userId, value=3张可选卡牌 */
    private Map<String, List<Card>> rewards = new HashMap<>();
    /** 玩家已领取的奖励卡牌名：key=userId, value=选择的卡牌名 */
    private Map<String, String> claimedRewards = new HashMap<>();
    /** 是否处于领奖阶段 */
    private boolean rewardsPhase = false;

    /** Redis 中战斗状态的单调版本号。 */
    private long stateVersion = 0L;

    public MultiplayerBattleState() {}

    public MultiplayerBattleState(String roomCode) {
        this.roomCode = roomCode;
    }

    /** 添加战斗日志（保留最近30条） */
    public void addLog(String entry) {
        combatLog.add(entry);
        while (combatLog.size() > 30) {
            combatLog.remove(0);
        }
    }

    /** 查找玩家在列表中的索引 */
    public int indexOfPlayer(String userId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getUserId().equals(userId)) {
                return i;
            }
        }
        return -1;
    }

    /** 查找玩家 */
    public MultiplayerPlayer findPlayer(String userId) {
        int idx = indexOfPlayer(userId);
        return idx >= 0 ? players.get(idx) : null;
    }

    /** 获取存活玩家数量 */
    public long alivePlayerCount() {
        return players.stream().filter(MultiplayerPlayer::isAlive).count();
    }

    /** 是否所有存活玩家都已结束回合 */
    public boolean allAlivePlayersEndedTurn() {
        return players.stream()
                .filter(MultiplayerPlayer::isAlive)
                .allMatch(MultiplayerPlayer::isEndedTurn);
    }

    /** 随机选择一个存活玩家作为敌人攻击目标 */
    public int randomAlivePlayerIndex(java.util.Random random) {
        List<Integer> aliveIndices = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).isAlive()) {
                aliveIndices.add(i);
            }
        }
        if (aliveIndices.isEmpty()) return 0;
        return aliveIndices.get(random.nextInt(aliveIndices.size()));
    }

    // ===== Getters/Setters =====

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public Enemy getEnemy() { return enemy; }
    public void setEnemy(Enemy enemy) { this.enemy = enemy; }

    public List<MultiplayerPlayer> getPlayers() { return players; }
    public void setPlayers(List<MultiplayerPlayer> players) { this.players = players; }

    public int getTurnNumber() { return turnNumber; }
    public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }

    public boolean isPlayerTurn() { return playerTurn; }
    public void setPlayerTurn(boolean playerTurn) { this.playerTurn = playerTurn; }

    public boolean isBattleOver() { return battleOver; }
    public void setBattleOver(boolean battleOver) { this.battleOver = battleOver; }

    public boolean isVictory() { return victory; }
    public void setVictory(boolean victory) { this.victory = victory; }

    public int getTargetPlayerIndex() { return targetPlayerIndex; }
    public void setTargetPlayerIndex(int targetPlayerIndex) { this.targetPlayerIndex = targetPlayerIndex; }

    public List<String> getCombatLog() { return combatLog; }
    public void setCombatLog(List<String> combatLog) { this.combatLog = combatLog; }

    public boolean isRewardsHandled() { return rewardsHandled; }
    public void setRewardsHandled(boolean rewardsHandled) { this.rewardsHandled = rewardsHandled; }

    public Map<String, List<Card>> getRewards() { return rewards; }
    public void setRewards(Map<String, List<Card>> rewards) { this.rewards = rewards; }

    public Map<String, String> getClaimedRewards() { return claimedRewards; }
    public void setClaimedRewards(Map<String, String> claimedRewards) { this.claimedRewards = claimedRewards; }

    public boolean isRewardsPhase() { return rewardsPhase; }
    public void setRewardsPhase(boolean rewardsPhase) { this.rewardsPhase = rewardsPhase; }

    public long getStateVersion() { return stateVersion; }
    public void setStateVersion(long stateVersion) { this.stateVersion = stateVersion; }
}
