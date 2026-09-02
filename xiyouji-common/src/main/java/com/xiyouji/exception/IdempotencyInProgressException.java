package com.xiyouji.exception;

import java.util.Map;

/** Another request with the same idempotency key is still executing. */
public class IdempotencyInProgressException extends ConcurrencyException {
    public IdempotencyInProgressException() {
        super("IDEMPOTENCY_IN_PROGRESS", "相同请求正在处理中，请稍后使用同一幂等键重试", Map.of());
    }
}
