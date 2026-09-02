package com.xiyouji.service.room;

import com.xiyouji.exception.StorageUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis房间存储
 * 在 app.redis.session-enabled=true 时启用（生产/多实例模式）。
 * 房间数据存于 Redis，设置 2 小时 TTL 自动过期，避免僵尸房间占用内存。
 */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "true")
public class RedisRoomStore implements RoomStore {

    private static final String KEY_PREFIX = "room:";
    private static final long TTL_HOURS = 2L;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRoomStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(Room room) {
        room.setStateVersion(room.getStateVersion() + 1);
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + room.getCode(), room, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    @Override
    public Room get(String code) {
        try {
            Object obj = redisTemplate.opsForValue().get(KEY_PREFIX + code);
            return obj instanceof Room ? (Room) obj : null;
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    @Override
    public boolean remove(String code) {
        try {
            Boolean deleted = redisTemplate.delete(KEY_PREFIX + code);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    @Override
    public boolean exists(String code) {
        try {
            Boolean has = redisTemplate.hasKey(KEY_PREFIX + code);
            return Boolean.TRUE.equals(has);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    @Override
    public boolean codeExists(String code) {
        return exists(code);
    }

    private StorageUnavailableException unavailable(Exception cause) {
        return new StorageUnavailableException("共享房间存储暂不可用，请稍后重试", cause);
    }
}
