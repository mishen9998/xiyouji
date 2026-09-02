package com.xiyouji.exception;

import java.util.Map;

/** Same key was reused with a different command fingerprint. */
public class IdempotencyKeyReusedException extends ConcurrencyException {
    public IdempotencyKeyReusedException() {
        super("IDEMPOTENCY_KEY_REUSED", "幂等键已用于另一条请求", Map.of());
    }
}
