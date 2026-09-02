package com.xiyouji.service;

import com.xiyouji.exception.StorageUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Redis SET NX implementation shared by all application replicas. */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "true")
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String PREFIX = "xiyouji:idempotency:";
    private final StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate,
                                 com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        return tryAcquire(key, "legacy", ttl);
    }

    @Override
    public boolean tryAcquire(String key, String fingerprint, Duration ttl) {
        try {
            String payload = objectMapper.writeValueAsString(new Entry(fingerprint, "", false));
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(PREFIX + key, payload, ttl));
        } catch (Exception e) {
            throw new StorageUnavailableException("共享幂等键存储暂不可用，请稍后重试", e);
        }
    }

    @Override
    public Optional<IdempotencyStore.Entry> find(String key) {
        try {
            String payload = redisTemplate.opsForValue().get(PREFIX + key);
            if (payload == null) return Optional.empty();
            Entry entry = objectMapper.readValue(payload, Entry.class);
            return Optional.of(new IdempotencyStore.Entry(entry.fingerprint(), entry.value(), entry.completed()));
        } catch (Exception e) {
            throw new StorageUnavailableException("共享幂等键存储暂不可用，请稍后重试", e);
        }
    }

    @Override
    public void complete(String key, String fingerprint, String value, Duration ttl) {
        try {
            String payload = objectMapper.writeValueAsString(new Entry(fingerprint, value == null ? "" : value, true));
            redisTemplate.opsForValue().set(PREFIX + key, payload, ttl);
        } catch (Exception e) {
            throw new StorageUnavailableException("共享幂等键存储暂不可用，请稍后重试", e);
        }
    }

    @Override
    public void remove(String key) {
        try {
            redisTemplate.delete(PREFIX + key);
        } catch (Exception e) {
            throw new StorageUnavailableException("共享幂等键存储暂不可用，请稍后重试", e);
        }
    }

    private record Entry(String fingerprint, String value, boolean completed) {}
}
