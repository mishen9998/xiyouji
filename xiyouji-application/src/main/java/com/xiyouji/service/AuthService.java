package com.xiyouji.service;

import com.xiyouji.dto.request.auth.LoginRequest;
import com.xiyouji.dto.request.auth.RegisterRequest;
import com.xiyouji.dto.response.auth.AuthResponse;
import com.xiyouji.exception.AuthenticationFailedException;
import com.xiyouji.exception.UserAlreadyExistsException;
import com.xiyouji.model.User;
import com.xiyouji.port.UserRepositoryPort;
import com.xiyouji.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 认证服务 - 处理用户注册和登录
 */
@Service
public class AuthService {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepositoryPort userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 用户注册
     *
     * @param request 注册请求（用户名 + 密码）
     * @return 认证响应（含JWT token）
     * @throws UserAlreadyExistsException 如果用户名已存在
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("PLAYER");
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return buildAuthResponse(token, user);
    }

    /**
     * 用户登录
     *
     * @param request 登录请求（用户名 + 密码）
     * @return 认证响应（含JWT token）
     * @throws AuthenticationFailedException 如果用户名不存在或密码错误
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AuthenticationFailedException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthenticationFailedException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return buildAuthResponse(token, user);
    }

    /**
     * 游客登录
     * 不校验用户名密码，也不写入数据库，直接生成一个游客 JWT Token。
     * 用户名格式为 "guest_" + 随机4位数字，角色固定为 GUEST。
     *
     * @return 认证响应（含JWT token），与 login 返回格式一致
     */
    public AuthResponse guestLogin() {
        // 生成随机4位数字（1000~9999），保证位数固定为4位
        int randomNum = ThreadLocalRandom.current().nextInt(1000, 10000);
        String guestUsername = "guest_" + randomNum;
        String guestRole = "GUEST";

        // 直接签发JWT Token，不读写数据库
        String token = jwtUtil.generateToken(guestUsername, guestRole);

        // 构建与登录一致的响应格式
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setUsername(guestUsername);
        response.setRole(guestRole);
        return response;
    }

    /**
     * 构建认证响应
     */
    private AuthResponse buildAuthResponse(String token, User user) {
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        return response;
    }
}
