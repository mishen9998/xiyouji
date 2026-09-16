package com.xiyouji.service.battle;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.combat.CombatDelta;
import com.xiyouji.combat.EnemyCombat;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Enemy;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.enums.BuffType;
import com.xiyouji.model.enums.EnemyIntent;
import com.xiyouji.service.room.MultiplayerBattleState;
import com.xiyouji.service.room.MultiplayerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多人战斗回合协调器
 * 负责玩家结束回合、敌人回合、新一轮玩家回合的状态流转，
 * 直接操作 MultiplayerBattleState 对象，不触碰存储与广播（由门面统一处理）。
 */
@Service
public class MultiplayerTurnCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MultiplayerTurnCoordinator.class);

    private final RewardService rewardService;
    private final SecureRandom random = new SecureRandom();

    public MultiplayerTurnCoordinator(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    /**
     * 玩家结束自己的回合。
     * 所有存活玩家都结束后执行敌人回合，战斗未结束则开始新一轮玩家回合。
     */
    public void endTurn(MultiplayerBattleState state, String userId) {
        MultiplayerPlayer player = state.findPlayer(userId);
        if (player == null) {
            throw new InvalidActionException("你不在该战斗中");
        }
        if (!player.isAlive()) {
            throw new InvalidActionException("你已经阵亡");
        }
        if (!state.isPlayerTurn() || state.isBattleOver()) {
            throw new InvalidActionException("当前无法结束回合");
        }
        if (player.isEndedTurn()) {
            throw new InvalidActionException("你已经结束了本回合");
        }

        // 标记该玩家结束回合，弃掉手牌
        player.setEndedTurn(true);
        player.getCharacter().endTurn();
        state.addLog(player.getUsername() + " 结束了回合");

        // 检查是否所有存活玩家都已结束
        if (state.allAlivePlayersEndedTurn()) {
            executeEnemyTurn(state);

            // 如果战斗未结束，开始新一轮玩家回合
            if (!state.isBattleOver()) {
                startPlayerTurn(state);
            }
        }
    }

    /**
     * 执行敌人回合
     * 消费玩家阶段开始前锁定的行动。领域解析器统一处理状态生命周期与胜负。
     */
    public void executeEnemyTurn(MultiplayerBattleState state) {
        state.setPlayerTurn(false);
        Enemy enemy = state.getEnemy();

        state.addLog("--- 敌人回合 ---");

        if (enemy.getLockedAction() == null) {
            int index = state.getTargetPlayerIndex();
            List<String> targets = index >= 0 && index < state.getPlayers().size()
                    ? List.of(state.getPlayers().get(index).getUserId()) : List.of();
            EnemyCombat.restoreLegacyForecast(enemy, targets);
        }
        Map<String, GameCharacter> players = new LinkedHashMap<>();
        state.getPlayers().stream().filter(MultiplayerPlayer::isAlive)
                .forEach(p -> players.put(p.getUserId(), p.getCharacter()));
        CombatDelta delta = EnemyCombat.execute(enemy, players);
        if (delta.enemyPoisonDamage() > 0) state.addLog(enemy.getName() + " 受到中毒伤害 " + delta.enemyPoisonDamage());
        for (CombatDelta.Hit hit : delta.hits()) {
            MultiplayerPlayer target = state.findPlayer(hit.targetUserId());
            state.addLog(enemy.getName() + " 攻击 " + target.getUsername() + "，造成 " + hit.hpLost() + " 伤害");
        }
        for (MultiplayerPlayer player : state.getPlayers()) {
            if (player.isAlive() && player.getCharacter().isDead()) {
                player.setAlive(false);
                state.addLog(player.getUsername() + " 阵亡了！");
            }
        }
        if (delta.outcome() == CombatDelta.Outcome.VICTORY) {
            rewardService.settleVictory(state);
            return;
        }
        if (enemy.getLockedAction().block() > 0) state.addLog(enemy.getName() + " 获得 " + enemy.getLockedAction().block() + " 格挡");
        if (enemy.getLockedAction().strengthGain() > 0) state.addLog(enemy.getName() + " 力量+" + enemy.getLockedAction().strengthGain());
        if (!enemy.getLockedAction().statusEffects().isEmpty()) state.addLog(enemy.getName() + " 施加 " + enemy.getLockedAction().statusEffects());
        if (delta.outcome() == CombatDelta.Outcome.DEFEAT) {
            state.setBattleOver(true);
            state.setVictory(false);
            state.addLog("全军覆没...战斗失败");
            log.info("Battle lost: room={}", state.getRoomCode());
            return;
        }

    }

    /**
     * 开始新一轮玩家回合
     * 为每个存活玩家：回能量、重置格挡、抽牌、tick buff
     */
    public void startPlayerTurn(MultiplayerBattleState state) {
        state.setPlayerTurn(true);
        state.setTurnNumber(state.getTurnNumber() + 1);

        for (MultiplayerPlayer player : state.getPlayers()) {
            if (player.isAlive()) {
                GameCharacter gc = player.getCharacter();
                gc.startTurn();   // 回能量、重置格挡、抽drawNextTurn张牌
                gc.drawCards(GameConstants.INITIAL_HAND_SIZE);  // 抽5张新手牌

                // 检查中毒死亡
                if (gc.isDead()) {
                    player.setAlive(false);
                    state.addLog(player.getUsername() + " 因中毒阵亡！");
                }
                player.setEndedTurn(false);
            }
        }

        // 如果全员因中毒死亡
        if (state.alivePlayerCount() == 0) {
            state.setBattleOver(true);
            state.setVictory(false);
            state.addLog("全军覆没...战斗失败");
            return;
        }

        state.addLog("--- 第 " + state.getTurnNumber() + " 回合 ---");
        lockNextAction(state, random.nextLong());
    }

    /** Shared by battle start and every subsequent player phase. */
    public static void lockNextAction(MultiplayerBattleState state, long seed) {
        var action = EnemyCombat.lockNextAction(state.getEnemy(), state.getPlayers().stream()
                .filter(p -> p.isAlive() && !p.getCharacter().isDead()).map(MultiplayerPlayer::getUserId).toList(), seed);
        if (!action.targetUserIds().isEmpty()) state.setTargetPlayerIndex(state.indexOfPlayer(action.targetUserIds().get(0)));
    }
}
