package com.xiyouji.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 移动请求DTO
 */
public class MoveRequest {

    @NotBlank(message = "节点ID不能为空")
    private String nodeId;

    public MoveRequest() {
    }

    public MoveRequest(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}
