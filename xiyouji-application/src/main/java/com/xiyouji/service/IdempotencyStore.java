package com.xiyouji.service;

import java.time.Duration;
import java.util.Optional;

/** Port for short-lived request idempotency keys. */
public interface IdempotencyStore {

    record Entry(String fingerprint, String value, boolean completed, String executionToken,
                 long generation, String resourceRef) {
        public Entry(String fingerprint, String value, boolean completed) {
            this(fingerprint, value, completed, null, 0, null);
        }
        public Entry completed(String response, String ref) {
            return new Entry(fingerprint, response, true, executionToken, generation, ref);
        }
    }

    /** Read a previously started/completed command, if it still exists. */
    default Optional<Entry> find(String key) {
        return Optional.empty();
    }

    /** Atomically reserve a key for a request fingerprint. */
    default boolean tryAcquire(String key, String fingerprint, Duration ttl) {
        return tryAcquire(key, ttl);
    }

    /** Legacy marker API retained for existing application tests. */
    boolean tryAcquire(String key, Duration ttl);

    default boolean reserve(String key, Entry owner, Duration ttl) {
        throw new UnsupportedOperationException("Token reservation is required");
    }
    default boolean complete(String key, Entry owner, String value, Duration ttl) {
        throw new UnsupportedOperationException("Token CAS is required");
    }
    default boolean abort(String key, Entry owner) { return false; }
    default boolean renew(String key, Entry owner, Duration ttl) { return false; }

    /** False means candidate collision only; ownership loss must throw. */
    default boolean create(String key, Entry owner, Creation<?> creation, String response) {
        throw new UnsupportedOperationException("Atomic creation is required");
    }
    record Creation<T>(String kind, String resourceRef, Object resource, T response) {}
}
