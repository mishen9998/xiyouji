package com.xiyouji.service;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Keep the atomic creation association alive for exactly the rolling resource lifetime. */
public final class RedisResourceWriter {
    private RedisResourceWriter() {}
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    public static boolean remove(RedisConnectionFactory factory, String key) {
        try (var connection = factory.getConnection()) {
            Long count = connection.scriptingCommands().eval(bytes("""
                local link = redis.call('GET', KEYS[2])
                if link then redis.call('DEL', link) end
                redis.call('DEL', KEYS[2])
                return redis.call('DEL', KEYS[1])
                """), ReturnType.INTEGER, 2, bytes(key), bytes(RedisIdempotencyStore.BACKLINK_PREFIX + key));
            return count != null && count > 0;
        }
    }
    public static void write(RedisConnectionFactory factory, String key, byte[] payload, Duration ttl) {
        try (var connection = factory.getConnection()) {
            connection.scriptingCommands().eval(bytes("""
                redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
                local link = redis.call('GET', KEYS[2])
                if link then
                  redis.call('PEXPIRE', link, ARGV[2])
                  redis.call('PEXPIRE', KEYS[2], ARGV[2])
                end
                return 1
                """), ReturnType.INTEGER, 2, bytes(key), bytes(RedisIdempotencyStore.BACKLINK_PREFIX + key),
                    payload, bytes(Long.toString(ttl.toMillis())));
        }
    }
}
