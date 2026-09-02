package com.xiyouji.exception;

/**
 * 无效操作异常
 * 当玩家执行了不允许的操作时抛出
 * HTTP状态码: 400
 */
public class InvalidActionException extends BusinessException {

    public InvalidActionException(String message) {
        super("INVALID_ACTION", message, 400);
    }

    public InvalidActionException(String message, Throwable cause) {
        super("INVALID_ACTION", message, 400, cause);
    }
}
