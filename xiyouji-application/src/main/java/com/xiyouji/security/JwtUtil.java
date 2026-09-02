package com.xiyouji.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT工具类 - 负责token的生成、解析和验证
 * 使用 jjwt 0.12.6 API
 *
 * 安全约束：
 * - 生产模式（enforceJwt=true）下 JWT_SECRET 必须通过环境变量注入，禁止使用默认值
 * - 默认值仅在开发模式使用，避免密钥可预测导致 token 伪造
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** 默认密钥（仅开发模式可用，生产模式启动时会拒绝） */
    private static final String DEFAULT_SECRET =
            "xiyouji-secret-key-for-jwt-token-generation-must-be-at-least-256-bits-long";

    @Value("${jwt.secret:" + DEFAULT_SECRET + "}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    /**
     * 是否强制 JWT 认证（生产模式）。
     * true 时 JWT_SECRET 必须通过环境变量注入，禁止使用默认值。
     */
    @Value("${app.security.enforce-jwt:false}")
    private boolean enforceJwt;

    /**
     * 启动时校验 JWT 配置：
     * - 生产模式（enforceJwt=true）下，若 secret 为默认值或为空则抛异常阻止启动
     * - 开发模式仅记录警告
     */
    @PostConstruct
    public void validateSecret() {
        boolean isDefault = DEFAULT_SECRET.equals(secret);
        if (enforceJwt) {
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException(
                        "生产模式下 JWT_SECRET 不能为空，请通过环境变量 JWT_SECRET 注入");
            }
            if (isDefault) {
                throw new IllegalStateException(
                        "生产模式下禁止使用默认 JWT_SECRET，请通过环境变量 JWT_SECRET 注入强随机密钥");
            }
            // 校验密钥长度（HS256 要求 >= 32 字节 = 256 位）
            if (secret.getBytes().length < 32) {
                throw new IllegalStateException(
                        "JWT_SECRET 长度不足 32 字节（256 位），不满足 HS256 签名要求");
            }
            log.info("JWT secret validated (production mode, custom secret)");
        } else {
            if (isDefault) {
                log.warn("JWT 使用默认密钥（仅开发模式可用），生产环境请通过 JWT_SECRET 环境变量注入强随机密钥");
            } else {
                log.info("JWT secret loaded from environment (dev mode)");
            }
        }
    }

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * 生成JWT token
     *
     * @param username 用户名
     * @param role     用户角色
     * @return JWT token字符串
     */
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从token中提取用户名
     */
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * 从token中提取角色
     */
    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * 解析token获取Claims
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证token是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 检查token是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            return getClaims(token).getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }
}
