package com.xiyouji.dto.response;

import java.util.List;

/**
 * 战斗奖励DTO
 */
public class BattleRewardDTO {

    private boolean victory;
    private int goldReward;
    private List<CardDTO> cardRewards;
    private RelicDTO relicReward;
    private String message;

    public BattleRewardDTO() {
    }

    public boolean isVictory() {
        return victory;
    }

    public void setVictory(boolean victory) {
        this.victory = victory;
    }

    public int getGoldReward() {
        return goldReward;
    }

    public void setGoldReward(int goldReward) {
        this.goldReward = goldReward;
    }

    public List<CardDTO> getCardRewards() {
        return cardRewards;
    }

    public void setCardRewards(List<CardDTO> cardRewards) {
        this.cardRewards = cardRewards;
    }

    public RelicDTO getRelicReward() {
        return relicReward;
    }

    public void setRelicReward(RelicDTO relicReward) {
        this.relicReward = relicReward;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
