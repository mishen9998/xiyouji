package com.xiyouji.service.session;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis会话存储实现
 * 当 app.redis.session-enabled=true 时启用
 *
 * 分布式核心: 多个应用实例共享同一个 Redis,
 * 实例1创建的会话存入 Redis, 实例2可以直接读取
 *
 * 使用手动 JSON 序列化 (StringRedisTemplate + ObjectMapper)
 * 避免复杂对象序列化问题
 */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "true")
public class RedisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);

    private static final String KEY_PREFIX = "xiyouji:session:";
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, GameSession> fallbackStore = new ConcurrentHashMap<>();

    public RedisSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        // 忽略未知属性, 提高反序列化容错性
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void put(String sessionId, GameSession session) {
        String key = buildKey(sessionId);
        try {
            // 手动序列化为 JSON 字符串后存入 Redis
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key, json, SESSION_TTL);
            fallbackStore.remove(sessionId);
            log.debug("会话已存入Redis: sessionId={}, size={}bytes", sessionId, json.length());
        } catch (Exception e) {
            log.warn("Redis存储失败，回退到内存: sessionId={}, error={}", sessionId, e.getMessage());
            fallbackStore.put(sessionId, session);
        }
    }

    @Override
    public GameSession get(String sessionId) {
        String key = buildKey(sessionId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return fallbackStore.get(sessionId);
            }
            // 从 JSON 反序列化为 GameSession
            GameSession session = objectMapper.readValue(json, GameSession.class);
            log.debug("从Redis读取会话: sessionId={}", sessionId);
            return session;
        } catch (Exception e) {
            log.warn("Redis读取失败，回退到内存: sessionId={}, error={}", sessionId, e.getMessage());
            return fallbackStore.get(sessionId);
        }
    }

    @Override
    public boolean remove(String sessionId) {
        String key = buildKey(sessionId);
        boolean removedFromFallback = fallbackStore.remove(sessionId) != null;
        boolean removedFromRedis = false;
        try {
            removedFromRedis = Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            log.warn("Redis删除失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
        if (removedFromRedis || removedFromFallback) {
            log.debug("会话已移除: sessionId={}", sessionId);
            return true;
        }
        return false;
    }

    @Override
    public boolean exists(String sessionId) {
        String key = buildKey(sessionId);
        if (fallbackStore.containsKey(sessionId)) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Redis检查存在性失败: sessionId={}, error={}", sessionId, e.getMessage());
            return false;
        }
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
