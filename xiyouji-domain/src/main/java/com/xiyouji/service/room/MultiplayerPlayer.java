package com.xiyouji.service.room;

import com.xiyouji.model.GameCharacter;

import java.io.Serializable;

/**
 * 多人战斗中的玩家包装类
 * 封装玩家身份信息 + 运行时角色状态（GameCharacter）+ 回合状态
 */
public class MultiplayerPlayer implements Serializable {

    private String userId;
    private String username;
    private GameCharacter character;
    private boolean endedTurn;
    private boolean alive = true;

    public MultiplayerPlayer() {}

    public MultiplayerPlayer(String userId, String username, GameCharacter character) {
        this.userId = userId;
        this.username = username;
        this.character = character;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public GameCharacter getCharacter() { return character; }
    public void setCharacter(GameCharacter character) { this.character = character; }

    public boolean isEndedTurn() { return endedTurn; }
    public void setEndedTurn(boolean endedTurn) { this.endedTurn = endedTurn; }

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
}
