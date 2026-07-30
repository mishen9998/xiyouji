package com.xiyouji.dto.request.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 加入房间请求
 */
public class JoinRoomRequest {

    @NotBlank(message = "房间码不能为空")
    @Size(min = 8, max = 8, message = "房间码必须为8位")
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
