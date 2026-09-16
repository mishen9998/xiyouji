package com.xiyouji.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyouji.exception.ResultUnknownException;
import com.xiyouji.exception.StorageUnavailableException;
import com.xiyouji.service.session.RedisSessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Shared token ownership; no takeover of an unexpired legacy or unknown command. */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "true")
public class RedisIdempotencyStore implements IdempotencyStore {
    public static final String PREFIX = "xiyouji:idempotency:";
    public static final String LINK_PREFIX = "xiyouji:command-resource:";
    public static final String BACKLINK_PREFIX = "xiyouji:resource-command:";
    private static final String OWNER = """
        local raw = redis.call('GET', KEYS[1])
        if not raw then return -1 end
        local entry = cjson.decode(raw)
        if entry.completed or not entry.executionToken or entry.executionToken == cjson.null
          or entry.executionToken ~= ARGV[1] or tostring(entry.generation) ~= ARGV[2] then return -1 end
        """;
    private static final DefaultRedisScript<Long> CAS = new DefaultRedisScript<>(OWNER + """
        if ARGV[3] == 'abort' then redis.call('DEL', KEYS[1])
        elseif ARGV[3] == 'renew' then redis.call('PEXPIRE', KEYS[1], ARGV[4])
        else redis.call('SET', KEYS[1], ARGV[5], 'PX', ARGV[4]) end
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
        if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then return 1 end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>(OWNER + """
        if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
        redis.call('SET', KEYS[2], ARGV[3], 'NX', 'PX', ARGV[4])
        redis.call('SET', KEYS[1], ARGV[5], 'PX', ARGV[6])
        redis.call('SET', KEYS[3], ARGV[5], 'PX', ARGV[4])
        redis.call('SET', KEYS[4], KEYS[3], 'PX', ARGV[4])
        return 1
        """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private RedisTemplate<String, Object> resources;
    private RedisSessionStore sessions;
    public RedisIdempotencyStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }
    @Autowired public void configureResources(RedisTemplate<String, Object> resources, RedisSessionStore sessions) {
        this.resources = resources;
        this.sessions = sessions;
    }
    @Override public Optional<Entry> find(String key) {
        try {
            String payload = redis.opsForValue().get(PREFIX + key);
            if (payload == null) payload = redis.opsForValue().get(LINK_PREFIX + key);
            return payload == null ? Optional.empty() : Optional.of(mapper.readValue(payload, Entry.class));
        } catch (Exception e) { throw unavailable(e); }
    }
    @Override public boolean reserve(String key, Entry owner, Duration ttl) {
        try {
            return Long.valueOf(1).equals(redis.execute(RESERVE, List.of(PREFIX + key, LINK_PREFIX + key),
                    mapper.writeValueAsString(owner), Long.toString(ttl.toMillis())));
        } catch (Exception e) { throw unavailable(e); }
    }
    private boolean cas(String key, Entry owner, String operation, Duration ttl, String value) {
        if (owner == null || owner.executionToken() == null) return false;
        try {
            Long result = redis.execute(CAS, List.of(PREFIX + key), owner.executionToken(),
                    Long.toString(owner.generation()), operation, Long.toString(ttl.toMillis()),
                    mapper.writeValueAsString(owner.completed(value, null)));
            return Long.valueOf(1).equals(result);
        } catch (Exception e) { throw unavailable(e); }
    }
    @Override public boolean complete(String key, Entry owner, String value, Duration ttl) { return cas(key, owner, "complete", ttl, value); }
    @Override public boolean abort(String key, Entry owner) { return cas(key, owner, "abort", Duration.ZERO, ""); }
    @Override public boolean renew(String key, Entry owner, Duration ttl) { return cas(key, owner, "renew", ttl, ""); }
    @Override public boolean create(String key, Entry owner, Creation<?> creation, String response) {
        if (owner == null || owner.executionToken() == null) throw new ResultUnknownException();
        try {
            boolean room = "room".equals(creation.kind());
            String resourceKey = room ? "room:" + creation.resourceRef() : "xiyouji:session:v2:" + creation.resourceRef();
            String payload = room ? new String(((org.springframework.data.redis.serializer.RedisSerializer<Object>)
                    resources.getValueSerializer()).serialize(creation.resource()), StandardCharsets.UTF_8)
                    : sessions.serialize((com.xiyouji.service.session.GameSession) creation.resource());
            String receipt = mapper.writeValueAsString(owner.completed(response, creation.kind() + ":" + creation.resourceRef()));
            Long result = redis.execute(CREATE, List.of(PREFIX + key, resourceKey, LINK_PREFIX + key,
                            BACKLINK_PREFIX + resourceKey), owner.executionToken(), Long.toString(owner.generation()),
                    payload, Long.toString(Duration.ofHours(room ? 2 : 24).toMillis()), receipt, Long.toString(CommandGuard.TTL.toMillis()));
            if (result == null || result < 0) throw new ResultUnknownException();
            return result == 1;
        } catch (ResultUnknownException e) { throw e; }
        catch (Exception e) { throw unavailable(e); }
    }
    @Override public boolean tryAcquire(String key, Duration ttl) { return tryAcquire(key, "legacy", ttl); }
    @Override public boolean tryAcquire(String key, String fingerprint, Duration ttl) {
        return reserve(key, new Entry(fingerprint, "", false), ttl);
    }
    private StorageUnavailableException unavailable(Exception e) {
        return new StorageUnavailableException("共享幂等存储暂不可用", e);
    }
}
