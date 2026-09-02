package com.xiyouji.service.room;

import com.xiyouji.exception.StorageUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis多人战斗状态存储
 * 在 app.redis.session-enabled=true 时启用（生产/多实例模式）。
 * 战斗数据存于 Redis，设置 2 小时 TTL 自动过期。
 */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "true")
public class RedisMultiplayerBattleStore implements MultiplayerBattleStore {

    private static final String KEY_PREFIX = "multiplayer-battle:";
    private static final long TTL_HOURS = 2L;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisMultiplayerBattleStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(MultiplayerBattleState state) {
        state.setStateVersion(state.getStateVersion() + 1);
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + state.getRoomCode(), state, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    @Override
    public MultiplayerBattleState get(String roomCode) {
        try {
            Object obj = redisTemplate.opsForValue().get(KEY_PREFIX + roomCode);
            return obj instanceof MultiplayerBattleState ? (MultiplayerBattleState) obj : null;
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    @Override
    public boolean remove(String roomCode) {
        try {
            Boolean deleted = redisTemplate.delete(KEY_PREFIX + roomCode);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    @Override
    public boolean exists(String roomCode) {
        try {
            Boolean has = redisTemplate.hasKey(KEY_PREFIX + roomCode);
            return Boolean.TRUE.equals(has);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    private StorageUnavailableException unavailable(Exception cause) {
        return new StorageUnavailableException("共享战斗存储暂不可用，请稍后重试", cause);
    }
}
