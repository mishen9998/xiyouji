package com.xiyouji.exception;

/** Returned when a command against an existing resource omits its version. */
public class ExpectedStateVersionRequiredException extends BusinessException {
    public ExpectedStateVersionRequiredException() {
        super("EXPECTED_STATE_VERSION_REQUIRED", "写请求必须携带 X-Expected-State-Version", 400);
    }
}
