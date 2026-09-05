package com.xiyouji.service;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Enemy;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.Relic;
import com.xiyouji.port.EnemyRepositoryPort;
import com.xiyouji.service.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 地图服务 - 负责地图生成和移动逻辑
 * 从GameService中提取，管理多路线分支地图的生成、节点移动、层级推进
 */
@Service
public class MapService {

    private static final Logger log = LoggerFactory.getLogger(MapService.class);

    private final EnemyRepositoryPort enemyRepo;

    public MapService(EnemyRepositoryPort enemyRepo) {
        this.enemyRepo = enemyRepo;
    }

    // ====== 多路线分支地图生成 ======

    /**
     * 生成一层地图 - 27行多路线从底部走到顶部
     * 行0 = 唐朝皇帝赐宝节点（1个EMPEROR）
     * 行1~25 = 随机分布的战斗/商店/休息/宝箱/篝火/随机事件节点（多路线分支）
     * 行26 = Boss节点（1个BOSS）
     * 每个节点向下连接1~2个相邻节点形成分支路线
     *
     * @param layer 当前层号 (1-3)
     * @return 该层所有节点列表
     */
    public List<MapNode> generateLayer(int layer) {
        List<MapNode> nodes = new ArrayList<>();
        Random rand = new Random();
        int nameIdx = (layer - 1) * GameConstants.ROWS_PER_LAYER;

        // 按行生成节点
        List<List<MapNode>> grid = new ArrayList<>();
        for (int row = 0; row < GameConstants.ROWS_PER_LAYER; row++) {
            List<MapNode> rowNodes = new ArrayList<>();

            if (row == GameConstants.EMPEROR_ROW) {
                // 行0：唐朝皇帝赐宝节点（1个，固定可达）
                String id = "L" + layer + "-R0-C1";
                MapNode n = new MapNode(id, layer, row, 1, GameConstants.NODE_EMPEROR, "大唐皇宫");
                n.setAccessible(true);
                rowNodes.add(n);
            } else if (row == GameConstants.BOSS_ROW) {
                // 行26：Boss节点（1个）
                String id = "L" + layer + "-R" + row + "-C1";
                MapNode n = new MapNode(id, layer, row, 1, GameConstants.NODE_BOSS,
                        layer == 1 ? "黑风洞" : layer == 2 ? "火焰山" : "大雷音寺");
                assignEnemy(n, true, layer);
                rowNodes.add(n);
            } else {
                // 中间行（1~25）：2-4个节点，随机类型
                int nodeCount = 2 + rand.nextInt(3); // 2-4
                for (int i = 0; i < nodeCount; i++) {
                    int col = (int) ((i + 0.5) * 4.0 / nodeCount);
                    String id = "L" + layer + "-R" + row + "-C" + col;
                    String type = randomNodeType(rand);
                    String name = GameConstants.NODE_SHOP.equals(type)
                            ? "土地庙"
                            : GameConstants.PLACE_NAMES[(nameIdx + row + i) % GameConstants.PLACE_NAMES.length];
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

        // 建立连接：每个节点连接到下一行1-2个相邻节点（多路线分支）
        for (int row = 0; row < GameConstants.ROWS_PER_LAYER - 1; row++) {
            List<MapNode> currentRow = grid.get(row);
            List<MapNode> nextRow = grid.get(row + 1);

            for (MapNode node : currentRow) {
                List<MapNode> candidates = new ArrayList<>(nextRow);
                candidates.sort(Comparator.comparingInt(n ->
                        Math.abs(n.getCol() - node.getCol())));

                int connectCount = Math.min(1 + rand.nextInt(2), candidates.size());
                for (int i = 0; i < connectCount; i++) {
                    if (!node.getConnections().contains(candidates.get(i).getId())) {
                        node.getConnections().add(candidates.get(i).getId());
                    }
                }
            }

            // 确保下一行每个节点都至少有一个来源连接（避免断路）
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

        log.debug("地图层 {} 生成完成，共 {} 个节点", layer, nodes.size());
        return nodes;
    }

    /**
     * 随机节点类型 - 战斗偏多，其余随机
     *
     * @param rand 随机数生成器
     * @return 节点类型字符串
     */
    private String randomNodeType(Random rand) {
        double r = rand.nextDouble();
        if (r < GameConstants.BATTLE_NODE_PROBABILITY) return GameConstants.NODE_BATTLE;
        if (r < GameConstants.REST_NODE_PROBABILITY) return GameConstants.NODE_REST;
        if (r < GameConstants.TREASURE_NODE_PROBABILITY) return GameConstants.NODE_TREASURE;
        if (r < GameConstants.SHOP_NODE_PROBABILITY) return GameConstants.NODE_SHOP;
        if (r < GameConstants.BONFIRE_NODE_PROBABILITY) return GameConstants.NODE_BONFIRE;
        return GameConstants.NODE_RANDOM;
    }

    /**
     * 为节点分配敌人
     *
     * @param node   地图节点
     * @param isBoss 是否Boss节点
     * @param layer  当前层号
     */
    private void assignEnemy(MapNode node, boolean isBoss, int layer) {
        int enemyLevel = Math.min(layer, GameConstants.MAX_LAYERS);
        List<Enemy> enemies = enemyRepo.findByIsBossAndLevel(isBoss, enemyLevel);
        if (enemies.isEmpty()) {
            enemies = enemyRepo.findByIsBoss(isBoss);
        }
        if (!enemies.isEmpty()) {
            Random rand = new Random();
            Enemy e = enemies.get(rand.nextInt(enemies.size()));
            node.setEnemyId(String.valueOf(e.getId()));
        }
    }

    // ====== 地图移动逻辑 ======

    /**
     * 前往节点 - 基于连接关系解锁
     *
     * @param session 游戏会话
     * @param nodeId  目标节点ID
     * @return 目标节点
     * @throws InvalidActionException 节点不存在或无法到达
     */
    public MapNode moveToNode(GameSession session, String nodeId) {
        if (session.getBattle() != null && session.getBattle().getCardRewards() != null) {
            throw new InvalidActionException("请先选择或跳过战斗奖励");
        }
        MapNode target = session.getMap().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new InvalidActionException("节点不存在: " + nodeId));

        if (!target.isAccessible()) {
            throw new InvalidActionException("无法到达此节点");
        }

        // 标记之前的所有可达节点为不可达（只能选一个走）
        session.getMap().stream()
                .filter(n -> n.isAccessible() && !n.isVisited())
                .forEach(n -> n.setAccessible(false));

        session.setBattle(null);
        target.setVisited(true);
        session.setCurrentNode(target);
        session.getPlayer().setFloor(target.getLayer());

        // 篝火节点：初始化升级次数
        if (GameConstants.NODE_BONFIRE.equals(target.getType())) {
            session.setBonfireUpgradesLeft(GameConstants.BONFIRE_UPGRADE_LIMIT);
        }

        // 解锁目标节点连接的下一行节点
        for (String nextId : target.getConnections()) {
            session.getMap().stream()
                    .filter(n -> n.getId().equals(nextId))
                    .findFirst()
                    .ifPresent(n -> n.setAccessible(true));
        }

        log.info("玩家移动到节点: sessionId={}, nodeId={}, type={}",
                session.getSessionId(), nodeId, target.getType());
        return target;
    }

    /**
     * Boss击败后进入下一层
     * 触发LAYER_START效果（如大唐通关文牒：每层开始+20生命）
     *
     * @param session 游戏会话
     * @return true表示成功进入下一层，false表示已通关
     */
    public boolean advanceToNextLayer(GameSession session) {
        if (session.getBattle() != null && session.getBattle().getCardRewards() != null) {
            throw new InvalidActionException("请先选择或跳过战斗奖励");
        }
        session.setBattle(null);
        int nextLayer = session.getCurrentLayer() + 1;
        if (nextLayer > session.getMaxLayer()) {
            log.info("玩家通关: sessionId={}", session.getSessionId());
            return false;
        }
        session.setCurrentLayer(nextLayer);
        session.getMap().clear();
        session.getMap().addAll(generateLayer(nextLayer));
        session.setCurrentNode(null);
        session.setMapOpen(true);

        // ★ 大唐通关文牒：每层开始回复20点生命
        for (Relic relic : session.getPlayer().getRelics()) {
            if (GameConstants.RELIC_EMPEROR_PASSPORT.equals(relic.getName())
                    && relic.getEffect() != null && relic.getEffect().contains("LAYER_START")) {
                String[] parts = relic.getEffect().split(";");
                for (String p : parts) {
                    if (p.startsWith("HEAL:")) {
                        int healAmount = Integer.parseInt(p.substring(5));
                        session.getPlayer().heal(healAmount);
                        log.info("宝物[{}]触发: 进入第{}层回复{}点生命", relic.getName(), nextLayer, healAmount);
                    }
                }
            }
        }

        log.info("玩家进入第 {} 层: sessionId={}", nextLayer, session.getSessionId());
        return true;
    }

    /**
     * 检查当前节点是否是Boss
     *
     * @param session 游戏会话
     * @return true表示当前在Boss节点
     */
    public boolean isAtBoss(GameSession session) {
        return session.getCurrentNode() != null
                && GameConstants.NODE_BOSS.equals(session.getCurrentNode().getType());
    }
}
