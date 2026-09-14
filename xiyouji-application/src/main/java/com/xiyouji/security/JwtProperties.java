package com.xiyouji.security;

/**
 * JWT 配置值（纯 POJO，不含框架依赖）
 *
 * 由 bootstrap 层读取 application.yml 后组装，注入 {@link JwtUtil}，
 * 使应用层不依赖 Spring 的 @Value / 生命周期注解。
 */
public final class JwtProperties {

    public static final String DEFAULT_SECRET =
            "xiyouji-secret-key-for-jwt-token-generation-must-be-at-least-256-bits-long";

    public static final long DEFAULT_EXPIRATION = 86400000L;

    private final String secret;
    private final long expiration;
    private final boolean enforceJwt;

    public JwtProperties(String secret, long expiration, boolean enforceJwt) {
        this.secret = secret;
        this.expiration = expiration;
        this.enforceJwt = enforceJwt;
    }

    public String getSecret() {
        return secret;
    }

    public long getExpiration() {
        return expiration;
    }

    public boolean isEnforceJwt() {
        return enforceJwt;
    }
}