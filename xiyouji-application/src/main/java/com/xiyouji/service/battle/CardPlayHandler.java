package com.xiyouji.service.battle;

import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.service.room.MultiplayerBattleState;
import com.xiyouji.service.room.MultiplayerPlayer;
import org.springframework.stereotype.Service;

/**
 * 出牌处理器（抢出牌机制）
 * 负责出牌前置校验与卡牌效果结算，直接操作 MultiplayerBattleState 对象，
 * 不触碰存储与广播（由门面统一处理）。
 */
@Service
public class CardPlayHandler {

    private final RewardService rewardService;

    public CardPlayHandler(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    /**
     * 执行一次出牌：玩家/回合/手牌/能量校验 → GameCharacter.playCard 结算 →
     * 敌人死亡则进入胜利结算。调用方已持有房间级分布式锁。
     */
    public void play(MultiplayerBattleState state, String userId, int handIndex) {
        MultiplayerPlayer player = state.findPlayer(userId);
        if (player == null) {
            throw new InvalidActionException("你不在该战斗中");
        }
        if (!player.isAlive()) {
            throw new InvalidActionException("你已经阵亡，无法出牌");
        }
        if (!state.isPlayerTurn() || state.isBattleOver()) {
            throw new InvalidActionException("当前不是你的回合或战斗已结束");
        }
        if (player.isEndedTurn()) {
            throw new InvalidActionException("你已经结束了本回合");
        }

        GameCharacter gc = player.getCharacter();
        if (handIndex < 0 || handIndex >= gc.getHand().size()) {
            throw new InvalidActionException("无效的手牌索引: " + handIndex);
        }

        Card card = gc.getHand().get(handIndex);
        if (gc.getEnergy() < card.getCost()) {
            throw new InvalidActionException("能量不足");
        }

        // 调用 GameCharacter.playCard 执行卡牌效果
        // 包含：扣能量、施加 debuff 到敌人、计算伤害（含力量/脆弱/虚弱）、格挡、抽牌、治疗等
        boolean success = gc.playCard(card, state.getEnemy());
        if (!success) {
            throw new InvalidActionException("出牌失败");
        }

        state.addLog(player.getUsername() + " 使用了 " + card.getName());

        // 检查敌人是否死亡
        if (state.getEnemy().isDead()) {
            rewardService.settleVictory(state);
        }
    }
}