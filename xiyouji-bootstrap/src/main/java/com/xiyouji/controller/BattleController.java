package com.xiyouji.controller;

import com.xiyouji.dto.PlayerSummaryAssembler;
import com.xiyouji.dto.request.BattlePlayRequest;
import com.xiyouji.dto.request.ChooseCardRequest;
import com.xiyouji.model.Card;
import com.xiyouji.service.BattleService;
import com.xiyouji.service.CommandGuard;
import com.xiyouji.service.CommandIdempotencyService;
import com.xiyouji.service.GameService;
import com.xiyouji.service.session.BattleState;
import com.xiyouji.service.session.GameSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

/**
 * 战斗系统控制器 - 处理战斗及奖励相关API
 */
@RestController
@RequestMapping("/api/game")
@Tag(name = "战斗系统", description = "战斗相关API")
public class BattleController {

    private static final Logger log = LoggerFactory.getLogger(BattleController.class);

    private final BattleService battleService;
    private final GameService gameService;
    private final PlayerSummaryAssembler playerSummaryAssembler;
    private final CommandIdempotencyService idempotency;

    public BattleController(BattleService battleService, GameService gameService,
                            PlayerSummaryAssembler playerSummaryAssembler,
                            CommandIdempotencyService idempotency) {
        this.battleService = battleService;
        this.gameService = gameService;
        this.playerSummaryAssembler = playerSummaryAssembler;
        this.idempotency = idempotency;
    }

    /** 开始战斗 */
    @PostMapping("/battle/start/{sessionId}")
    @Operation(summary = "开始战斗", description = "在当前节点开始一场战斗，初始化战斗状态并抽取手牌")
    public Map<String, Object> startBattle(@PathVariable String sessionId,
                                           @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                           @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        log.info("Starting battle for session: {}", sessionId);
        String user = currentUsername();
        String fingerprint = CommandGuard.fingerprint("POST", "/api/game/battle/start/" + sessionId, "");
        String scope = "game:battle:start:" + user + ":" + sessionId;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return battleService.getBattleInfo(sessionId);
        try {
            battleService.startBattle(sessionId, expectedVersion, user);
            Map<String, Object> result = battleService.getBattleInfo(sessionId);
            idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
            return result;
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
    }

    /** 出牌 */
    @PostMapping("/battle/play/{sessionId}")
    @Operation(summary = "出牌", description = "在战斗中打出指定索引的手牌，若战斗结束则附带奖励信息")
    public Map<String, Object> playCard(@PathVariable String sessionId,
                                        @Valid @RequestBody BattlePlayRequest request,
                                        @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                        @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        int handIndex = request.getHandIndex();
        log.info("Playing card at handIndex {} for session: {}", handIndex, sessionId);
        String user = currentUsername();
        String fingerprint = CommandGuard.fingerprint("POST", "/api/game/battle/play/" + sessionId,
                String.valueOf(handIndex));
        String scope = "game:battle:play:" + user + ":" + sessionId;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return battleService.getBattleInfo(sessionId);
        try {
            Map<String, Object> result = battleService.playCardAndResolve(sessionId, handIndex, expectedVersion, user);
            idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
            return result;
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
    }

    /** 结束回合 */
    @PostMapping("/battle/endturn/{sessionId}")
    @Operation(summary = "结束回合", description = "结束玩家回合并执行敌人行动，若战斗结束则附带奖励信息")
    public Map<String, Object> endTurn(@PathVariable String sessionId,
                                       @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                       @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        log.info("Ending turn for session: {}", sessionId);
        String user = currentUsername();
        String fingerprint = CommandGuard.fingerprint("POST", "/api/game/battle/endturn/" + sessionId, "");
        String scope = "game:battle:endturn:" + user + ":" + sessionId;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return battleService.getBattleInfo(sessionId);
        try {
            Map<String, Object> result = battleService.endTurnAndResolve(sessionId, expectedVersion, user);
            idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
            return result;
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
    }

    /** 获取战斗状态 */
    @GetMapping("/battle/state/{sessionId}")
    @Operation(summary = "获取战斗状态", description = "查询当前战斗的完整状态信息，包括玩家手牌、敌人意图等")
    public Map<String, Object> battleState(@PathVariable String sessionId) {
        log.debug("Fetching battle state for session: {}", sessionId);
        gameService.getSessionForUser(sessionId, currentUsername());
        return battleService.getBattleInfo(sessionId);
    }

    /** 选择卡牌奖励 */
    @PostMapping("/reward/choose/{sessionId}")
    @Operation(summary = "选择卡牌奖励", description = "从战斗胜利后的卡牌奖励列表中选择一张加入牌组")
    public Map<String, Object> chooseCardReward(@PathVariable String sessionId,
                                                @Valid @RequestBody ChooseCardRequest request,
                                                @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                                @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        int cardIndex = request.getCardIndex();
        log.info("Choosing card reward at index {} for session: {}", cardIndex, sessionId);

        String user = currentUsername();
        String fingerprint = CommandGuard.fingerprint("POST", "/api/game/reward/choose/" + sessionId,
                String.valueOf(cardIndex));
        String scope = "game:reward:choose:" + user + ":" + sessionId;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return battleService.getBattleInfo(sessionId);
        try {
            Map<String, Object> result = battleService.chooseCardReward(sessionId, cardIndex, expectedVersion, user);
            GameSession session = gameService.getSessionForUser(sessionId, user);
            result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
            idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
            return result;
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
    }

    /** 跳过奖励 */
    @PostMapping("/reward/skip/{sessionId}")
    @Operation(summary = "跳过奖励", description = "跳过战斗胜利后的卡牌奖励，不选择任何卡牌")
    public Map<String, Object> skipReward(@PathVariable String sessionId,
                                          @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                          @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        log.info("Skipping card reward for session: {}", sessionId);
        String user = currentUsername();
        String fingerprint = CommandGuard.fingerprint("POST", "/api/game/reward/skip/" + sessionId, "");
        String scope = "game:reward:skip:" + user + ":" + sessionId;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return battleService.getBattleInfo(sessionId);
        try {
            Map<String, Object> result = battleService.skipReward(sessionId, expectedVersion, user);
            idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
            return result;
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return authentication.getName();
    }
}
