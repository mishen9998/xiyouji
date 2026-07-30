package com.xiyouji.service.room;

import com.xiyouji.dto.response.room.RoomDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 房间事件广播器
 * 通过 STOMP 将房间/战斗状态变化推送到对应频道，所有订阅该房间的客户端实时收到更新。
 *
 * 频道约定：
 *   /topic/room/{code}        - 房间元数据变化（玩家进出/准备/选角）
 *   /topic/room/{code}/battle - 战斗状态变化（出牌/回合/伤害结算）
 */
@Component
public class RoomEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RoomEventBroadcaster.class);

    private static final String ROOM_TOPIC_PREFIX = "/topic/room/";
    private static final String BATTLE_TOPIC_SUFFIX = "/battle";

    private final SimpMessagingTemplate messagingTemplate;

    public RoomEventBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 广播房间状态变化（玩家加入/退出/准备/选角）
     */
    public void broadcastRoomUpdate(String code, RoomDTO room) {
        String destination = ROOM_TOPIC_PREFIX + code;
        messagingTemplate.convertAndSend(destination, room);
        log.debug("Broadcasted room update to {}: {} players", destination, room.getPlayerCount());
    }

    /**
     * 广播战斗状态变化（出牌/回合切换/伤害结算）
     * battleState 为战斗状态对象（阶段3实现后填充具体类型）
     */
    public void broadcastBattleUpdate(String code, Object battleState) {
        String destination = ROOM_TOPIC_PREFIX + code + BATTLE_TOPIC_SUFFIX;
        messagingTemplate.convertAndSend(destination, battleState);
        log.debug("Broadcasted battle update to {}", destination);
    }

    /**
     * 广播系统消息（如"玩家X加入了房间"、"游戏开始"等提示）
     */
    public void broadcastSystemMessage(String code, String message) {
        String destination = ROOM_TOPIC_PREFIX + code;
        messagingTemplate.convertAndSend(destination, Map.of(
                "type", "SYSTEM_MESSAGE",
                "message", message,
                "timestamp", System.currentTimeMillis()
        ));
    }
}
