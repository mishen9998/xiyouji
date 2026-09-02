package com.xiyouji.dto.request;

import jakarta.validation.constraints.Min;

/**
 * 移除卡牌请求DTO
 */
public class RemoveCardRequest {

    @Min(value = 0, message = "卡牌索引不能为负数")
    private int index;

    public RemoveCardRequest() {
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}
