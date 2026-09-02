package com.xiyouji.service;

import com.xiyouji.service.room.InMemoryMultiplayerBattleStore;
import com.xiyouji.service.room.InMemoryRoomStore;
import com.xiyouji.service.room.MultiplayerBattleState;
import com.xiyouji.service.room.Room;
import com.xiyouji.service.room.RoomLockKeys;
import com.xiyouji.service.session.GameSession;
import com.xiyouji.service.session.InMemorySessionStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DistributedStateTest {

    @Test
    void roomLockKeyIsSharedByEveryRoomOperation() {
        assertEquals("xiyouji:lock:room:ABCD1234", RoomLockKeys.forRoom("ABCD1234"));
    }

    @Test
    void inMemoryStoresExposeMonotonicStateVersions() {
        InMemoryRoomStore rooms = new InMemoryRoomStore();
        Room room = new Room("ABCD1234", "host");
        rooms.save(room);
        rooms.save(room);
        assertEquals(2, room.getStateVersion());

        InMemoryMultiplayerBattleStore battles = new InMemoryMultiplayerBattleStore();
        MultiplayerBattleState battle = new MultiplayerBattleState("ABCD1234");
        IntStream.range(0, 5).forEach(ignored -> battles.save(battle));
        assertEquals(5, battle.getStateVersion());
        assertTrue(battles.exists("ABCD1234"));
    }

    @Test
    void idempotencyKeyIsAcceptedOnlyOnce() {
        LocalIdempotencyStore store = new LocalIdempotencyStore();
        assertTrue(store.tryAcquire("claim:room:user:key", Duration.ofMinutes(10)));
        assertTrue(!store.tryAcquire("claim:room:user:key", Duration.ofMinutes(10)));
    }

    @Test
    void staleSessionObjectCannotOverwriteNewerVersion() {
        InMemorySessionStore sessions = new InMemorySessionStore();
        GameSession current = new GameSession("session", null, java.util.List.of());
        sessions.put("session", current); // version 1

        GameSession stale = new GameSession("session", null, java.util.List.of());
        assertThrows(com.xiyouji.exception.StateVersionConflictException.class,
                () -> sessions.put("session", stale));
        assertEquals(1, sessions.get("session").getStateVersion());
    }
}
