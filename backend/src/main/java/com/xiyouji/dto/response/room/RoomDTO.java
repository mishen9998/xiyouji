package com.xiyouji.dto.response.room;

import com.xiyouji.model.MapNode;
import com.xiyouji.service.room.RoomPlayer;
import com.xiyouji.service.room.RoomStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 房间响应DTO
 * 返回给前端时使用，屏蔽内部细节。
 */
public class RoomDTO {

    private String code;
    private String hostUserId;
    private List<RoomPlayer> players;
    private int playerCount;
    private RoomStatus status;
    private LocalDateTime createdAt;
    private int floor;
    private int maxLayer;
    private List<MapNode> map;
    private MapNode currentNode;
    private int bonfireUpgradesLeft;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getHostUserId() { return hostUserId; }
    public void setHostUserId(String hostUserId) { this.hostUserId = hostUserId; }

    public List<RoomPlayer> getPlayers() { return players; }
    public void setPlayers(List<RoomPlayer> players) { this.players = players; }

    public int getPlayerCount() { return playerCount; }
    public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }

    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }

    public int getMaxLayer() { return maxLayer; }
    public void setMaxLayer(int maxLayer) { this.maxLayer = maxLayer; }

    public List<MapNode> getMap() { return map; }
    public void setMap(List<MapNode> map) { this.map = map; }

    public MapNode getCurrentNode() { return currentNode; }
    public void setCurrentNode(MapNode currentNode) { this.currentNode = currentNode; }

    public int getBonfireUpgradesLeft() { return bonfireUpgradesLeft; }
    public void setBonfireUpgradesLeft(int bonfireUpgradesLeft) { this.bonfireUpgradesLeft = bonfireUpgradesLeft; }
}
