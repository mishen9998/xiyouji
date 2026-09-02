package com.xiyouji.service;

import java.time.Duration;
import java.util.Optional;

/** Port for short-lived request idempotency keys. */
public interface IdempotencyStore {

    record Entry(String fingerprint, String value, boolean completed) {
    }

    /** Read a previously started/completed command, if it still exists. */
    default Optional<Entry> find(String key) {
        return Optional.empty();
    }

    /** Atomically reserve a key for a request fingerprint. */
    default boolean tryAcquire(String key, String fingerprint, Duration ttl) {
        return tryAcquire(key, ttl);
    }

    /** Store the small replay value (usually a resource id or final version). */
    default void complete(String key, String fingerprint, String value, Duration ttl) {
        // Implementations that only support a marker remain safe: the command
        // is still serialized by the resource lock and the caller can refresh.
    }

    /** Remove an in-progress marker when the command failed before commit. */
    default void remove(String key) {
    }

    /** Legacy marker API retained for existing application tests. */
    boolean tryAcquire(String key, Duration ttl);
}
