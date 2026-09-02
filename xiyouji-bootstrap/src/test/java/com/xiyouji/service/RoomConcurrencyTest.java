package com.xiyouji.service;

import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.port.CharacterRepositoryPort;
import com.xiyouji.port.RelicRepositoryPort;
import com.xiyouji.service.room.InMemoryRoomStore;
import com.xiyouji.service.room.LocalDistributedLockService;
import com.xiyouji.service.room.MultiplayerMapService;
import com.xiyouji.service.room.RoomService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomConcurrencyTest {

    @Test
    void concurrentJoinNeverExceedsFivePlayers() throws Exception {
        RoomService service = newService();
        String code = service.createRoom("host", "host").getCode();

        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String user = "user-" + i;
                futures.add(pool.submit(() -> {
                    try {
                        service.joinRoom(code, user, user);
                    } catch (RuntimeException ignored) {
                        // Full-room responses are expected for losing requests.
                    }
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            pool.shutdownNow();
        }

        assertEquals(5, service.getRoomEntity(code).getPlayerCount());
    }

    @Test
    void sameCharacterCannotBeSelectedByTwoPlayers() {
        RoomService service = newService();
        String code = service.createRoom("host", "host").getCode();
        service.joinRoom(code, "guest", "guest");

        service.selectCharacter(code, "host", CharacterClass.SUN_WUKONG);
        assertThrows(RuntimeException.class,
                () -> service.selectCharacter(code, "guest", CharacterClass.SUN_WUKONG));
    }

    private RoomService newService() {
        return new RoomService(
                new InMemoryRoomStore(),
                org.mockito.Mockito.mock(MultiplayerMapService.class),
                org.mockito.Mockito.mock(CharacterRepositoryPort.class),
                org.mockito.Mockito.mock(CardRepositoryPort.class),
                org.mockito.Mockito.mock(RelicRepositoryPort.class),
                new LocalDistributedLockService());
    }
}
