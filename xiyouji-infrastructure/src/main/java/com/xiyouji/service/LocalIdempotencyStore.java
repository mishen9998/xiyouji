package com.xiyouji.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** JVM implementation used only by standalone mode. */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "false", matchIfMissing = true)
public class LocalIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, Entry> keys = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        long now = System.currentTimeMillis();
        keys.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        return keys.putIfAbsent(key, new Entry("legacy", "", false, now + ttl.toMillis())) == null;
    }

    @Override
    public boolean tryAcquire(String key, String fingerprint, Duration ttl) {
        long now = System.currentTimeMillis();
        keys.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        return keys.putIfAbsent(key, new Entry(fingerprint, "", false, now + ttl.toMillis())) == null;
    }

    @Override
    public Optional<IdempotencyStore.Entry> find(String key) {
        Entry entry = keys.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt() <= System.currentTimeMillis()) {
            keys.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(new IdempotencyStore.Entry(entry.fingerprint(), entry.value(), entry.completed()));
    }

    @Override
    public void complete(String key, String fingerprint, String value, Duration ttl) {
        keys.put(key, new Entry(fingerprint, value == null ? "" : value, true,
                System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public void remove(String key) {
        keys.remove(key);
    }

    private record Entry(String fingerprint, String value, boolean completed, long expiresAt) {}
}
