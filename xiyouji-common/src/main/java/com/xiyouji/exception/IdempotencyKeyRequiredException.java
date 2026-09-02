package com.xiyouji.exception;

/** Returned when a mutating command omits its idempotency key. */
public class IdempotencyKeyRequiredException extends BusinessException {
    public IdempotencyKeyRequiredException() {
        super("IDEMPOTENCY_KEY_REQUIRED", "写请求必须携带 X-Idempotency-Key", 400);
    }
}
