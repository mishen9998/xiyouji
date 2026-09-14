package com.xiyouji.service.battle;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.Relic;
import com.xiyouji.service.GameService;
import com.xiyouji.service.session.BattleState;
import com.xiyouji.service.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 单人战斗奖励组件
 *
 * 负责：战斗结束结算（金币/卡牌/遗物奖励与 ON_KILL 效果）、
 * 奖励信息组装、卡牌奖励选择与跳过（含防重复领取语义）。
 */
@Component
public class SoloRewardService {

    private static final Logger log = LoggerFactory.getLogger(SoloRewardService.class);

    private final GameService gameService;

    public SoloRewardService(GameService gameService) {
        this.gameService = gameService;
    }

    /** 战斗结束处理（调用方须持有会话锁） */
    public Map<String, Object> resolveBattleEnd(String sessionId) {
        GameSession session = gameService.getSession(sessionId);
        BattleState battle = session.getBattle();
        if (battle == null || !battle.isBattleOver()) {
            throw new InvalidActionException("战斗未结束");
        }
        // 防止重复领取奖励
        if (battle.isRewardsHandled()) {
            return rewardInfo(battle);
        }
        battle.setRewardsHandled(true);

        Map<String, Object> result = new HashMap<>();
        GameCharacter player = session.getPlayer();

        if (battle.isVictory()) {
            // 金币奖励：基础 + 层数加成 + Boss额外
            int goldReward = GameConstants.BASE_GOLD_REWARD
                    + session.getCurrentLayer() * GameConstants.GOLD_PER_LAYER;
            if (battle.getEnemy().isBoss()) goldReward += GameConstants.BOSS_GOLD_BONUS;
            // ★ 太宗玉玺：战斗金币奖励翻倍
            if (player.getRelics().stream().anyMatch(r -> GameConstants.RELIC_EMPEROR_JADE_SEAL.equals(r.getName()))) {
                goldReward *= 2;
            }
            player.setGold(player.getGold() + goldReward);
            result.put("victory", true);
            result.put("goldReward", goldReward);

            // 卡牌奖励：战斗必掉 CARD_REWARD_COUNT 张选1
            List<Card> rewards = gameService.getCardRewards(sessionId, GameConstants.CARD_REWARD_COUNT);
            battle.setCardRewards(rewards);
            result.put("cardRewards", rewards);

            // 宝物掉落：RELIC_DROP_RATE概率掉落遗物（Boss必掉）
            boolean dropRelic = battle.getEnemy().isBoss()
                    || new Random().nextDouble() < GameConstants.RELIC_DROP_RATE;
            if (dropRelic) {
                Relic relic = gameService.getRandomRelic(sessionId);
                if (relic != null) {
                    player.getRelics().add(relic);
                    result.put("relicReward", relic);
                }
            }

            log.info("战斗胜利: sessionId={}, goldReward={}, dropRelic={}",
                    sessionId, goldReward, dropRelic);

            // ★ 宝物击杀效果：炼妖壶（ON_KILL;MAX_HP:N）— 每击杀敌人最大生命+N
            for (Relic relic : player.getRelics()) {
                if (relic.getEffect() != null && relic.getEffect().contains("ON_KILL")) {
                    String[] parts = relic.getEffect().split(";");
                    for (String p : parts) {
                        if (p.startsWith("MAX_HP:")) {
                            int hpBonus = Integer.parseInt(p.substring(7));
                            player.setMaxHp(player.getMaxHp() + hpBonus);
                            player.heal(hpBonus);
                            result.merge("onKillMsg", relic.getName() + "触发！最大生命+" + hpBonus, (a, b) -> a + "; " + b);
                        }
                    }
                }
            }
        } else {
            result.put("victory", false);
            result.put("message", "你已被击败，请重新开始！");
            log.info("战斗失败: sessionId={}", sessionId);
        }

        battle.setRewardSummary(new HashMap<>(result));
        gameService.saveSession(session);
        return rewardInfo(battle);
    }

    private Map<String, Object> rewardInfo(BattleState battle) {
        Map<String, Object> result = new HashMap<>(battle.getRewardSummary());
        result.put("cardRewards", battle.getCardRewards() == null ? List.of() : battle.getCardRewards());
        result.put("resolved", battle.isRewardsHandled() && battle.getCardRewards() == null);
        return result;
    }

    /** 选择奖励卡牌（调用方须持有会话锁并完成归属校验） */
    public Map<String, Object> chooseCardReward(String sessionId, int cardIndex) {
        GameSession session = gameService.getSession(sessionId);
        BattleState battle = session.getBattle();
        if (battle == null || !battle.isVictory() || !battle.isRewardsHandled()) {
            throw new InvalidActionException("当前没有战斗胜利奖励");
        }
        if (battle.getCardRewards() != null && (cardIndex < 0 || cardIndex >= battle.getCardRewards().size())) {
            throw new InvalidActionException("无效的卡牌选择");
        }
        Map<String, Object> result = new HashMap<>();
        if (battle != null && battle.getCardRewards() != null
                && cardIndex >= 0 && cardIndex < battle.getCardRewards().size()) {
            Card selected = battle.getCardRewards().get(cardIndex);
            session.getPlayer().addCard(selected.copy());
            result.put("chosenCard", selected.getName());
            // Consuming the reward list makes a successful command safe even if
            // an old client retries with a different idempotency key.
            battle.setCardRewards(null);
        }
        result.put("success", true);
        result.put("stateVersion", session.getStateVersion() + 1);
        result.put("player", session.getPlayer());
        gameService.saveSession(session);
        result.put("stateVersion", session.getStateVersion());
        return result;
    }

    /** 跳过卡牌奖励（调用方须持有会话锁并完成归属校验） */
    public Map<String, Object> skipReward(String sessionId) {
        GameSession session = gameService.getSession(sessionId);
        if (session.getBattle() == null || !session.getBattle().isVictory() || !session.getBattle().isRewardsHandled()) {
            throw new InvalidActionException("当前没有战斗胜利奖励");
        }
        session.getBattle().setCardRewards(null);
        gameService.saveSession(session);
        return Map.of("success", true, "stateVersion", session.getStateVersion());
    }
}