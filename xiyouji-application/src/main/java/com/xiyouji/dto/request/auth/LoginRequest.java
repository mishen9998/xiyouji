package com.xiyouji.dto.request.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求DTO
 */
public class LoginRequest {

    /** 新版客户端使用 account 登录。 */
    private String account;

    /** 兼容旧客户端：未提供 account 时仍接受 username。 */
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    public LoginRequest() {
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
