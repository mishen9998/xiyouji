package com.xiyouji.service.room;

/**
 * Redis lock keys shared by all operations that mutate a room aggregate.
 *
 * Keeping one key per room is intentional: a room update can touch both the
 * room metadata and its multiplayer battle state, so separate action locks
 * would still allow lost updates across operations.
 */
public final class RoomLockKeys {

    private static final String PREFIX = "xiyouji:lock:room:";

    private RoomLockKeys() {
    }

    public static String forRoom(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            throw new IllegalArgumentException("roomCode must not be blank");
        }
        return PREFIX + roomCode;
    }
}
