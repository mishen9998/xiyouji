package com.xiyouji.service.room;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Enemy;
import com.xiyouji.model.MapNode;
import com.xiyouji.repository.EnemyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 多人模式地图服务
 * 复用单机 MapService 的地图生成算法，操作 Room 而非 GameSession。
 * 所有玩家共享同一张地图，房主负责选择移动路线。
 */
@Service
public class MultiplayerMapService {

    private static final Logger log = LoggerFactory.getLogger(MultiplayerMapService.class);

    private final EnemyRepository enemyRepo;
    private final Random rand = new Random();

    public MultiplayerMapService(EnemyRepository enemyRepo) {
        this.enemyRepo = enemyRepo;
    }

    /**
     * 生成一层地图（复用单机算法）
     */
    public List<MapNode> generateLayer(int layer) {
        List<MapNode> nodes = new ArrayList<>();
        int nameIdx = (layer - 1) * GameConstants.ROWS_PER_LAYER;

        List<List<MapNode>> grid = new ArrayList<>();
        for (int row = 0; row < GameConstants.ROWS_PER_LAYER; row++) {
            List<MapNode> rowNodes = new ArrayList<>();

            if (row == 0) {
                // 起点：2个战斗节点
                for (int c = 0; c < 2; c++) {
                    String id = "L" + layer + "-R0-C" + c;
                    MapNode n = new MapNode(id, layer, row, c, GameConstants.NODE_BATTLE,
                            GameConstants.PLACE_NAMES[(nameIdx + row) % GameConstants.PLACE_NAMES.length]);
                    n.setAccessible(true);
                    assignEnemy(n, false, layer);
                    rowNodes.add(n);
                }
            } else if (row == GameConstants.ROWS_PER_LAYER - 1) {
                // Boss行：1个Boss节点
                String id = "L" + layer + "-R" + row + "-C1";
                MapNode n = new MapNode(id, layer, row, 1, GameConstants.NODE_BOSS,
                        layer == 1 ? "黑风洞" : layer == 2 ? "火焰山" : "大雷音寺");
                assignEnemy(n, true, layer);
                rowNodes.add(n);
            } else {
                // 中间行：2-4个节点，随机类型
                int nodeCount = 2 + rand.nextInt(3);
                for (int i = 0; i < nodeCount; i++) {
                    int col = (int) ((i + 0.5) * 4.0 / nodeCount);
                    String id = "L" + layer + "-R" + row + "-C" + col;
                    String type = randomNodeType();
                    String name = GameConstants.PLACE_NAMES[(nameIdx + row + i) % GameConstants.PLACE_NAMES.length];
                    MapNode n = new MapNode(id, layer, row, col, type, name);
                    if (GameConstants.NODE_BATTLE.equals(type)) {
                        assignEnemy(n, false, layer);
                    }
                    rowNodes.add(n);
                }
            }
            grid.add(rowNodes);
            nodes.addAll(rowNodes);
        }

        // 建立连接：每个节点连接到下一行1-2个节点
        for (int row = 0; row < GameConstants.ROWS_PER_LAYER - 1; row++) {
            List<MapNode> currentRow = grid.get(row);
            List<MapNode> nextRow = grid.get(row + 1);

            for (MapNode node : currentRow) {
                List<MapNode> candidates = new ArrayList<>(nextRow);
                candidates.sort(Comparator.comparingInt(n -> Math.abs(n.getCol() - node.getCol())));

                int connectCount = Math.min(1 + rand.nextInt(2), candidates.size());
                for (int i = 0; i < connectCount; i++) {
                    if (!node.getConnections().contains(candidates.get(i).getId())) {
                        node.getConnections().add(candidates.get(i).getId());
                    }
                }
            }

            // 确保下一行每个节点都至少有一个来源连接
            for (MapNode nextNode : nextRow) {
                boolean hasSource = currentRow.stream()
                        .anyMatch(n -> n.getConnections().contains(nextNode.getId()));
                if (!hasSource && !currentRow.isEmpty()) {
                    MapNode nearest = currentRow.stream()
                            .min(Comparator.comparingInt(n -> Math.abs(n.getCol() - nextNode.getCol())))
                            .orElse(currentRow.get(0));
                    nearest.getConnections().add(nextNode.getId());
                }
            }
        }

        log.info("Multiplayer map layer {} generated: {} nodes", layer, nodes.size());
        return nodes;
    }

    private String randomNodeType() {
        double r = rand.nextDouble();
        if (r < GameConstants.BATTLE_NODE_PROBABILITY) return GameConstants.NODE_BATTLE;
        if (r < GameConstants.REST_NODE_PROBABILITY) return GameConstants.NODE_REST;
        if (r < GameConstants.TREASURE_NODE_PROBABILITY) return GameConstants.NODE_TREASURE;
        if (r < GameConstants.SHOP_NODE_PROBABILITY) return GameConstants.NODE_SHOP;
        if (r < GameConstants.BONFIRE_NODE_PROBABILITY) return GameConstants.NODE_BONFIRE;
        return GameConstants.NODE_RANDOM;
    }

    private void assignEnemy(MapNode node, boolean isBoss, int layer) {
        int enemyLevel = Math.min(layer, GameConstants.MAX_LAYERS);
        List<Enemy> enemies = enemyRepo.findByIsBossAndLevel(isBoss, enemyLevel);
        if (enemies.isEmpty()) {
            enemies = enemyRepo.findByIsBoss(isBoss);
        }
        if (!enemies.isEmpty()) {
            Enemy e = enemies.get(rand.nextInt(enemies.size()));
            node.setEnemyId(String.valueOf(e.getId()));
        }
    }

    /**
     * 移动到指定节点（多人共享位置，房主操作）
     *
     * @param room   房间
     * @param nodeId 目标节点ID
     * @return 目标节点
     */
    public MapNode moveToNode(Room room, String nodeId) {
        MapNode target = room.getMap().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new InvalidActionException("节点不存在: " + nodeId));

        if (!target.isAccessible()) {
            throw new InvalidActionException("无法到达此节点");
        }

        // 标记所有其他可达节点为不可达（只能选一个走）
        room.getMap().stream()
                .filter(n -> n.isAccessible() && !n.isVisited())
                .forEach(n -> n.setAccessible(false));

        target.setVisited(true);
        room.setCurrentNode(target);

        // 篝火节点：初始化升级次数
        if (GameConstants.NODE_BONFIRE.equals(target.getType())) {
            room.setBonfireUpgradesLeft(GameConstants.BONFIRE_UPGRADE_LIMIT);
        }

        // 解锁目标节点连接的下一行节点
        for (String nextId : target.getConnections()) {
            room.getMap().stream()
                    .filter(n -> n.getId().equals(nextId))
                    .findFirst()
                    .ifPresent(n -> n.setAccessible(true));
        }

        log.info("Room {} moved to node: {}, type={}", room.getCode(), nodeId, target.getType());
        return target;
    }

    /**
     * 节点类型转换为事件类型字符串
     */
    public String interpretNode(MapNode node) {
        return switch (node.getType()) {
            case "BATTLE" -> "battle";
            case "BOSS" -> "boss_battle";
            case "REST" -> "rest";
            case "TREASURE" -> "treasure";
            case "SHOP" -> "shop";
            case "RANDOM" -> "random";
            case "BONFIRE" -> "bonfire";
            default -> "unknown";
        };
    }

    /**
     * 进入下一层
     *
     * @return true表示成功进入下一层，false表示已通关
     */
    public boolean advanceToNextLayer(Room room) {
        int nextLayer = room.getFloor() + 1;
        if (nextLayer > room.getMaxLayer()) {
            log.info("Room {} completed all layers!", room.getCode());
            return false;
        }
        room.setFloor(nextLayer);
        room.getMap().clear();
        room.getMap().addAll(generateLayer(nextLayer));
        room.setCurrentNode(null);
        room.setMapOpen(true);
        log.info("Room {} advanced to layer {}", room.getCode(), nextLayer);
        return true;
    }

    /**
     * 检查当前节点是否是Boss
     */
    public boolean isAtBoss(Room room) {
        return room.getCurrentNode() != null
                && GameConstants.NODE_BOSS.equals(room.getCurrentNode().getType());
    }
}
