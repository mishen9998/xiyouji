package com.xiyouji.exception;

/**
 * 用户已存在异常
 * 当注册时用户名已被占用时抛出
 * HTTP状态码: 409 Conflict
 */
public class UserAlreadyExistsException extends BusinessException {

    public UserAlreadyExistsException(String message) {
        super("USER_ALREADY_EXISTS", message, 409);
    }

    public UserAlreadyExistsException(String message, Throwable cause) {
        super("USER_ALREADY_EXISTS", message, 409, cause);
    }
}
