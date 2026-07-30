package com.xiyouji.service.room;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xiyouji.model.MapNode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 多人协作房间
 * 5人PvE闯关的房间对象，通过8位房间码标识。
 * 房间状态流转：WAITING(等待中) → IN_BATTLE(战斗中) → FINISHED(已结束)
 *
 * 存储位置：Redis（key=room:{code}），TTL 2小时。
 * 战斗状态单独存储于 MultiplayerBattleState，不在此对象内。
 */
public class Room implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 最大玩家数（唐僧师徒五人） */
    public static final int MAX_PLAYERS = 5;

    /** 8位房间码（大写字母+数字，去除易混淆字符 0/O/1/I/L） */
    private String code;

    /** 房主用户ID */
    private String hostUserId;

    /** 房间内玩家列表 */
    private List<RoomPlayer> players = new ArrayList<>();

    /** 房间状态 */
    private RoomStatus status = RoomStatus.WAITING;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 当前关卡层数 */
    private int floor = 1;

    /** 最大层数 */
    private int maxLayer = 3;

    /** 当前层地图节点列表 */
    private List<MapNode> map = new ArrayList<>();

    /** 当前所在节点 */
    private MapNode currentNode;

    /** 地图是否展开 */
    private boolean mapOpen = true;

    /** 篝火剩余升级次数 */
    private int bonfireUpgradesLeft = 0;

    public Room() {
    }

    public Room(String code, String hostUserId) {
        this.code = code;
        this.hostUserId = hostUserId;
        this.createdAt = LocalDateTime.now();
    }

    /** 当前玩家数 */
    @JsonIgnore
    public int getPlayerCount() {
        return players.size();
    }

    /** 房间是否已满 */
    @JsonIgnore
    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    /** 所有玩家是否都已准备 */
    public boolean allReady() {
        return !players.isEmpty()
                && players.stream().allMatch(RoomPlayer::isReady);
    }

    /** 是否包含某玩家 */
    public boolean hasPlayer(String userId) {
        return players.stream().anyMatch(p -> p.getUserId().equals(userId));
    }

    // ===== Getters/Setters =====

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getHostUserId() { return hostUserId; }
    public void setHostUserId(String hostUserId) { this.hostUserId = hostUserId; }

    public List<RoomPlayer> getPlayers() { return players; }
    public void setPlayers(List<RoomPlayer> players) { this.players = players; }

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

    @JsonIgnore
    public boolean isMapOpen() { return mapOpen; }
    public void setMapOpen(boolean mapOpen) { this.mapOpen = mapOpen; }

    public int getBonfireUpgradesLeft() { return bonfireUpgradesLeft; }
    public void setBonfireUpgradesLeft(int bonfireUpgradesLeft) { this.bonfireUpgradesLeft = bonfireUpgradesLeft; }
}
