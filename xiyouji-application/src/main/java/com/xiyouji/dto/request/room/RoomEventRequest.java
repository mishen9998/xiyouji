package com.xiyouji.dto.request.room;

/**
 * 房间节点事件请求 DTO
 *
 * 支持休息/篝火/宝箱/商店/随机事件，action 缺省为 none。
 */
public class RoomEventRequest {

    private String action;
    private Long cardId;
    private Integer cardIndex;

    public RoomEventRequest() {
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public Integer getCardIndex() {
        return cardIndex;
    }

    public void setCardIndex(Integer cardIndex) {
        this.cardIndex = cardIndex;
    }
}