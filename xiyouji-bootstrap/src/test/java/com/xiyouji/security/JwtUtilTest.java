package com.xiyouji.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 * 直接 new JwtUtil() 并使用反射设置 @Value 字段，无需 Spring 上下文
 */
@DisplayName("JwtUtil 单元测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String TEST_SECRET =
            "xiyouji-secret-key-for-jwt-token-generation-must-be-at-least-256-bits-long";
    private static final long TEST_EXPIRATION = 86400000L; // 24 小时

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil();
        setField(jwtUtil, "secret", TEST_SECRET);
        setField(jwtUtil, "expiration", TEST_EXPIRATION);
    }

    /** 通过反射设置私有字段 */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
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
    void testValidateToken_expiredToken_returnsFalse() throws Exception {
        // 设置负的过期时间，使 token 立即过期
        setField(jwtUtil, "expiration", -1000L);

        String token = jwtUtil.generateToken("testuser", "USER");

        boolean valid = jwtUtil.validateToken(token);

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
