package com.xiyouji.controller.support;

import com.xiyouji.exception.InvalidActionException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 当前登录用户解析器
 *
 * 统一从 SecurityContext 获取 JWT subject（游客用户名），
 * 替代原先 Game/Room/Battle 三个控制器中重复且行为不一致的 currentUsername()。
 * 所有 API 均位于 JWT 过滤器之后，未登录不可达，因此统一按异常处理。
 */
@Component
public class CurrentUserResolver {

    public String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new InvalidActionException("未登录，请先获取游客token");
        }
        return auth.getName();
    }
}