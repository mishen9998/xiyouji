package com.xiyouji.controller;

import com.xiyouji.dto.PlayerSummaryAssembler;
import com.xiyouji.dto.request.BattlePlayRequest;
import com.xiyouji.dto.request.ChooseCardRequest;
import com.xiyouji.model.Card;
import com.xiyouji.service.BattleService;
import com.xiyouji.service.GameService;
import com.xiyouji.service.session.BattleState;
import com.xiyouji.service.session.GameSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

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

    public BattleController(BattleService battleService, GameService gameService, PlayerSummaryAssembler playerSummaryAssembler) {
        this.battleService = battleService;
        this.gameService = gameService;
        this.playerSummaryAssembler = playerSummaryAssembler;
    }

    /** 开始战斗 */
    @PostMapping("/battle/start/{sessionId}")
    @Operation(summary = "开始战斗", description = "在当前节点开始一场战斗，初始化战斗状态并抽取手牌")
    public Map<String, Object> startBattle(@PathVariable String sessionId) {
        log.info("Starting battle for session: {}", sessionId);
        battleService.startBattle(sessionId);
        return battleService.getBattleInfo(sessionId);
    }

    /** 出牌 */
    @PostMapping("/battle/play/{sessionId}")
    @Operation(summary = "出牌", description = "在战斗中打出指定索引的手牌，若战斗结束则附带奖励信息")
    public Map<String, Object> playCard(@PathVariable String sessionId,
                                        @Valid @RequestBody BattlePlayRequest request) {
        int handIndex = request.getHandIndex();
        log.info("Playing card at handIndex {} for session: {}", handIndex, sessionId);
        battleService.playCard(sessionId, handIndex);

        GameSession session = gameService.getSession(sessionId);
        BattleState battle = session.getBattle();
        if (battle != null && battle.isBattleOver()) {
            log.info("Battle ended after playCard for session: {}", sessionId);
            Map<String, Object> reward = battleService.handleBattleEnd(sessionId);
            Map<String, Object> info = battleService.getBattleInfo(sessionId);
            info.put("rewards", reward);
            session.setMapOpen(true);
            return info;
        }
        return battleService.getBattleInfo(sessionId);
    }

    /** 结束回合 */
    @PostMapping("/battle/endturn/{sessionId}")
    @Operation(summary = "结束回合", description = "结束玩家回合并执行敌人行动，若战斗结束则附带奖励信息")
    public Map<String, Object> endTurn(@PathVariable String sessionId) {
        log.info("Ending turn for session: {}", sessionId);
        battleService.endTurn(sessionId);

        GameSession session = gameService.getSession(sessionId);
        BattleState battle = session.getBattle();
        if (battle != null && battle.isBattleOver()) {
            log.info("Battle ended after endTurn for session: {}", sessionId);
            Map<String, Object> reward = battleService.handleBattleEnd(sessionId);
            Map<String, Object> info = battleService.getBattleInfo(sessionId);
            info.put("rewards", reward);
            session.setMapOpen(true);
            return info;
        }
        return battleService.getBattleInfo(sessionId);
    }

    /** 获取战斗状态 */
    @GetMapping("/battle/state/{sessionId}")
    @Operation(summary = "获取战斗状态", description = "查询当前战斗的完整状态信息，包括玩家手牌、敌人意图等")
    public Map<String, Object> battleState(@PathVariable String sessionId) {
        log.debug("Fetching battle state for session: {}", sessionId);
        return battleService.getBattleInfo(sessionId);
    }

    /** 选择卡牌奖励 */
    @PostMapping("/reward/choose/{sessionId}")
    @Operation(summary = "选择卡牌奖励", description = "从战斗胜利后的卡牌奖励列表中选择一张加入牌组")
    public Map<String, Object> chooseCardReward(@PathVariable String sessionId,
                                                @Valid @RequestBody ChooseCardRequest request) {
        int cardIndex = request.getCardIndex();
        log.info("Choosing card reward at index {} for session: {}", cardIndex, sessionId);

        GameSession session = gameService.getSession(sessionId);
        BattleState battle = session.getBattle();
        Map<String, Object> result = new HashMap<>();

        if (battle != null && battle.getCardRewards() != null
                && cardIndex >= 0 && cardIndex < battle.getCardRewards().size()) {
            Card selected = battle.getCardRewards().get(cardIndex);
            session.getPlayer().addCard(selected.copy());
            result.put("chosenCard", selected.getName());
            log.info("Card '{}' added to deck for session: {}", selected.getName(), sessionId);
        } else {
            log.warn("Invalid card reward index {} for session: {}", cardIndex, sessionId);
        }
        result.put("success", true);
        result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
        return result;
    }

    /** 跳过奖励 */
    @PostMapping("/reward/skip/{sessionId}")
    @Operation(summary = "跳过奖励", description = "跳过战斗胜利后的卡牌奖励，不选择任何卡牌")
    public Map<String, Object> skipReward(@PathVariable String sessionId) {
        log.info("Skipping card reward for session: {}", sessionId);
        return Map.of("success", true);
    }
}
