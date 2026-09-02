package com.xiyouji.dto.request;

import jakarta.validation.constraints.Min;

/**
 * 选择卡牌奖励请求DTO
 */
public class ChooseCardRequest {

    @Min(value = 0, message = "卡牌索引不能为负数")
    private int cardIndex;

    public ChooseCardRequest() {
    }

    public int getCardIndex() {
        return cardIndex;
    }

    public void setCardIndex(int cardIndex) {
        this.cardIndex = cardIndex;
    }
}
