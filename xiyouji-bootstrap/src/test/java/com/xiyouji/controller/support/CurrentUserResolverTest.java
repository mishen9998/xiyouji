package com.xiyouji.controller.support;

import com.xiyouji.exception.InvalidActionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CurrentUserResolver 单元测试
 * 验证 JWT subject 解析与未登录时的异常契约。
 */
@DisplayName("当前用户解析器")
class CurrentUserResolverTest {

    private final CurrentUserResolver resolver = new CurrentUserResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("已认证时返回用户名")
    void returnsUsername_whenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("guest_1", null, List.of()));

        assertEquals("guest_1", resolver.username());
    }

    @Test
    @DisplayName("无认证上下文时抛出异常")
    void throws_whenNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThrows(InvalidActionException.class, resolver::username);
    }

    @Test
    @DisplayName("匿名认证: 与既有控制器行为一致，返回其 name")
    void returnsName_whenAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("anon", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        // AnonymousAuthenticationToken.isAuthenticated() 恒为 true，
        // 与拆分前 Game/Room 控制器取用户名的行为保持一致。
        assertEquals("anonymousUser", resolver.username());
    }
}