package com.xiyouji.service.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bridges shared Redis room events to this instance's local STOMP broker. */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "true")
public class RedisRoomEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisRoomEventSubscriber.class);

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public RedisRoomEventSubscriber(ObjectMapper objectMapper,
                                    SimpMessagingTemplate messagingTemplate) {
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RoomEvent event = objectMapper.readValue(message.getBody(), RoomEvent.class);
            Object payload = event.getPayload();
            if (payload instanceof Map<?, ?> map) {
                Map<String, Object> enriched = new LinkedHashMap<>();
                map.forEach((key, value) -> enriched.put(String.valueOf(key), value));
                enriched.put("eventId", event.getEventId());
                enriched.put("stateVersion", event.getStateVersion());
                payload = enriched;
            }
            messagingTemplate.convertAndSend(event.destination(), payload);
        } catch (Exception e) {
            log.warn("Unable to consume room event: {}", e.getMessage());
        }
    }
}
