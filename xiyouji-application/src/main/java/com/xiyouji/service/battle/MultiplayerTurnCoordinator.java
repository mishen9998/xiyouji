package com.xiyouji.service.battle;

import com.xiyouji.constants.GameConstants;
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
     * 1. 中毒伤害 2. 敌人按意图行动 3. 重置敌人格挡 4. tick敌人buff
     * 5. 检查玩家死亡 6. 选择新意图和目标
     */
    public void executeEnemyTurn(MultiplayerBattleState state) {
        state.setPlayerTurn(false);
        Enemy enemy = state.getEnemy();

        state.addLog("--- 敌人回合 ---");

        // 1. 中毒伤害
        Integer poison = enemy.getBuffs().get(BuffType.POISON);
        if (poison != null && poison > 0) {
            int poisonDmg = poison;
            enemy.takeDamage(poisonDmg);
            state.addLog(enemy.getName() + " 受到中毒伤害 " + poisonDmg);
            if (enemy.isDead()) {
                rewardService.settleVictory(state);
                return;
            }
        }

        // 2. 敌人按意图行动
        EnemyIntent intent = enemy.getIntent();
        int intentValue = enemy.getIntentValue();
        MultiplayerPlayer target = state.getPlayers().get(state.getTargetPlayerIndex());

        if (target != null && target.isAlive()) {
            switch (intent) {
                case ATTACK -> {
                    int actualDmg = target.getCharacter().takeDamage(intentValue);
                    state.addLog(enemy.getName() + " 攻击 " + target.getUsername() + "，造成 " + actualDmg + " 伤害");
                    if (target.getCharacter().isDead()) {
                        target.setAlive(false);
                        state.addLog(target.getUsername() + " 阵亡了！");
                    }
                }
                case DEFEND -> {
                    enemy.gainBlock(intentValue);
                    state.addLog(enemy.getName() + " 进入防御姿态，获得 " + intentValue + " 格挡");
                }
                case BUFF -> {
                    enemy.addBuff(BuffType.STRENGTH, intentValue);
                    state.addLog(enemy.getName() + " 蓄力，力量+" + intentValue);
                }
                default -> state.addLog(enemy.getName() + " 在观望");
            }
        }

        // 3. 重置敌人格挡
        enemy.resetBlock();

        // 4. tick敌人buff（减少debuff回合数）
        enemy.tickBuffs();

        // 5. 检查所有玩家是否阵亡
        if (state.alivePlayerCount() == 0) {
            state.setBattleOver(true);
            state.setVictory(false);
            state.addLog("全军覆没...战斗失败");
            log.info("Battle lost: room={}", state.getRoomCode());
            return;
        }

        // 6. 选择新意图和攻击目标
        enemy.chooseIntent();
        state.setTargetPlayerIndex(state.randomAlivePlayerIndex(random));
        MultiplayerPlayer newTarget = state.getPlayers().get(state.getTargetPlayerIndex());
        state.addLog(enemy.getName() + " 意图攻击: " + (newTarget != null ? newTarget.getUsername() : "?"));
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
                gc.tickBuffs();   // 减少debuff回合数

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
    }
}