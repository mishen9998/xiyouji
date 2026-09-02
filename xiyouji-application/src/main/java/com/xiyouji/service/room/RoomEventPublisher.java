package com.xiyouji.service.room;

import com.xiyouji.dto.response.room.RoomDTO;

/**
 * Application port for publishing room and battle events.
 * Implementations may use a local broker (standalone) or a shared event bus
 * (distributed deployment).
 */
public interface RoomEventPublisher {

    void broadcastRoomUpdate(String code, RoomDTO room);

    void broadcastBattleUpdate(String code, Object battleState);

    void broadcastSystemMessage(String code, String message);
}
