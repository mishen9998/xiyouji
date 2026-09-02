package com.xiyouji.exception;

import java.util.Map;

/** Base exception for strict command concurrency/idempotency failures. */
public class ConcurrencyException extends BusinessException {

    protected ConcurrencyException(String code, String message, Map<String, Object> details) {
        super(code, message, 409, details);
    }
}
