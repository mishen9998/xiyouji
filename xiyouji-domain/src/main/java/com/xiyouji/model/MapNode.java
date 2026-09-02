package com.xiyouji.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图节点 — 支持多路线分支地图
 */
public class MapNode {
    private String id;
    private int layer;              // 第几层 (1-3)
    private int row;                // 在当前层中的行号 (0=底部起点, 最大行=Boss)
    private int col;                // 列号 (用于水平定位)
    private String type;            // BATTLE/BOSS/REST/TREASURE/SHOP/RANDOM/BONFIRE
    private String name;
    private boolean visited;
    private boolean accessible;
    private String enemyId;         // 战斗节点的敌人ID
    private List<String> connections; // 连接的下一行节点ID列表（支持多路线分支）

    public MapNode() {
        this.connections = new ArrayList<>();
    }

    public MapNode(String id, int layer, int row, int col, String type, String name) {
        this.id = id;
        this.layer = layer;
        this.row = row;
        this.col = col;
        this.type = type;
        this.name = name;
        this.visited = false;
        this.accessible = false;
        this.connections = new ArrayList<>();
    }

    // 兼容旧代码
    public int getLevel() { return layer; }
    public void setLevel(int level) { this.layer = level; }
    public int getPosition() { return row; }
    public void setPosition(int position) { this.row = position; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getLayer() { return layer; }
    public void setLayer(int layer) { this.layer = layer; }
    public int getRow() { return row; }
    public void setRow(int row) { this.row = row; }
    public int getCol() { return col; }
    public void setCol(int col) { this.col = col; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isVisited() { return visited; }
    public void setVisited(boolean visited) { this.visited = visited; }
    public boolean isAccessible() { return accessible; }
    public void setAccessible(boolean accessible) { this.accessible = accessible; }
    public String getEnemyId() { return enemyId; }
    public void setEnemyId(String enemyId) { this.enemyId = enemyId; }
    public List<String> getConnections() { return connections; }
    public void setConnections(List<String> connections) { this.connections = connections; }
}
