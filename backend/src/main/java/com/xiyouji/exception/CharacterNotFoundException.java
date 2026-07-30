package com.xiyouji.exception;

/**
 * 角色未找到异常
 * 当请求的角色职业不存在时抛出
 * HTTP状态码: 404
 */
public class CharacterNotFoundException extends BusinessException {

    public CharacterNotFoundException(String message) {
        super("CHARACTER_NOT_FOUND", message, 404);
    }

    public CharacterNotFoundException(String message, Throwable cause) {
        super("CHARACTER_NOT_FOUND", message, 404, cause);
    }
}
