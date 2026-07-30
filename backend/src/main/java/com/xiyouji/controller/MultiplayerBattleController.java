package com.xiyouji.controller;

import com.xiyouji.dto.request.BattlePlayRequest;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.service.MultiplayerBattleService;
import com.xiyouji.service.room.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 多人战斗控制器
 *
 * 提供多人协作战斗的REST API：
 *   POST /api/multiplayer/battle/{roomCode}/start    - 房主开始战斗
 *   POST /api/multiplayer/battle/{roomCode}/play     - 出牌（抢出牌机制）
 *   POST /api/multiplayer/battle/{roomCode}/endturn  - 结束自己的回合
 *   GET  /api/multiplayer/battle/{roomCode}/state    - 查询战斗状态
 *
 * 所有操作完成后通过WebSocket实时广播到 /topic/room/{roomCode}/battle
 */
@RestController
@RequestMapping("/api/multiplayer/battle")
@Validated
@Tag(name = "多人战斗系统", description = "5人PvE协作战斗，抢出牌机制")
public class MultiplayerBattleController {

    private static final Logger log = LoggerFactory.getLogger(MultiplayerBattleController.class);

    private final MultiplayerBattleService battleService;
    private final RoomService roomService;

    public MultiplayerBattleController(MultiplayerBattleService battleService,
                                       RoomService roomService) {
        this.battleService = battleService;
        this.roomService = roomService;
    }

    @PostMapping("/{roomCode}/start")
    @Operation(summary = "开始多人战斗", description = "房主发起，所有玩家已准备且选角后开始")
    public Map<String, Object> startBattle(@PathVariable String roomCode) {
        String userId = currentUserId();
        log.info("Start multiplayer battle: room={}, requester={}", roomCode, userId);
        battleService.startBattle(roomCode, userId);
        return battleService.getBattleInfo(roomCode);
    }

    @PostMapping("/{roomCode}/play")
    @Operation(summary = "出牌", description = "抢出牌机制：谁先请求谁先出，使用服务端锁保证原子性")
    public Map<String, Object> playCard(@PathVariable String roomCode,
                                        @Valid @RequestBody BattlePlayRequest request) {
        String userId = currentUserId();
        log.info("Play card: room={}, user={}, handIndex={}", roomCode, userId, request.getHandIndex());
        battleService.playCard(roomCode, userId, request.getHandIndex());
        return battleService.getBattleInfo(roomCode);
    }

    @PostMapping("/{roomCode}/endturn")
    @Operation(summary = "结束回合", description = "结束自己的回合，所有存活玩家都结束后敌人行动")
    public Map<String, Object> endTurn(@PathVariable String roomCode) {
        String userId = currentUserId();
        log.info("End turn: room={}, user={}", roomCode, userId);
        battleService.endTurn(roomCode, userId);
        return battleService.getBattleInfo(roomCode);
    }

    @GetMapping("/{roomCode}/state")
    @Operation(summary = "查询战斗状态", description = "获取当前战斗完整状态（含所有玩家手牌）")
    public Map<String, Object> getBattleState(@PathVariable String roomCode) {
        return battleService.getBattleInfo(roomCode);
    }

    @GetMapping("/{roomCode}/exists")
    @Operation(summary = "检查战斗是否存在")
    public boolean battleExists(@PathVariable String roomCode) {
        return roomService.roomExists(roomCode);
    }

    @PostMapping("/{roomCode}/claim-reward")
    @Operation(summary = "领取战斗奖励", description = "从3张卡牌中选1张加入牌组")
    public Map<String, Object> claimReward(@PathVariable String roomCode,
                                            @RequestBody Map<String, String> body) {
        String userId = currentUserId();
        String cardName = body.get("cardName");
        if (cardName == null || cardName.isBlank()) {
            throw new InvalidActionException("请选择一张卡牌");
        }
        log.info("Claim reward: room={}, user={}, card={}", roomCode, userId, cardName);
        battleService.claimReward(roomCode, userId, cardName);
        return battleService.getBattleInfo(roomCode);
    }

    @PostMapping("/{roomCode}/next-floor")
    @Operation(summary = "返回地图探索", description = "房主发起，战斗胜利后返回地图。Boss节点则进入下一层")
    public Map<String, Object> returnToMap(@PathVariable String roomCode) {
        String userId = currentUserId();
        log.info("Return to map: room={}, requester={}", roomCode, userId);
        return battleService.returnToMap(roomCode, userId);
    }

    // ===== 内部方法 =====

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new InvalidActionException("未登录，请先获取游客token");
        }
        return auth.getName();
    }
}
