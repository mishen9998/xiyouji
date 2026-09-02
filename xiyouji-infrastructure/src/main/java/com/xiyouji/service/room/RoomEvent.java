package com.xiyouji.service.room;

import java.time.Instant;
import java.util.UUID;

/** Serializable envelope used to fan room events out between app instances. */
public class RoomEvent {

    private String eventId;
    private String eventType;
    private String roomCode;
    private long stateVersion;
    private String sourceInstance;
    private Instant occurredAt;
    private Object payload;

    public RoomEvent() {
    }

    public RoomEvent(String eventType, String roomCode, long stateVersion,
                     String sourceInstance, Object payload) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.roomCode = roomCode;
        this.stateVersion = stateVersion;
        this.sourceInstance = sourceInstance;
        this.occurredAt = Instant.now();
        this.payload = payload;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public long getStateVersion() { return stateVersion; }
    public void setStateVersion(long stateVersion) { this.stateVersion = stateVersion; }
    public String getSourceInstance() { return sourceInstance; }
    public void setSourceInstance(String sourceInstance) { this.sourceInstance = sourceInstance; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    public String destination() {
        return switch (eventType) {
            case "BATTLE_UPDATED" -> "/topic/room/" + roomCode + "/battle";
            default -> "/topic/room/" + roomCode;
        };
    }
}
