package com.xiyouji.service;

import com.xiyouji.service.battle.SoloBattleInfoAssembler;
import com.xiyouji.service.battle.SoloBattleStarter;
import com.xiyouji.service.battle.SoloCardPlayHandler;
import com.xiyouji.service.battle.SoloRewardService;
import com.xiyouji.service.battle.SoloTurnCoordinator;
import com.xiyouji.service.session.BattleState;
import com.xiyouji.service.session.GameSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 战斗服务（门面）- 管理单人战斗流程
 *
 * 统一持有会话锁 / 归属校验 / 命令结算模板，并委托给内聚组件：
 *   - SoloBattleStarter       开场构建（敌人/难度/遗物战斗开始效果）
 *   - SoloCardPlayHandler     出牌结算与被动加成
 *   - SoloTurnCoordinator     回合流转（结束回合/TURN_START 遗物）
 *   - SoloRewardService       胜利结算与奖励领取
 *   - SoloBattleInfoAssembler 状态转 Map（响应）
 *   - SoloRelicTriggers       TURN_START 效果解析（首回合与每轮共享）
 *
 * 公开 API 契约不变（Controller 依赖），前端零改动。
 */
@Service
public class BattleService {

    private final GameService gameService;
    private final SoloBattleStarter starter;
    private final SoloCardPlayHandler cardPlayHandler;
    private final SoloTurnCoordinator turnCoordinator;
    private final SoloRewardService rewardService;
    private final SoloBattleInfoAssembler infoAssembler;

    public BattleService(GameService gameService,
                         SoloBattleStarter starter,
                         SoloCardPlayHandler cardPlayHandler,
                         SoloTurnCoordinator turnCoordinator,
                         SoloRewardService rewardService,
                         SoloBattleInfoAssembler infoAssembler) {
        this.gameService = gameService;
        this.starter = starter;
        this.cardPlayHandler = cardPlayHandler;
        this.turnCoordinator = turnCoordinator;
        this.rewardService = rewardService;
        this.infoAssembler = infoAssembler;
    }

    /** 开始战斗 */
    @Transactional
    public BattleState startBattle(String sessionId) {
        return starter.start(sessionId);
    }

    @Transactional
    public BattleState startBattle(String sessionId, long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            return starter.start(sessionId);
        });
    }

    /** 打出卡牌 — 加成通过参数传入，不修改卡牌本身 */
    @Transactional
    public BattleState playCard(String sessionId, int handIndex) {
        return cardPlayHandler.play(sessionId, handIndex);
    }

    public BattleState playCard(String sessionId, int handIndex, long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            return cardPlayHandler.play(sessionId, handIndex);
        });
    }

    /** 结束回合 */
    @Transactional
    public BattleState endTurn(String sessionId) {
        return turnCoordinator.endTurn(sessionId);
    }

    public BattleState endTurn(String sessionId, long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            return turnCoordinator.endTurn(sessionId);
        });
    }

    /** Executes a card command and resolves rewards while retaining the session lock. */
    @Transactional
    public Map<String, Object> playCardAndResolve(String sessionId, int handIndex,
                                                   long expectedVersion, String userId) {
        return resolveUnderLock(sessionId, expectedVersion, userId,
                () -> cardPlayHandler.play(sessionId, handIndex));
    }

    /** Executes a turn command and resolves rewards while retaining the session lock. */
    @Transactional
    public Map<String, Object> endTurnAndResolve(String sessionId, long expectedVersion, String userId) {
        return resolveUnderLock(sessionId, expectedVersion, userId,
                () -> turnCoordinator.endTurn(sessionId));
    }

    /** 战斗结束处理 */
    @Transactional
    public Map<String, Object> handleBattleEnd(String sessionId) {
        return gameService.withSessionLock(sessionId, () -> rewardService.resolveBattleEnd(sessionId));
    }

    /** Chooses one reward card under the owner/session lock. */
    @Transactional
    public Map<String, Object> chooseCardReward(String sessionId, int cardIndex,
                                                long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            return rewardService.chooseCardReward(sessionId, cardIndex);
        });
    }

    @Transactional
    public Map<String, Object> skipReward(String sessionId, long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            return rewardService.skipReward(sessionId);
        });
    }

    /** 获取战斗状态（JSON友好格式） */
    public Map<String, Object> getBattleInfo(String sessionId) {
        GameSession session = gameService.getSession(sessionId);
        return infoAssembler.toBattleInfo(session);
    }

    /** 命令+奖励结算模板：执行动作，若战斗已结束则在同一会话锁内结算并附带奖励。 */
    private Map<String, Object> resolveUnderLock(String sessionId, long expectedVersion, String userId,
                                                 Runnable action) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            action.run();
            GameSession session = gameService.getSession(sessionId);
            if (session.getBattle() != null && session.getBattle().isBattleOver()) {
                // Resolve and save this exact aggregate. Redis GET returns detached copies;
                // a nested GET/save followed by saving this older copy would conflict.
                Map<String, Object> rewards = rewardService.resolveBattleEnd(session);
                Map<String, Object> info = infoAssembler.toBattleInfo(session);
                info.put("rewards", rewards);
                return info;
            }
            return infoAssembler.toBattleInfo(session);
        });
    }
}
