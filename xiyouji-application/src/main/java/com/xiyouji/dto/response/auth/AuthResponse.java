package com.xiyouji.dto.response.auth;

/**
 * 认证响应DTO - 包含JWT token和用户信息
 */
public class AuthResponse {

    private String token;
    private String tokenType;
    private String account;
    private String username;
    private String role;

    public AuthResponse() {}

    public AuthResponse(String token, String tokenType, String username, String role) {
        this.token = token;
        this.tokenType = tokenType;
        this.account = username;
        this.username = username;
        this.role = role;
    }

    // ===== Getters/Setters =====

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
