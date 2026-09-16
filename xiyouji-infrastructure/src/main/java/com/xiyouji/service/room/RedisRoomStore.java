package com.xiyouji.service.room;

import com.xiyouji.exception.StorageUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
        room.setLastActiveAt(LocalDateTime.now());
        try {
            com.xiyouji.service.RedisResourceWriter.write(redisTemplate.getConnectionFactory(), KEY_PREFIX + room.getCode(),
                    ((org.springframework.data.redis.serializer.RedisSerializer<Object>) redisTemplate.getValueSerializer()).serialize(room),
                    java.time.Duration.ofHours(TTL_HOURS));
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    @Override public boolean createIfAbsent(Room room) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + room.getCode(), room, TTL_HOURS, TimeUnit.HOURS));
        } catch (Exception e) { throw unavailable(e); }
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
            return com.xiyouji.service.RedisResourceWriter.remove(redisTemplate.getConnectionFactory(), KEY_PREFIX + code);
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

    @Override
    public List<Room> findAll() {
        List<Room> all = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(100).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Room room = get(key.substring(KEY_PREFIX.length()));
                if (room != null) {
                    all.add(room);
                }
            }
        } catch (Exception e) {
            throw unavailable(e);
        }
        return all;
    }

    private StorageUnavailableException unavailable(Exception cause) {
        return new StorageUnavailableException("共享房间存储暂不可用，请稍后重试", cause);
    }
}
