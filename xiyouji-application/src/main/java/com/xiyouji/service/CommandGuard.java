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
                throw new IdempotencyInProgressException();
            }
            return entry;
        }
        if (!store.tryAcquire(key, fingerprint, TTL)) {
            var raced = store.find(key);
            if (raced.isPresent() && raced.get().fingerprint().equals(fingerprint) && raced.get().completed()) {
                return raced.get();
            }
            throw new IdempotencyInProgressException();
        }
        return new IdempotencyStore.Entry(fingerprint, "", false);
    }

    public static void checkVersion(String resource, long expected, long actual) {
        if (expected < 0 || expected != actual) {
            throw new StateVersionConflictException(resource, expected, actual);
        }
    }
}
