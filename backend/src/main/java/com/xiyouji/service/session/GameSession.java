package com.xiyouji.service.session;

import com.xiyouji.model.*;

/**
 * 游戏会话 - 封装玩家的完整游戏状态
 */
public class GameSession {

    private String sessionId;
    private GameCharacter player;
    private java.util.List<MapNode> map;
    private MapNode currentNode;
    private BattleState battle;
    private boolean mapOpen = true;
    private String lastEvent;
    private int currentLayer = 1;    // 当前层数 (1-3)
    private int maxLayer = 3;        // 总层数
    private int bonfireUpgradesLeft = 0; // 篝火剩余升级次数

    public GameSession() {}

    public GameSession(String sessionId, GameCharacter player, java.util.List<MapNode> map) {
        this.sessionId = sessionId;
        this.player = player;
        this.map = map;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public GameCharacter getPlayer() { return player; }
    public void setPlayer(GameCharacter player) { this.player = player; }
    public java.util.List<MapNode> getMap() { return map; }
    public void setMap(java.util.List<MapNode> map) { this.map = map; }
    public MapNode getCurrentNode() { return currentNode; }
    public void setCurrentNode(MapNode currentNode) { this.currentNode = currentNode; }
    public BattleState getBattle() { return battle; }
    public void setBattle(BattleState battle) { this.battle = battle; }
    public boolean isMapOpen() { return mapOpen; }
    public void setMapOpen(boolean mapOpen) { this.mapOpen = mapOpen; }
    public String getLastEvent() { return lastEvent; }
    public void setLastEvent(String lastEvent) { this.lastEvent = lastEvent; }
    public int getCurrentLayer() { return currentLayer; }
    public void setCurrentLayer(int currentLayer) { this.currentLayer = currentLayer; }
    public int getMaxLayer() { return maxLayer; }
    public void setMaxLayer(int maxLayer) { this.maxLayer = maxLayer; }
    public int getBonfireUpgradesLeft() { return bonfireUpgradesLeft; }
    public void setBonfireUpgradesLeft(int bonfireUpgradesLeft) { this.bonfireUpgradesLeft = bonfireUpgradesLeft; }
}
