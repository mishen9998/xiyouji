package com.xiyouji.exception;

/**
 * 卡牌未找到异常
 * 当请求的卡牌不存在时抛出
 * HTTP状态码: 404
 */
public class CardNotFoundException extends BusinessException {

    public CardNotFoundException(String message) {
        super("CARD_NOT_FOUND", message, 404);
    }

    public CardNotFoundException(String message, Throwable cause) {
        super("CARD_NOT_FOUND", message, 404, cause);
    }
}
