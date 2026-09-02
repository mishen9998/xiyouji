package com.xiyouji.service.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyouji.dto.response.room.RoomDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Redis-backed room event publisher. Each application instance publishes
 * envelopes to Redis; RedisRoomEventSubscriber fans them out through the
 * local STOMP broker so clients connected to any instance receive the update.
 */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "true")
public class RedisRoomEventPublisher implements RoomEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisRoomEventPublisher.class);
    public static final String CHANNEL = "xiyouji:room-events";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String instanceId;

    public RedisRoomEventPublisher(StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${INSTANCE_ID:${instance.id:unknown}}") String instanceId) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.instanceId = instanceId;
    }

    /**
     * 广播房间状态变化（玩家加入/退出/准备/选角）
     */
    public void broadcastRoomUpdate(String code, RoomDTO room) {
        publish(new RoomEvent("ROOM_UPDATED", code, room.getStateVersion(), instanceId, room));
    }

    /**
     * 广播战斗状态变化（出牌/回合切换/伤害结算）
     * battleState 为战斗状态对象（阶段3实现后填充具体类型）
     */
    public void broadcastBattleUpdate(String code, Object battleState) {
        publish(new RoomEvent("BATTLE_UPDATED", code, stateVersion(battleState), instanceId, battleState));
    }

    /**
     * 广播系统消息（如"玩家X加入了房间"、"游戏开始"等提示）
     */
    public void broadcastSystemMessage(String code, String message) {
        publish(new RoomEvent("SYSTEM_MESSAGE", code, 0, instanceId, Map.of(
                "type", "SYSTEM_MESSAGE",
                "message", message,
                "timestamp", System.currentTimeMillis()
        )));
    }

    private void publish(RoomEvent event) {
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            // State is already persisted in Redis. A failed notification is
            // recoverable by the client's REST refresh/reconnect path.
            log.error("Unable to serialize room event: room={}, type={}",
                    event.getRoomCode(), event.getEventType(), e);
        } catch (Exception e) {
            log.warn("Unable to publish room event: room={}, type={}, error={}",
                    event.getRoomCode(), event.getEventType(), e.getMessage());
        }
    }

    private long stateVersion(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object value = map.get("stateVersion");
            if (value instanceof Number number) return number.longValue();
        }
        return 0;
    }
}
