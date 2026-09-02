package com.xiyouji.exception;

/**
 * 认证失败异常
 * 当用户名或密码错误、token无效等认证失败情况时抛出
 * HTTP状态码: 401
 */
public class AuthenticationFailedException extends BusinessException {

    public AuthenticationFailedException(String message) {
        super("AUTH_FAILED", message, 401);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super("AUTH_FAILED", message, 401, cause);
    }
}
