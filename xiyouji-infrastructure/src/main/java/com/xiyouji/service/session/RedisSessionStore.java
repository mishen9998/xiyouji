package com.xiyouji.service.session;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.xiyouji.exception.StateVersionConflictException;
import com.xiyouji.exception.StorageUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

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

    /** Versioned namespace invalidates legacy sessions without owner metadata. */
    private static final String KEY_PREFIX = "xiyouji:session:v2:";
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
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
            String currentJson = redisTemplate.opsForValue().get(key);
            if (currentJson != null) {
                GameSession current = objectMapper.readValue(currentJson, GameSession.class);
                if (current.getStateVersion() > session.getStateVersion()) {
                    throw new StateVersionConflictException("session:" + sessionId,
                            session.getStateVersion(), current.getStateVersion());
                }
            }
            session.setStateVersion(session.getStateVersion() + 1);
            // 手动序列化为 JSON 字符串后存入 Redis
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key, json, SESSION_TTL);
            log.debug("会话已存入Redis: sessionId={}, size={}bytes", sessionId, json.length());
        } catch (StateVersionConflictException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis存储失败，拒绝写入本机内存: sessionId={}, error={}", sessionId, e.getMessage());
            throw new StorageUnavailableException("共享会话存储暂不可用，请稍后重试", e);
        }
    }

    @Override
    public GameSession get(String sessionId) {
        String key = buildKey(sessionId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            // 从 JSON 反序列化为 GameSession
            GameSession session = objectMapper.readValue(json, GameSession.class);
            log.debug("从Redis读取会话: sessionId={}", sessionId);
            return session;
        } catch (Exception e) {
            log.error("Redis读取失败，拒绝回退到本机内存: sessionId={}, error={}", sessionId, e.getMessage());
            throw new StorageUnavailableException("共享会话存储暂不可用，请稍后重试", e);
        }
    }

    @Override
    public boolean remove(String sessionId) {
        String key = buildKey(sessionId);
        try {
            boolean removedFromRedis = Boolean.TRUE.equals(redisTemplate.delete(key));
            if (removedFromRedis) {
                log.debug("会话已移除: sessionId={}", sessionId);
            }
            return removedFromRedis;
        } catch (Exception e) {
            log.error("Redis删除失败: sessionId={}, error={}", sessionId, e.getMessage());
            throw new StorageUnavailableException("共享会话存储暂不可用，请稍后重试", e);
        }
    }

    @Override
    public boolean exists(String sessionId) {
        String key = buildKey(sessionId);
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Redis检查存在性失败: sessionId={}, error={}", sessionId, e.getMessage());
            throw new StorageUnavailableException("共享会话存储暂不可用，请稍后重试", e);
        }
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
