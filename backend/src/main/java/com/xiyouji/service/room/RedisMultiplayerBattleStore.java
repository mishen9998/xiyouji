package com.xiyouji.service.room;

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
        redisTemplate.opsForValue().set(
                KEY_PREFIX + state.getRoomCode(), state, TTL_HOURS, TimeUnit.HOURS);
    }

    @Override
    public MultiplayerBattleState get(String roomCode) {
        Object obj = redisTemplate.opsForValue().get(KEY_PREFIX + roomCode);
        return obj instanceof MultiplayerBattleState ? (MultiplayerBattleState) obj : null;
    }

    @Override
    public boolean remove(String roomCode) {
        Boolean deleted = redisTemplate.delete(KEY_PREFIX + roomCode);
        return Boolean.TRUE.equals(deleted);
    }

    @Override
    public boolean exists(String roomCode) {
        Boolean has = redisTemplate.hasKey(KEY_PREFIX + roomCode);
        return Boolean.TRUE.equals(has);
    }
}
