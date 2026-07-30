package com.xiyouji.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 事件请求DTO
 * 用于处理休息、篝火升级、宝箱、商店、随机事件等节点交互
 */
public class EventRequest {

    /** 操作类型，如 rest、upgrade、buy 等 */
    @NotBlank(message = "操作类型不能为空")
    @Size(max = 32, message = "操作类型长度不能超过32个字符")
    private String action;

    /** 卡牌索引（篝火升级时使用，需 >= 0） */
    @Min(value = 0, message = "卡牌索引不能为负数")
    private Integer cardIndex;

    /** 卡牌ID（商店购买时使用，需为正数） */
    @Positive(message = "卡牌ID必须为正数")
    private Long cardId;

    /** 价格（商店购买时使用，需 >= 0） */
    @Min(value = 0, message = "价格不能为负数")
    private Integer price;

    /** 宝物名称（唐朝皇帝赐宝选择时使用） */
    @Size(max = 64, message = "宝物名称长度不能超过64个字符")
    private String relicName;

    public EventRequest() {
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getCardIndex() {
        return cardIndex;
    }

    public void setCardIndex(Integer cardIndex) {
        this.cardIndex = cardIndex;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public String getRelicName() {
        return relicName;
    }

    public void setRelicName(String relicName) {
        this.relicName = relicName;
    }
}
