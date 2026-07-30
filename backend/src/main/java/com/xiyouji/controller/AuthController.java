package com.xiyouji.controller;

import com.xiyouji.dto.request.auth.LoginRequest;
import com.xiyouji.dto.request.auth.RegisterRequest;
import com.xiyouji.dto.response.auth.AuthResponse;
import com.xiyouji.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器 - 提供注册和登录接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * 游客登录
     * POST /api/auth/guest
     * 无需用户名密码，直接签发游客JWT Token（username="guest_"+随机4位数字, role="GUEST"）
     * 返回格式与 login 接口一致
     */
    @PostMapping("/guest")
    public ResponseEntity<AuthResponse> guestLogin() {
        return ResponseEntity.ok(authService.guestLogin());
    }
}
