package com.xiyouji.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyouji.dto.request.auth.LoginRequest;
import com.xiyouji.dto.request.auth.RegisterRequest;
import com.xiyouji.dto.response.auth.AuthResponse;
import com.xiyouji.service.AuthService;
import com.xiyouji.service.CommandGuard;
import com.xiyouji.service.CommandIdempotencyService;
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
    private final CommandIdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public AuthController(AuthService authService, CommandIdempotencyService idempotency,
                          ObjectMapper objectMapper) {
        this.authService = authService;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String fingerprint = CommandGuard.fingerprint("POST", "/api/auth/register",
                request.getUsername() + "|" + request.getPassword());
        String scope = "auth:register:" + request.getUsername();
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        if (previous != null && previous.completed()) return ResponseEntity.ok(readResponse(previous.value()));
        try {
            AuthResponse response = authService.register(request);
            idempotency.complete(scope, idempotencyKey, fingerprint, writeResponse(response));
            return ResponseEntity.ok(response);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
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
    public ResponseEntity<AuthResponse> guestLogin(@RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String fingerprint = CommandGuard.fingerprint("POST", "/api/auth/guest", "");
        String scope = "auth:guest";
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        if (previous != null && previous.completed()) return ResponseEntity.ok(readResponse(previous.value()));
        try {
            AuthResponse response = authService.guestLogin();
            idempotency.complete(scope, idempotencyKey, fingerprint, writeResponse(response));
            return ResponseEntity.ok(response);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
    }

    private String writeResponse(AuthResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法缓存认证响应", e);
        }
    }

    private AuthResponse readResponse(String value) {
        try {
            return objectMapper.readValue(value, AuthResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("幂等认证响应已损坏", e);
        }
    }
}
