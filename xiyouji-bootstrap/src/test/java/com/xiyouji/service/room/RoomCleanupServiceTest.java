package com.xiyouji.service.room;

import com.xiyouji.exception.StorageUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 房间清理服务测试。
 * 锁使用真实的 LocalDistributedLockService，因为 executeWithLock 必须真正执行
 * supplier 内的双重确认逻辑；战斗存储与事件发布使用 mock。
 */
class RoomCleanupServiceTest {

    private final MultiplayerBattleStore battleStore = mock(MultiplayerBattleStore.class);
    private final RoomEventPublisher eventPublisher = mock(RoomEventPublisher.class);
    private final DistributedLockService lockService = new LocalDistributedLockService();

    private RoomCleanupService service(RoomStore roomStore, long idleMinutes) {
        return new RoomCleanupService(roomStore, battleStore, lockService, eventPublisher, idleMinutes);
    }

    private static Room room(String code, LocalDateTime lastActiveAt) {
        Room room = new Room(code, "host");
        room.setLastActiveAt(lastActiveAt);
        return room;
    }

    @Test
    void dissolvesIdleRoomAndKeepsActiveRoom() {
        InMemoryRoomStore store = new InMemoryRoomStore();
        store.save(room("IDLEROOM", LocalDateTime.now()));
        store.save(room("ACTIVERO", LocalDateTime.now()));
        // save() 会盖章为当前时间；通过 get 拿到同一引用把闲置房间拨回一小时前
        store.get("IDLEROOM").setLastActiveAt(LocalDateTime.now().minusHours(1));

        int removed = service(store, 30).sweep();

        assertEquals(1, removed);
        assertFalse(store.exists("IDLEROOM"));
        assertTrue(store.exists("ACTIVERO"));
        verify(eventPublisher).broadcastSystemMessage("IDLEROOM", "房间长时间无活动，已自动解散");
        verify(battleStore).remove("IDLEROOM");
    }

    @Test
    void skipsIdleRoomWithLiveBattleState() {
        InMemoryRoomStore store = new InMemoryRoomStore();
        store.save(room("FIGHTING", LocalDateTime.now()));
        store.get("FIGHTING").setLastActiveAt(LocalDateTime.now().minusHours(1));
        when(battleStore.exists("FIGHTING")).thenReturn(true);

        int removed = service(store, 30).sweep();

        assertEquals(0, removed);
        assertTrue(store.exists("FIGHTING"));
        verify(battleStore, never()).remove(anyString());
    }

    @Test
    void doubleCheckSkipsRoomThatBecameActiveInsideLock() {
        Room staleSnapshot = room("REVIVED", LocalDateTime.now().minusHours(1));
        RoomStore store = mock(RoomStore.class);
        when(store.findAll()).thenReturn(List.of(staleSnapshot));
        when(store.get("REVIVED")).thenReturn(room("REVIVED", LocalDateTime.now()));

        int removed = service(store, 30).sweep();

        assertEquals(0, removed);
        verify(store, never()).remove(anyString());
    }

    @Test
    void doubleCheckSkipsRoomAlreadyRemovedByAnotherInstance() {
        Room staleSnapshot = room("GONE", LocalDateTime.now().minusHours(1));
        RoomStore store = mock(RoomStore.class);
        when(store.findAll()).thenReturn(List.of(staleSnapshot));
        when(store.get("GONE")).thenReturn(null);

        int removed = service(store, 30).sweep();

        assertEquals(0, removed);
        verify(store, never()).remove(anyString());
    }

    @Test
    void skipsLegacyRoomWithoutLastActiveAt() {
        Room legacy = room("LEGACY00", null);
        RoomStore store = mock(RoomStore.class);
        when(store.findAll()).thenReturn(List.of(legacy));

        int removed = service(store, 30).sweep();

        assertEquals(0, removed);
        verify(store, never()).get(anyString());
        verify(store, never()).remove(anyString());
    }

    @Test
    void storageFailureReturnsZeroWithoutThrowing() {
        RoomStore store = mock(RoomStore.class);
        when(store.findAll()).thenThrow(new StorageUnavailableException("down", new RuntimeException("down")));

        assertEquals(0, service(store, 30).sweep());
    }
}