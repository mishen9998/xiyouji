package com.xiyouji.service.room;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存多人战斗状态存储
 * 在 app.redis.session-enabled=false 时启用（开发/standalone模式）。
 */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryMultiplayerBattleStore implements MultiplayerBattleStore {

    private final ConcurrentHashMap<String, MultiplayerBattleState> battles = new ConcurrentHashMap<>();

    @Override
    public void save(MultiplayerBattleState state) {
        state.setStateVersion(state.getStateVersion() + 1);
        battles.put(state.getRoomCode(), state);
    }

    @Override
    public MultiplayerBattleState get(String roomCode) {
        return battles.get(roomCode);
    }

    @Override
    public boolean remove(String roomCode) {
        return battles.remove(roomCode) != null;
    }

    @Override
    public boolean exists(String roomCode) {
        return battles.containsKey(roomCode);
    }
}
