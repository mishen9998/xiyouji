package com.xiyouji.dto;

import com.xiyouji.model.MapNode;
import com.xiyouji.service.session.GameSession;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 游戏会话响应组装器
 *
 * 将领域状态（GameSession / MapNode）组装为 HTTP 友好的 Map 结构，
 * 集中 GameController 中散落的响应组装逻辑（完整状态/新游戏/移动/下一层/玩家刷新），
 * 与 PlayerSummaryAssembler 同层，使控制器保持纯转发职责。
 */
@Component
public class GameSessionAssembler {

    private final PlayerSummaryAssembler playerSummaryAssembler;

    public GameSessionAssembler(PlayerSummaryAssembler playerSummaryAssembler) {
        this.playerSummaryAssembler = playerSummaryAssembler;
    }

    /** 完整游戏状态响应（GET /api/game/state/{sessionId}） */
    public Map<String, Object> fullState(String sessionId, GameSession session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("stateVersion", session.getStateVersion());
        result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
        result.put("map", session.getMap());
        result.put("currentNode", session.getCurrentNode());
        result.put("mapOpen", session.isMapOpen());
        result.put("lastEvent", session.getLastEvent());
        result.put("storyEvent", story(session));
        result.put("currentLayer", session.getCurrentLayer());
        result.put("maxLayer", session.getMaxLayer());
        result.put("inBattle", session.getBattle() != null && (!session.getBattle().isBattleOver()
                || session.getBattle().getCardRewards() != null
                || (session.getBattle().isVictory() && session.getCurrentNode() != null
                    && "BOSS".equals(session.getCurrentNode().getType()))));
        return result;
    }

    /** 新游戏响应 */
    public Map<String, Object> newGame(String sessionId, GameSession session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("stateVersion", session.getStateVersion());
        result.put("success", true);
        result.put("storyEvent", story(session));
        result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
        result.put("map", session.getMap());
        result.put("currentNode", session.getCurrentNode());
        return result;
    }

    /** 移动节点响应 */
    public Map<String, Object> moveResult(MapNode node, long stateVersion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("node", node);
        result.put("eventType", node.domainEventType());
        result.put("stateVersion", stateVersion);
        return result;
    }

    /** 进入下一层响应 */
    public Map<String, Object> nextLayerResult(GameSession session, boolean success) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("storyEvent", story(session));
        result.put("currentLayer", session.getCurrentLayer());
        result.put("maxLayer", session.getMaxLayer());
        result.put("stateVersion", session.getStateVersion());
        return result;
    }

    /** 玩家摘要 + 状态版本（事件处理、移除卡牌等刷新型响应） */
    public Map<String, Object> playerState(GameSession session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stateVersion", session.getStateVersion());
        result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
        return result;
    }
    private com.xiyouji.dto.response.StoryEvent story(GameSession session) {
        return com.xiyouji.service.event.StoryCatalog.map(session.getSessionId(),session.getCurrentLayer(),
            session.getCurrentNode(),session.isCompleted(),java.util.List.of(com.xiyouji.service.event.EventActor.solo(session)));
    }
}
