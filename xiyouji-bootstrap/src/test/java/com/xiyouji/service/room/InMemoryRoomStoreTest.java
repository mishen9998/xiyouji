package com.xiyouji.service.room;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 内存房间存储补充测试：save 盖章最后活动时间、findAll 列出全部房间。
 */
class InMemoryRoomStoreTest {

    @Test
    void saveStampsLastActiveAtAndIncrementsVersion() {
        InMemoryRoomStore store = new InMemoryRoomStore();
        Room room = new Room("ROOM0001", "host");
        room.setLastActiveAt(LocalDateTime.now().minusHours(1));
        LocalDateTime before = LocalDateTime.now();

        store.save(room);

        assertEquals(1, room.getStateVersion());
        assertFalse(room.getLastActiveAt().isBefore(before));
    }

    @Test
    void findAllReturnsAllSavedRooms() {
        InMemoryRoomStore store = new InMemoryRoomStore();
        store.save(new Room("ROOM0001", "host-a"));
        store.save(new Room("ROOM0002", "host-b"));

        List<Room> all = store.findAll();

        assertEquals(2, all.size());
    }
}