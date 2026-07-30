package com.xiyouji.dto.response;

/**
 * 游戏状态响应DTO
 */
public class GameResponse {

    private String sessionId;
    private boolean success;
    private String message;
    private Object player;
    private Object map;
    private Object currentNode;
    private boolean inBattle;

    public GameResponse() {
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getPlayer() {
        return player;
    }

    public void setPlayer(Object player) {
        this.player = player;
    }

    public Object getMap() {
        return map;
    }

    public void setMap(Object map) {
        this.map = map;
    }

    public Object getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(Object currentNode) {
        this.currentNode = currentNode;
    }

    public boolean isInBattle() {
        return inBattle;
    }

    public void setInBattle(boolean inBattle) {
        this.inBattle = inBattle;
    }
}
