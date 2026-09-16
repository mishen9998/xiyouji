package com.xiyouji.service;

import com.xiyouji.exception.IdempotencyInProgressException;
import com.xiyouji.exception.IdempotencyKeyReusedException;
import com.xiyouji.exception.StateVersionConflictException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Shared command guard for short-lived idempotency and optimistic version
 * checks. The resource lock must be acquired before calling checkVersion.
 */
public final class CommandGuard {

    public static final Duration TTL = Duration.ofMinutes(10);

    private CommandGuard() {
    }

    public static String fingerprint(String method, String path, String body) {
        String input = method + "\n" + path + "\n" + (body == null ? "" : body);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Reserve a key, rejecting reuse with a different fingerprint. */
    public static IdempotencyStore.Entry begin(IdempotencyStore store, String key, String fingerprint) {
        var existing = store.find(key);
        if (existing.isPresent()) {
            IdempotencyStore.Entry entry = existing.get();
            if (!entry.fingerprint().equals(fingerprint)) {
                throw new IdempotencyKeyReusedException();
            }
            if (!entry.completed()) {
                throw new com.xiyouji.exception.ResultUnknownException();
            }
            return entry;
        }
        var owner = new IdempotencyStore.Entry(fingerprint, "", false,
                java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), null);
        if (!store.reserve(key, owner, TTL)) {
            var raced = store.find(key);
            if (raced.isPresent() && raced.get().fingerprint().equals(fingerprint) && raced.get().completed()) {
                return raced.get();
            }
            throw new com.xiyouji.exception.ResultUnknownException();
        }
        return owner;
    }

    public static void checkVersion(String resource, long expected, long actual) {
        if (expected < 0 || expected != actual) {
            throw new StateVersionConflictException(resource, expected, actual);
        }
    }

    /** A known validation rejection is reportable; storage/runtime failures after entry
     * into business code cannot establish rollback and must retain the command marker. */
    public static RuntimeException failure(RuntimeException error) {
        if (error instanceof com.xiyouji.exception.BusinessException business && business.getHttpStatus() < 500) return error;
        return new com.xiyouji.exception.ResultUnknownException();
    }
}
