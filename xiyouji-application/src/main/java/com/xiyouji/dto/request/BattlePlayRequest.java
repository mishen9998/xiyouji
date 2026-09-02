package com.xiyouji.dto.request;

import jakarta.validation.constraints.Min;

/**
 * 战斗出牌请求DTO
 */
public class BattlePlayRequest {

    @Min(value = 0, message = "手牌索引不能为负数")
    private int handIndex;

    public BattlePlayRequest() {
    }

    public int getHandIndex() {
        return handIndex;
    }

    public void setHandIndex(int handIndex) {
        this.handIndex = handIndex;
    }
}
