package com.xiyouji.exception;

/**
 * 敌人未找到异常
 * 当请求的敌人不存在时抛出
 * HTTP状态码: 404
 */
public class EnemyNotFoundException extends BusinessException {

    public EnemyNotFoundException(String message) {
        super("ENEMY_NOT_FOUND", message, 404);
    }

    public EnemyNotFoundException(String message, Throwable cause) {
        super("ENEMY_NOT_FOUND", message, 404, cause);
    }
}
