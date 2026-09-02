package com.xiyouji.service.room;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存房间存储
 * 在 app.redis.session-enabled=false 时启用（开发/standalone模式）。
 * 使用 ConcurrentHashMap 存储房间，重启丢失。
 */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRoomStore implements RoomStore {

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    @Override
    public void save(Room room) {
        room.setStateVersion(room.getStateVersion() + 1);
        rooms.put(room.getCode(), room);
    }

    @Override
    public Room get(String code) {
        return rooms.get(code);
    }

    @Override
    public boolean remove(String code) {
        return rooms.remove(code) != null;
    }

    @Override
    public boolean exists(String code) {
        return rooms.containsKey(code);
    }

    @Override
    public boolean codeExists(String code) {
        return rooms.containsKey(code);
    }
}
