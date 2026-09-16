package com.xiyouji.config;

import com.xiyouji.controller.support.CurrentUserResolver;
import com.xiyouji.service.room.RoomService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** All private REST room/battle routes authorize before executing or replaying commands. */
@Configuration
public class RoomAuthorizationConfig implements WebMvcConfigurer {
    private final RoomService rooms;
    private final CurrentUserResolver currentUser;
    public RoomAuthorizationConfig(RoomService rooms, CurrentUserResolver currentUser) {
        this.rooms = rooms;
        this.currentUser = currentUser;
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor()).addPathPatterns("/api/room/**", "/api/multiplayer/battle/**");
    }
    public HandlerInterceptor interceptor() {
        return new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                String tail = path.startsWith("/api/room/") ? path.substring(10)
                        : path.substring("/api/multiplayer/battle/".length());
                String code = tail.split("/")[0];
                if (!java.util.Set.of("create", "join", "characters").contains(code))
                    rooms.assertMember(code, currentUser.username());
                return true;
            }
        };
    }
}
