package com.xiyouji.controller;

import com.xiyouji.dto.PlayerSummaryAssembler;
import com.xiyouji.dto.request.EventRequest;
import com.xiyouji.dto.request.MoveRequest;
import com.xiyouji.dto.request.NewGameRequest;
import com.xiyouji.dto.request.RemoveCardRequest;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.*;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.service.GameService;
import com.xiyouji.service.GameEventProcessor;
import com.xiyouji.service.CommandGuard;
import com.xiyouji.service.IdempotentCommandRunner;
import com.xiyouji.service.session.GameSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

/**
 * 游戏主控制器 - 管理游戏会话、地图移动、节点事件等核心API
 */
@RestController
@RequestMapping("/api/game")
@Validated
@Tag(name = "游戏主系统", description = "游戏会话、地图探索与节点事件相关API")
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    private final GameService gameService;
    private final GameEventProcessor eventProcessor;
    private final PlayerSummaryAssembler playerSummaryAssembler;
    private final IdempotentCommandRunner idempotent;

    public GameController(GameService gameService, GameEventProcessor eventProcessor,
                          PlayerSummaryAssembler playerSummaryAssembler,
                          IdempotentCommandRunner idempotent) {
        this.gameService = gameService;
        this.eventProcessor = eventProcessor;
        this.playerSummaryAssembler = playerSummaryAssembler;
        this.idempotent = idempotent;
    }

    /** 开始新游戏 */
    @PostMapping("/new")
    @Operation(summary = "开始新游戏", description = "创建新的游戏会话，选择角色职业并生成第一层地图")
    public Map<String, Object> newGame(@Valid @RequestBody NewGameRequest request,
                                       @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String username = currentUsername();
        String charClass = request.getCharacterClass();
        log.info("Creating new game with character class: {}", charClass);

        CharacterClass characterClass = parseCharacterClass(charClass);

        String fingerprint = CommandGuard.fingerprint("POST", "/api/game/new", charClass);
        String scope = "game:new:" + username;
        return idempotent.run(scope, idempotencyKey, fingerprint, Map.class,
                () -> {
                    String sessionId = UUID.randomUUID().toString().substring(0, 8);
                    GameSession session = gameService.newGame(sessionId, characterClass, username);
                    Map<String, Object> result = newGameResponse(session, sessionId);
                    log.info("New game created successfully, sessionId: {}", sessionId);
                    return result;
                },
                previous -> {
                    GameSession existing = gameService.getSessionForUser(previous.value(), username);
                    return newGameResponse(existing, previous.value());
                });
    }

    /** 获取游戏状态 */
    @GetMapping("/state/{sessionId}")
    @Operation(summary = "获取游戏状态", description = "查询指定会话的完整游戏状态，包括玩家信息、地图、当前节点等")
    public Map<String, Object> gameState(@PathVariable String sessionId) {
        log.debug("Fetching game state for session: {}", sessionId);
        GameSession session = gameService.getSessionForUser(sessionId, currentUsername());
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("stateVersion", session.getStateVersion());
        result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
        result.put("map", session.getMap());
        result.put("currentNode", session.getCurrentNode());
        result.put("mapOpen", session.isMapOpen());
        result.put("lastEvent", session.getLastEvent());
        result.put("currentLayer", session.getCurrentLayer());
        result.put("maxLayer", session.getMaxLayer());
        result.put("inBattle", session.getBattle() != null && (!session.getBattle().isBattleOver()
                || session.getBattle().getCardRewards() != null
                || (session.getBattle().isVictory() && session.getCurrentNode() != null
                    && "BOSS".equals(session.getCurrentNode().getType()))));
        return result;
    }

    /** 删除会话（删除存档） */
    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "删除会话", description = "删除指定的游戏会话，用于放弃当前存档")
    public Map<String, Object> deleteSession(@PathVariable String sessionId,
                                              @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                              @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        log.info("Deleting session: {}", sessionId);
        String username = currentUsername();
        String fingerprint = CommandGuard.fingerprint("DELETE", "/api/game/sessions/" + sessionId, "");
        String scope = "game:delete:" + username + ":" + sessionId;
        return idempotent.run(scope, idempotencyKey, fingerprint, Map.class,
                () -> {
                    boolean ok = gameService.deleteSession(sessionId, expectedVersion, username);
                    if (!ok) {
                        log.warn("Session not found for deletion: {}", sessionId);
                    }
                    return Map.of("success", ok);
                },
                previous -> Map.of("success", true));
    }

    /** 移动到地图节点 */
    @PostMapping("/move/{sessionId}")
    @Operation(summary = "移动到地图节点", description = "将玩家移动到指定的地图节点，返回节点信息及事件类型")
    public Map<String, Object> move(@PathVariable String sessionId,
                                    @Valid @RequestBody MoveRequest request,
                                    @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                    @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String nodeId = request.getNodeId();
        log.info("Moving to node {} for session: {}", nodeId, sessionId);
        String username = currentUsername();
        String fingerprint = CommandGuard.fingerprint("POST", "/api/game/move/" + sessionId, nodeId);
        String scope = "game:move:" + username + ":" + sessionId;
        return idempotent.run(scope, idempotencyKey, fingerprint, Map.class,
                () -> {
                    MapNode node = gameService.moveToNode(sessionId, nodeId, expectedVersion, username);
                    Map<String, Object> result = new HashMap<>();
                    result.put("node", node);
                    result.put("eventType", interpretNode(node));
                    result.put("stateVersion", gameService.getSession(sessionId).getStateVersion());
                    return result;
                },
                previous -> {
                    GameSession existing = gameService.getSessionForUser(sessionId, username);
                    return Map.of("node", existing.getCurrentNode(), "eventType", interpretNode(existing.getCurrentNode()),
                            "stateVersion", existing.getStateVersion());
                });
    }

    /** Boss击败后进入下一层 */
    @PostMapping("/next-layer/{sessionId}")
    @Operation(summary = "进入下一层", description = "Boss击败后推进到下一层地图，若已通关则返回通关消息")
    public Map<String, Object> nextLayer(@PathVariable String sessionId,
                                         @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                         @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        log.info("Advancing to next layer for session: {}", sessionId);
        String username = currentUsername();
        String fingerprint = CommandGuard.fingerprint("POST", "/api/game/next-layer/" + sessionId, "");
        String scope = "game:next-layer:" + username + ":" + sessionId;
        return idempotent.run(scope, idempotencyKey, fingerprint, Map.class,
                () -> {
                    boolean success = gameService.advanceToNextLayer(sessionId, expectedVersion, username);
                    GameSession session = gameService.getSessionForUser(sessionId, username);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", success);
                    result.put("currentLayer", session.getCurrentLayer());
                    result.put("maxLayer", session.getMaxLayer());
                    result.put("stateVersion", session.getStateVersion());
                    if (!success) {
                        log.info("Game completed for session: {}", sessionId);
                        result.put("message", "恭喜通关！西天取经圆满！");
                    }
                    return result;
                },
                previous -> {
                    GameSession existing = gameService.getSessionForUser(sessionId, username);
                    return Map.of("success", true, "currentLayer", existing.getCurrentLayer(),
                            "maxLayer", existing.getMaxLayer(), "stateVersion", existing.getStateVersion());
                });
    }

    /** 从节点获得收益（休息/宝箱/商店） */
    @PostMapping("/event/{sessionId}")
    @Operation(summary = "处理节点事件", description = "处理休息、篝火升级、宝箱、商店、随机事件等节点交互")
    public Map<String, Object> handleEvent(@PathVariable String sessionId,
                                           @Valid @RequestBody EventRequest request,
                                           @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                           @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String username = currentUsername();
        String action = request.getAction() != null ? request.getAction() : "none";
        String fingerprint = CommandGuard.fingerprint("POST", "/api/game/event/" + sessionId,
                action + "|" + request.getCardIndex() + "|" + request.getCardId() + "|"
                        + request.getPrice() + "|" + request.getRelicName());
        String scope = "game:event:" + username + ":" + sessionId;
        return idempotent.run(scope, idempotencyKey, fingerprint, Map.class,
                () -> gameService.withSessionLock(sessionId,
                        () -> eventProcessor.process(sessionId, request, expectedVersion, username)),
                previous -> {
                GameSession existing = gameService.getSessionForUser(sessionId, username);
                return Map.of("stateVersion", existing.getStateVersion(), "player",
                        playerSummaryAssembler.toPlayerSummary(existing.getPlayer()));
            });
    }

    /** 移除卡牌 */
    @PostMapping("/deck/remove/{sessionId}")
    @Operation(summary = "移除卡牌", description = "从玩家牌组中移除指定索引的卡牌")
    public Map<String, Object> removeCard(@PathVariable String sessionId,
                                          @Valid @RequestBody RemoveCardRequest request,
                                          @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                          @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        int index = request.getIndex();
        log.info("Removing card at index {} for session: {}", index, sessionId);
        String username = currentUsername();
        String fingerprint = CommandGuard.fingerprint("POST", "/api/game/deck/remove/" + sessionId,
                String.valueOf(index));
        String scope = "game:deck-remove:" + username + ":" + sessionId;
        return idempotent.run(scope, idempotencyKey, fingerprint, Map.class,
                () -> {
                    gameService.removeCardFromDeck(sessionId, index, expectedVersion, username);
                    GameSession session = gameService.getSessionForUser(sessionId, username);
                    return Map.of("success", true, "stateVersion", session.getStateVersion(),
                            "player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
                },
                previous -> {
                    GameSession existing = gameService.getSessionForUser(sessionId, username);
                    return Map.of("success", true, "stateVersion", existing.getStateVersion(),
                            "player", playerSummaryAssembler.toPlayerSummary(existing.getPlayer()));
                });
    }

    // ===== 辅助方法 =====

    /**
     * 大小写不敏感地解析角色职业枚举。
     * 支持 "sunwukong"、"SUN_WUKONG"、"Sun_Wukong" 等各种格式，
     * 也支持中文名称 "孙悟空" 匹配。
     */
    private CharacterClass parseCharacterClass(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidActionException("角色职业不能为空");
        }
        // 1. 尝试直接枚举匹配（大小写敏感）
        try {
            return CharacterClass.valueOf(input);
        } catch (IllegalArgumentException ignored) {}
        // 2. 大小写不敏感匹配：转大写并替换空格/连字符为下划线
        String normalized = input.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        try {
            return CharacterClass.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {}
        // 3. 去除下划线后匹配（如 "sunwukong" → "SUNWUKONG" vs "SUN_WUKONG"）
        String noSeparator = normalized.replace("_", "");
        for (CharacterClass cc : CharacterClass.values()) {
            if (cc.name().replace("_", "").equals(noSeparator)) {
                return cc;
            }
        }
        // 4. 中文名称匹配
        for (CharacterClass cc : CharacterClass.values()) {
            if (cc.getDisplayName().equals(input.trim())) {
                return cc;
            }
        }
        log.warn("Invalid character class provided: {}", input);
        throw new InvalidActionException("无效的角色职业: " + input);
    }

    private String interpretNode(MapNode node) {
        return switch (node.getType()) {
            case "BATTLE" -> "battle";
            case "BOSS" -> "boss_battle";
            case "REST" -> "rest";
            case "TREASURE" -> "treasure";
            case "SHOP" -> "shop";
            case "RANDOM" -> "random";
            case "BONFIRE" -> "bonfire";
            case "EMPEROR" -> "emperor";
            default -> "unknown";
        };
    }

    private Map<String, Object> newGameResponse(GameSession session, String sessionId) {
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("stateVersion", session.getStateVersion());
        result.put("success", true);
        result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
        result.put("map", session.getMap());
        result.put("currentNode", session.getCurrentNode());
        return result;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new InvalidActionException("未登录，请先获取游客token");
        }
        return auth.getName();
    }
}
