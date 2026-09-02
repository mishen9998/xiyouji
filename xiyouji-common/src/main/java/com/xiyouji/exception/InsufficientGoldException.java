package com.xiyouji.exception;

/**
 * 金币不足异常
 * 当玩家金币不足以购买卡牌或服务时抛出
 * HTTP状态码: 400
 */
public class InsufficientGoldException extends BusinessException {

    public InsufficientGoldException(String message) {
        super("INSUFFICIENT_GOLD", message, 400);
    }

    public InsufficientGoldException(String message, Throwable cause) {
        super("INSUFFICIENT_GOLD", message, 400, cause);
    }
}
