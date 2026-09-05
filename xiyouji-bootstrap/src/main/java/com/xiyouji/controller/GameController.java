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
import com.xiyouji.service.CommandGuard;
import com.xiyouji.service.CommandIdempotencyService;
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
    private final PlayerSummaryAssembler playerSummaryAssembler;
    private final CommandIdempotencyService idempotency;

    public GameController(GameService gameService, PlayerSummaryAssembler playerSummaryAssembler,
                          CommandIdempotencyService idempotency) {
        this.gameService = gameService;
        this.playerSummaryAssembler = playerSummaryAssembler;
        this.idempotency = idempotency;
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
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed() && !previous.value().isBlank()) {
            GameSession existing = gameService.getSessionForUser(previous.value(), username);
            return newGameResponse(existing, previous.value());
        }

        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        GameSession session;
        try {
            session = gameService.newGame(sessionId, characterClass, username);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }

        Map<String, Object> result = newGameResponse(session, sessionId);
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
        log.info("New game created successfully, sessionId: {}", sessionId);
        return result;
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
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return Map.of("success", true);
        boolean ok;
        try {
            ok = gameService.deleteSession(sessionId, expectedVersion, username);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
        if (!ok) {
            log.warn("Session not found for deletion: {}", sessionId);
        }
        Map<String, Object> result = Map.of("success", ok);
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
        return result;
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
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) {
            GameSession existing = gameService.getSessionForUser(sessionId, username);
            return Map.of("node", existing.getCurrentNode(), "eventType", interpretNode(existing.getCurrentNode()),
                    "stateVersion", existing.getStateVersion());
        }
        MapNode node;
        try {
            node = gameService.moveToNode(sessionId, nodeId, expectedVersion, username);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("node", node);
        result.put("eventType", interpretNode(node));
        result.put("stateVersion", gameService.getSession(sessionId).getStateVersion());
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
        return result;
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
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) {
            GameSession existing = gameService.getSessionForUser(sessionId, username);
            return Map.of("success", true, "currentLayer", existing.getCurrentLayer(),
                    "maxLayer", existing.getMaxLayer(), "stateVersion", existing.getStateVersion());
        }
        boolean success;
        try {
            success = gameService.advanceToNextLayer(sessionId, expectedVersion, username);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
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
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
        return result;
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
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) {
            GameSession existing = gameService.getSessionForUser(sessionId, username);
            return Map.of("stateVersion", existing.getStateVersion(), "player",
                    playerSummaryAssembler.toPlayerSummary(existing.getPlayer()));
        }
        try {
            Map<String, Object> result = gameService.withSessionLock(sessionId,
                    () -> handleEventUnderLock(sessionId, request, expectedVersion, username));
            idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
            return result;
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
    }

    private Map<String, Object> handleEventUnderLock(String sessionId, EventRequest request,
                                                       long expectedVersion, String username) {
        String action = request.getAction() != null ? request.getAction() : "none";
        log.info("Handling event action '{}' for session: {}", action, sessionId);
        gameService.assertOwnerAndVersion(sessionId, username, expectedVersion);

        Map<String, Object> result = new HashMap<>();
        GameSession session = gameService.getSession(sessionId);
        boolean persistAfterEvent = false;
        MapNode node = session.getCurrentNode();
        if (node == null) {
            log.warn("No current node for session: {}", sessionId);
            result.put("error", "不在任何节点");
            return result;
        }

        switch (node.getType()) {
            case "REST" -> {
                if ("rest".equals(action)) {
                    gameService.heal(sessionId, session.getPlayer().getMaxHp() / 3);
                    // Redis-backed service calls deserialize their own copy;
                    // reload so the response is based on the persisted state
                    // and never writes an older snapshot back over it.
                    session = gameService.getSession(sessionId);
                    result.put("message", "休息完毕，生命值已恢复");
                    result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
                }
            }
            case "BONFIRE" -> {
                if ("upgrade".equals(action)) {
                    if (session.getBonfireUpgradesLeft() <= 0) {
                        result.put("error", "升级次数已用完");
                        result.put("bonfireUpgradesLeft", 0);
                        result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
                        break;
                    }
                    int cardIdx = request.getCardIndex() != null ? request.getCardIndex() : -1;
                    if (cardIdx >= 0) {
                        gameService.upgradeCard(sessionId, cardIdx);
                        session = gameService.getSession(sessionId);
                        Card upgraded = session.getPlayer().getDeck().get(cardIdx);
                        session.setBonfireUpgradesLeft(session.getBonfireUpgradesLeft() - 1);
                        // upgradeCard persists the card mutation itself; the
                        // remaining bonfire counter must be persisted by the
                        // outer command as part of the same locked transition.
                        persistAfterEvent = true;
                        result.put("upgraded", upgraded);
                    }
                }
                result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
                result.put("bonfireUpgradesLeft", session.getBonfireUpgradesLeft());
            }
            case "TREASURE" -> {
                Relic relic = gameService.getRandomRelic(sessionId);
                if (relic != null) {
                    session.getPlayer().getRelics().add(relic);
                    persistAfterEvent = true;
                    result.put("relic", relic);
                    result.put("message", "获得遗物: " + relic.getName());
                    log.info("Relic '{}' obtained for session: {}", relic.getName(), sessionId);
                }
            }
            case "EMPEROR" -> {
                // 唐朝皇帝赐宝：第一次进入返回三选一候选；选择后入库
                if ("choose".equals(action)) {
                    String relicName = request.getRelicName();
                    if (relicName == null || relicName.isBlank()) {
                        log.warn("Missing relicName for emperor choose, session: {}", sessionId);
                        throw new InvalidActionException("选择皇帝宝物时必须提供relicName");
                    }
                    Relic chosen = gameService.chooseEmperorRelic(sessionId, relicName);
                    if (chosen != null) {
                        result.put("relic", chosen);
                        result.put("message", "唐太宗李世民赐予你: " + chosen.getName() + "！");
                        result.put("player", playerSummaryAssembler.toPlayerSummary(gameService.getSession(sessionId).getPlayer()));
                        log.info("Emperor relic chosen: {} for session: {}", relicName, sessionId);
                    } else {
                        result.put("error", "无效或已拥有的宝物: " + relicName);
                    }
                } else {
                    // 默认动作：获取3件候选宝物
                    List<Relic> choices = gameService.getEmperorChoices(sessionId);
                    result.put("choices", choices);
                    result.put("message", "唐太宗李世民设宴相送，请从三件御赐宝物中选择一件：");
                    log.info("Emperor choices offered for session: {}", sessionId);
                }
            }
            case "SHOP" -> {
                if ("buy".equals(action)) {
                    Long cardId = request.getCardId();
                    if (cardId == null) {
                        log.warn("Missing cardId for shop purchase, session: {}", sessionId);
                        throw new InvalidActionException("购买卡牌时必须提供cardId");
                    }
                    int price = request.getPrice() != null ? request.getPrice() : 50;
                    boolean bought = gameService.buyCard(sessionId, cardId, price);
                    session = gameService.getSession(sessionId);
                    result.put("bought", bought);
                    result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
                    log.info("Shop purchase cardId={}, price={}, bought={} for session: {}",
                            cardId, price, bought, sessionId);
                } else {
                    List<Card> shopCards = gameService.getShopCards(sessionId);
                    result.put("shopCards", shopCards);
                }
            }
            case "RANDOM" -> {
                result.put("message", randomEvent(session));
                result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
                // randomEvent mutates gold/HP/relics directly and therefore
                // needs an explicit save before the command completes.
                persistAfterEvent = true;
            }
        }

        if (persistAfterEvent) {
            gameService.saveSession(session);
        }

        result.put("stateVersion", gameService.getSession(sessionId).getStateVersion());

        return result;
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
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) {
            GameSession existing = gameService.getSessionForUser(sessionId, username);
            return Map.of("success", true, "stateVersion", existing.getStateVersion(),
                    "player", playerSummaryAssembler.toPlayerSummary(existing.getPlayer()));
        }
        try {
            gameService.removeCardFromDeck(sessionId, index, expectedVersion, username);
            GameSession session = gameService.getSessionForUser(sessionId, username);
            Map<String, Object> result = Map.of("success", true, "stateVersion", session.getStateVersion(),
                    "player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
            idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
            return result;
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
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

    private String randomEvent(GameSession session) {
        String[] events = {
            "你遇到了一位老神仙，他给了你一些指引。获得10金币。",
            "路边有棵人参果树，摘了一颗吃。回复8点生命值。",
            "遇到小妖怪打劫！失去10金币。",
            "发现了太上老君的丹炉遗迹，获得了一件遗物。",
            "山间的温泉让你神清气爽。回复5点生命值。"
        };

        Random r = new Random();
        String event = events[r.nextInt(events.length)];

        // 简单效果
        if (event.contains("10金币")) session.getPlayer().setGold(session.getPlayer().getGold() + 10);
        if (event.contains("8点生命")) session.getPlayer().heal(8);
        if (event.contains("10金币") && event.contains("失去"))
            session.getPlayer().setGold(Math.max(0, session.getPlayer().getGold() - 10));
        if (event.contains("5点生命")) session.getPlayer().heal(5);
        if (event.contains("遗物")) {
            Relic relic = gameService.getRandomRelic(session.getSessionId());
            if (relic != null) session.getPlayer().getRelics().add(relic);
        }

        return event;
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
