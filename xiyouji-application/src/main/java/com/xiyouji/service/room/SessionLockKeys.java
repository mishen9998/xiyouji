package com.xiyouji.service.room;

/** Shared lock key for all mutations of one single-player session. */
public final class SessionLockKeys {

    private static final String PREFIX = "xiyouji:lock:session:";

    private SessionLockKeys() {
    }

    public static String forSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return PREFIX + sessionId;
    }
}
