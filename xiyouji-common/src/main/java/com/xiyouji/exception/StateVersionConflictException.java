package com.xiyouji.exception;

import java.util.Map;

/** Returned when a command was based on a stale aggregate version. */
public class StateVersionConflictException extends ConcurrencyException {

    private final long currentStateVersion;

    public StateVersionConflictException(String resource, long expected, long current) {
        super("STATE_VERSION_CONFLICT",
                "状态已被其他请求更新，请刷新后重试",
                Map.of("resource", resource,
                        "expectedStateVersion", expected,
                        "currentStateVersion", current));
        this.currentStateVersion = current;
    }

    public long getCurrentStateVersion() {
        return currentStateVersion;
    }
}
