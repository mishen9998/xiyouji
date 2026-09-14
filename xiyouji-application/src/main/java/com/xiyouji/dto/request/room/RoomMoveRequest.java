package com.xiyouji.dto.request.room;

import jakarta.validation.constraints.NotBlank;

/**
 * 房间移动节点请求 DTO
 */
public class RoomMoveRequest {

    @NotBlank(message = "节点ID不能为空")
    private String nodeId;

    public RoomMoveRequest() {
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}