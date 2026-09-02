package com.xiyouji.config;

import com.xiyouji.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security配置
 * - JWT无状态认证
 * - 通过 app.security.enforce-jwt 控制：true时游戏API需要认证，false时开发模式兼容
 * - 禁用CSRF
 * - 允许H2控制台iframe
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final StrictCommandHeadersFilter strictCommandHeadersFilter;

    /**
     * 是否强制JWT认证。
     * true  -> 生产模式：/api/game/** 需要认证
     * false -> 开发模式：/api/game/** 放行（保持前端兼容性）
     * 默认 false，通过环境变量 ENFORCE_JWT 覆盖。
     */
    @Value("${app.security.enforce-jwt:false}")
    private boolean enforceJwt;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          StrictCommandHeadersFilter strictCommandHeadersFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.strictCommandHeadersFilter = strictCommandHeadersFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 始终放行的公共路径（认证接口、静态资源、Swagger、实例信息等）。
        // Actuator 在生产模式下单独处理：只开放健康探针和 Prometheus，
        // 避免 /info、/metrics 等管理信息被匿名访问。
        java.util.List<String> publicPathList = new java.util.ArrayList<>(java.util.Arrays.asList(
            // 认证相关（含游客登录）
            "/api/auth/**",
            // Swagger / OpenAPI
            "/swagger-ui/**",
            "/v3/api-docs/**",
            // 静态资源
            "/css/**",
            "/js/**",
            "/images/**",
            "/index.html",
            "/",
            // Vue Router SPA 路由
            "/char-select",
            "/map",
            "/battle",
            "/room",
            "/room/**",
            // 实例信息接口 (分布式监控用)
            "/api/instance/**",
            // WebSocket端点：JWT认证由 WebSocketAuthInterceptor 在握手时通过 ?token=xxx 完成
            // SockJS fallback 会产生 /ws/** 下多个子端点（info、iframe 等），需全部放行
            "/ws/**",
            "/ws"
        ));

        if (enforceJwt) {
            publicPathList.add("/actuator/health");
            publicPathList.add("/actuator/health/**");
            publicPathList.add("/actuator/prometheus");
        } else {
            // 开发模式保留完整 Actuator，便于本地排查问题。
            publicPathList.add("/actuator/**");
        }

        // H2 控制台仅在开发模式放行；生产模式（enforceJwt=true）下移除，避免暴露数据库管理界面
        if (!enforceJwt) {
            publicPathList.add("/h2-console/**");
        }
        String[] publicPaths = publicPathList.toArray(new String[0]);

        // 根据 enforceJwt 值用 if-else 配置不同的授权策略
        if (enforceJwt) {
            // 生产模式：游戏API需要认证，不再是 permitAll
            http.authorizeHttpRequests(auth -> auth
                .requestMatchers(publicPaths).permitAll()
                // /api/game/** 落入 anyRequest().authenticated() 强制要求JWT
                .anyRequest().authenticated()
            );
        } else {
            // 开发模式：保持现有行为，游戏API放行（认证可选）
            http.authorizeHttpRequests(auth -> auth
                .requestMatchers(publicPaths).permitAll()
                // 游戏API暂时放行（保持前端兼容性，认证可选）
                .requestMatchers(
                    "/api/game/new",
                    "/api/game/state/**",
                    "/api/game/move/**",
                    "/api/game/battle/**",
                    "/api/game/event/**",
                    "/api/game/reward/**",
                    "/api/game/deck/**",
                    "/api/game/next-layer/**",
                    "/api/game/sessions/**",
                    // 多人房间与战斗API（开发模式放行，认证可选）
                    "/api/room/**",
                    "/api/multiplayer/**"
                ).permitAll()
                .anyRequest().authenticated()
            );
        }

        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
            .addFilterBefore(strictCommandHeadersFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
