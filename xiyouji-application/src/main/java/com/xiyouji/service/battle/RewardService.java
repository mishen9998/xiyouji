package com.xiyouji.service.battle;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.service.CardRewardSampler;
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
 * 战斗奖励服务
 * 负责胜利结算（胜利状态 + 卡牌奖励生成）与奖励领取/跳过，
 * 直接操作 MultiplayerBattleState 对象，不触碰存储与广播（由门面统一处理）。
 */
@Service
public class RewardService {

    private static final Logger log = LoggerFactory.getLogger(RewardService.class);

    private final CardRepositoryPort cardRepo;
    private final SecureRandom random = new SecureRandom();

    public RewardService(CardRepositoryPort cardRepo) {
        this.cardRepo = cardRepo;
    }

    /**
     * 战斗胜利的统一结算入口：置胜利状态并生成奖励。
     * 供出牌击杀敌人与敌人回合中毒击杀两条路径复用。
     */
    public void settleVictory(MultiplayerBattleState state) {
        state.setBattleOver(true);
        state.setVictory(true);
        state.setPlayerTurn(false);
        state.addLog("击败了 " + state.getEnemy().getName() + "！胜利！");
        generateRewards(state);
        log.info("Battle won: room={}", state.getRoomCode());
    }

    /**
     * 战斗胜利后为每个存活玩家生成5张随机卡牌奖励
     * 奖励数量 = 存活玩家数（每人5选1）
     */
    public void generateRewards(MultiplayerBattleState state) {
        Map<String, List<Card>> rewards = new LinkedHashMap<>();
        for (MultiplayerPlayer p : state.getPlayers()) {
            if (p.isAlive()) {
                rewards.put(p.getUserId(), generateRewardCards(p.getCharacter().getCharacterClass()));
            }
        }
        state.setRewards(rewards);
        state.setRewardsPhase(true);
        state.setRewardsHandled(false);
        state.addLog("战斗胜利！每位存活玩家可从5张卡牌中选择1张");
        log.info("Rewards generated: room={}, alivePlayers={}", state.getRoomCode(), rewards.size());
    }

    /** 为指定角色生成5张随机卡牌（非基础卡，优先该职业+通用） */
    private List<Card> generateRewardCards(CharacterClass charClass) {
        return CardRewardSampler.draw(cardRepo.findByCharacterClassOrCharacterClassIsNull(charClass),
                charClass, GameConstants.CARD_REWARD_COUNT, random);
    }

    /** 玩家领取奖励（从5张中选1张加入牌组），不含 save/broadcast */
    public void claim(MultiplayerBattleState state, String userId, String cardName) {
        resolve(state, userId, cardName, false);
    }

    /** 玩家跳过奖励，不含 save/broadcast */
    public void skip(MultiplayerBattleState state, String userId) {
        resolve(state, userId, null, true);
    }

    private void resolve(MultiplayerBattleState state, String userId, String cardName, boolean skip) {
        if (!state.isRewardsPhase()) {
            throw new InvalidActionException("当前不在领奖阶段");
        }
        if (state.getClaimedRewards().containsKey(userId)) {
            throw new InvalidActionException("你已经领取过奖励了");
        }
        List<Card> options = state.getRewards().get(userId);
        if (options == null || (!skip && options.isEmpty())) {
            throw new InvalidActionException("你没有可领取的奖励");
        }

        // 找到选择的卡牌
        Card chosen = skip ? null : options.stream()
                .filter(c -> c.getName().equals(cardName))
                .findFirst()
                .orElseThrow(() -> new InvalidActionException("无效的卡牌选择: " + cardName));

        // 加入玩家牌组
        MultiplayerPlayer player = state.findPlayer(userId);
        if (player == null) {
            throw new InvalidActionException("你不在该战斗中");
        }
        if (chosen != null) player.getCharacter().addCard(chosen.copy());

        state.getClaimedRewards().put(userId, skip ? "__SKIPPED__" : cardName);
        state.addLog(player.getUsername() + (skip ? " 跳过了卡牌奖励" : " 选择了卡牌: " + cardName));

        // 检查是否所有人都领取完毕
        boolean allClaimed = state.getRewards().keySet().stream()
                .allMatch(uid -> state.getClaimedRewards().containsKey(uid));
        if (allClaimed) {
            state.setRewardsHandled(true);
            state.addLog("所有玩家已处理奖励，房主可进入下一层");
        }
    }
}