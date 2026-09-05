package com.xiyouji.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket(STOMP)配置
 *
 * 端点：/ws - 前端通过 SockJS 连接，握手时带 ?token=xxx 进行 JWT 认证
 * 应用前缀：/app - 客户端发送消息的目的地前缀（如 /app/room/{code}/play）
 * 广播前缀：/topic - 服务端推送消息的目的地前缀（如 /topic/room/{code}）
 *
 * 房间频道约定：
 *   /topic/room/{code}          - 房间状态变化（玩家加入/退出/准备/选角）
 *   /topic/room/{code}/battle   - 战斗状态变化（出牌/回合/伤害）
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor,
                           @Value("${app.cors.allowed-origins:http://localhost:8080}") String[] allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 服务端广播消息目的地前缀，客户端订阅这些频道
        registry.enableSimpleBroker("/topic")
                .setTaskScheduler(roomHeartbeatScheduler())
                .setHeartbeatValue(new long[]{10000, 10000});
        // 客户端发送消息目的地前缀，映射到 @MessageMapping 方法
        registry.setApplicationDestinationPrefixes("/app");
        // 点对点消息前缀（暂不使用，预留）
        registry.setUserDestinationPrefix("/user");
    }

    @Bean
    public ThreadPoolTaskScheduler roomHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("room-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);
        // SockJS fallback，兼容不支持原生 WebSocket 的浏览器
        registry.addEndpoint("/ws")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }
}
