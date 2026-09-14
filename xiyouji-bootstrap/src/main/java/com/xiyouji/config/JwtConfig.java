package com.xiyouji.config;

import com.xiyouji.security.JwtProperties;
import com.xiyouji.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 装配（bootstrap 层）
 *
 * 唯一允许接触 @Value / yml 配置的边界，将配置值收敛为纯 POJO 的
 * JwtProperties 后注入应用层的 JwtUtil，保持应用层去框架依赖。
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtProperties jwtProperties(
            @Value("${jwt.secret:" + JwtProperties.DEFAULT_SECRET + "}") String secret,
            @Value("${jwt.expiration:" + JwtProperties.DEFAULT_EXPIRATION + "}") long expiration,
            @Value("${app.security.enforce-jwt:false}") boolean enforceJwt) {
        return new JwtProperties(secret, expiration, enforceJwt);
    }

    @Bean
    public JwtUtil jwtUtil(JwtProperties properties) {
        return new JwtUtil(properties);
    }
}