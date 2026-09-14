package com.xiyouji.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 * 直接通过 JwtProperties（纯 POJO）构造，无需 Spring 上下文
 */
@DisplayName("JwtUtil 单元测试")
class JwtUtilTest {

    private static final String TEST_SECRET = JwtProperties.DEFAULT_SECRET;
    private static final long TEST_EXPIRATION = JwtProperties.DEFAULT_EXPIRATION;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(new JwtProperties(TEST_SECRET, TEST_EXPIRATION, false));
    }

    @Test
    @DisplayName("generateToken 返回非空 token")
    void testGenerateToken_returnsNonEmptyToken() {
        String token = jwtUtil.generateToken("testuser", "USER");

        assertNotNull(token, "token 不应为 null");
        assertFalse(token.isEmpty(), "token 不应为空字符串");
        assertTrue(token.contains("."), "JWT token 应包含点号分隔符");
    }

    @Test
    @DisplayName("extractUsername 返回正确的用户名")
    void testExtractUsername_returnsCorrectUsername() {
        String token = jwtUtil.generateToken("testuser", "USER");

        String username = jwtUtil.extractUsername(token);

        assertEquals("testuser", username, "提取的用户名应匹配");
    }

    @Test
    @DisplayName("validateToken 有效 token 返回 true")
    void testValidateToken_validToken_returnsTrue() {
        String token = jwtUtil.generateToken("testuser", "USER");

        boolean valid = jwtUtil.validateToken(token);

        assertTrue(valid, "有效 token 应返回 true");
    }

    @Test
    @DisplayName("validateToken 过期 token 返回 false")
    void testValidateToken_expiredToken_returnsFalse() {
        // 负过期时间使生成的 token 立即过期
        JwtUtil expiredUtil = new JwtUtil(new JwtProperties(TEST_SECRET, -1000L, false));
        String token = expiredUtil.generateToken("testuser", "USER");

        boolean valid = expiredUtil.validateToken(token);

        assertFalse(valid, "过期 token 应返回 false");
    }

    @Test
    @DisplayName("extractRole 返回正确的角色")
    void testExtractRole_returnsCorrectRole() {
        String token = jwtUtil.generateToken("testuser", "ADMIN");

        String role = jwtUtil.extractRole(token);

        assertEquals("ADMIN", role, "提取的角色应匹配");
    }
}