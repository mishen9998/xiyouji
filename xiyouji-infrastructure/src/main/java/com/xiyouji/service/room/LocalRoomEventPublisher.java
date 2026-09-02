package com.xiyouji.service.room;

import com.xiyouji.dto.response.room.RoomDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Local-only event publisher used by the standalone H2 profile. */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "false", matchIfMissing = true)
public class LocalRoomEventPublisher implements RoomEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public LocalRoomEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void broadcastRoomUpdate(String code, RoomDTO room) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("code", room.getCode());
        payload.put("hostUserId", room.getHostUserId());
        payload.put("players", room.getPlayers());
        payload.put("playerCount", room.getPlayerCount());
        payload.put("status", room.getStatus());
        payload.put("createdAt", room.getCreatedAt());
        payload.put("floor", room.getFloor());
        payload.put("maxLayer", room.getMaxLayer());
        payload.put("map", room.getMap());
        payload.put("currentNode", room.getCurrentNode());
        payload.put("bonfireUpgradesLeft", room.getBonfireUpgradesLeft());
        payload.put("stateVersion", room.getStateVersion());
        payload.put("eventId", UUID.randomUUID().toString());
        messagingTemplate.convertAndSend("/topic/room/" + code, payload);
    }

    @Override
    public void broadcastBattleUpdate(String code, Object battleState) {
        if (battleState instanceof Map<?, ?> map) {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            map.forEach((key, value) -> payload.put(String.valueOf(key), value));
            payload.put("eventId", UUID.randomUUID().toString());
            messagingTemplate.convertAndSend("/topic/room/" + code + "/battle", payload);
        } else {
            messagingTemplate.convertAndSend("/topic/room/" + code + "/battle", battleState);
        }
    }

    @Override
    public void broadcastSystemMessage(String code, String message) {
        messagingTemplate.convertAndSend("/topic/room/" + code,
                Map.of("type", "SYSTEM_MESSAGE", "message", message,
                        "timestamp", System.currentTimeMillis(), "eventId", UUID.randomUUID().toString()));
    }
}
