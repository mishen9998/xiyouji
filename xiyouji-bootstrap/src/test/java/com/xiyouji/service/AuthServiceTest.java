package com.xiyouji.service;

import com.xiyouji.dto.request.auth.LoginRequest;
import com.xiyouji.dto.request.auth.RegisterRequest;
import com.xiyouji.dto.response.auth.AuthResponse;
import com.xiyouji.exception.AuthenticationFailedException;
import com.xiyouji.exception.UserAlreadyExistsException;
import com.xiyouji.model.User;
import com.xiyouji.port.UserRepositoryPort;
import com.xiyouji.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试
 * 覆盖注册（重复用户名、正常注册）、登录（用户不存在、密码错误、正常登录）、游客登录
 * 验证阶段1引入的自定义异常：UserAlreadyExistsException / AuthenticationFailedException
 */
@DisplayName("AuthService 单元测试")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("register 用户名已存在时抛出 UserAlreadyExistsException（409）")
    void register_duplicateUsername_throwsUserAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existingUser");
        request.setPassword("password123");

        when(userRepository.existsByUsername("existingUser")).thenReturn(true);

        UserAlreadyExistsException ex = assertThrows(UserAlreadyExistsException.class,
                () -> authService.register(request),
                "用户名已存在时应抛出 UserAlreadyExistsException");

        assertEquals(409, ex.getHttpStatus(), "HTTP 状态码应为 409 Conflict");
        assertEquals("USER_ALREADY_EXISTS", ex.getErrorCode());
        // 不应执行 save
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register 正常注册成功并返回 JWT token")
    void register_validInput_returnsAuthResponse() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newUser");
        request.setPassword("password123");

        when(userRepository.existsByUsername("newUser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPwd");
        when(jwtUtil.generateToken("newUser", "PLAYER")).thenReturn("jwt-token-xxx");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("jwt-token-xxx", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("newUser", response.getUsername());
        assertEquals("PLAYER", response.getRole());
        verify(userRepository).save(argThat(u ->
                "newUser".equals(u.getAccount())
                        && "newUser".equals(u.getUsername())
                        && "encodedPwd".equals(u.getPassword())
                        && "PLAYER".equals(u.getRole())));
    }

    @Test
    @DisplayName("register 将登录账号和显示用户名分别持久化")
    void register_separateAccountAndDisplayName_persistsBoth() {
        RegisterRequest request = new RegisterRequest();
        request.setAccount("pilgrim01");
        request.setUsername("取经人");
        request.setPassword("password123");

        when(userRepository.existsByAccount("pilgrim01")).thenReturn(false);
        when(userRepository.existsByUsername("取经人")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPwd");
        when(jwtUtil.generateToken("取经人", "PLAYER")).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertEquals("pilgrim01", response.getAccount());
        assertEquals("取经人", response.getUsername());
        verify(userRepository).save(argThat(u ->
                "pilgrim01".equals(u.getAccount()) && "取经人".equals(u.getUsername())));
    }

    @Test
    @DisplayName("login 用户不存在时抛出 AuthenticationFailedException（401）")
    void login_userNotFound_throwsAuthenticationFailedException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("ghost");
        request.setPassword("anything");

        when(userRepository.findByAccount("ghost")).thenReturn(Optional.empty());

        AuthenticationFailedException ex = assertThrows(AuthenticationFailedException.class,
                () -> authService.login(request));

        assertEquals(401, ex.getHttpStatus());
        // 密码校验不应执行
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("login 密码错误时抛出 AuthenticationFailedException（401）")
    void login_wrongPassword_throwsAuthenticationFailedException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("realUser");
        request.setPassword("wrongPwd");

        User user = new User();
        user.setAccount("realUser");
        user.setUsername("realUser");
        user.setPassword("encodedCorrectPwd");
        user.setRole("PLAYER");

        when(userRepository.findByAccount("realUser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPwd", "encodedCorrectPwd")).thenReturn(false);

        AuthenticationFailedException ex = assertThrows(AuthenticationFailedException.class,
                () -> authService.login(request));

        assertEquals(401, ex.getHttpStatus());
        // 不应签发 token
        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("login 正确凭据返回 JWT token")
    void login_validCredentials_returnsAuthResponse() {
        LoginRequest request = new LoginRequest();
        request.setUsername("realUser");
        request.setPassword("correctPwd");

        User user = new User();
        user.setAccount("realUser");
        user.setUsername("realUser");
        user.setPassword("encodedCorrectPwd");
        user.setRole("PLAYER");

        when(userRepository.findByAccount("realUser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPwd", "encodedCorrectPwd")).thenReturn(true);
        when(jwtUtil.generateToken("realUser", "PLAYER")).thenReturn("jwt-login-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt-login-token", response.getToken());
        assertEquals("realUser", response.getUsername());
        assertEquals("PLAYER", response.getRole());
    }

    @Test
    @DisplayName("guestLogin 返回 guest_ 前缀用户名和 GUEST 角色，不读写数据库")
    void guestLogin_returnsGuestCredentials_withoutDbAccess() {
        when(jwtUtil.generateToken(anyString(), eq("GUEST"))).thenReturn("guest-jwt-token");

        AuthResponse response = authService.guestLogin();

        assertNotNull(response);
        assertEquals("guest-jwt-token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertTrue(response.getUsername().startsWith("guest_"), "用户名应以 guest_ 开头");
        assertEquals(10, response.getUsername().length(), "用户名应为 guest_ + 4位数字");
        assertEquals("GUEST", response.getRole());
        // 游客登录不读写数据库
        verifyNoInteractions(userRepository);
    }
}
