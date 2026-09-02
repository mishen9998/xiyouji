package com.xiyouji.exception;

/**
 * 会话未找到异常
 * 当请求的游戏会话不存在时抛出
 * HTTP状态码: 404
 */
public class SessionNotFoundException extends BusinessException {

    public SessionNotFoundException(String message) {
        super("SESSION_NOT_FOUND", message, 404);
    }

    public SessionNotFoundException(String message, Throwable cause) {
        super("SESSION_NOT_FOUND", message, 404, cause);
    }
}
