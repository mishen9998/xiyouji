package com.xiyouji.controller;

import com.xiyouji.controller.support.CharacterClassParser;
import com.xiyouji.controller.support.CurrentUserResolver;
import com.xiyouji.dto.request.room.JoinRoomRequest;
import com.xiyouji.dto.request.room.SelectCharacterRequest;
import com.xiyouji.dto.response.room.RoomDTO;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.service.CommandIdempotencyService;
import com.xiyouji.service.IdempotentCommandRunner;
import com.xiyouji.service.room.Room;
import com.xiyouji.service.room.RoomEventPublisher;
import com.xiyouji.service.room.RoomPlayer;
import com.xiyouji.service.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RoomController 单元测试
 * 验证 9 个命令端点的参数转发、广播副作用与幂等重放（配合真实 IdempotentCommandRunner）。
 */
@DisplayName("多人房间控制器")
@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock private RoomService roomService;
    @Mock private RoomEventPublisher broadcaster;
    @Mock private CommandIdempotencyService idempotency;

    private RoomController controller;

    private static final String USER = "guest_1";
    private static final String CODE = "ABCD1234";
    private static final String KEY = "k1";

    @BeforeEach
    void setUp() {
        controller = new RoomController(roomService, broadcaster,
                new IdempotentCommandRunner(idempotency),
                new CurrentUserResolver(), new CharacterClassParser());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER, null, List.of()));
    }

    private RoomDTO roomDTO(String code, String hostUserId) {
        RoomDTO dto = new RoomDTO();
        dto.setCode(code);
        dto.setHostUserId(hostUserId);
        dto.setPlayers(List.of(new RoomPlayer(USER, USER)));
        return dto;
    }

    @Test
    @DisplayName("创建房间: 转发服务并返回房间")
    void createRoom_forwardsAndReturns() {
        RoomDTO dto = roomDTO(CODE, USER);
        when(roomService.createRoom(USER, USER)).thenReturn(dto);

        RoomDTO result = controller.createRoom(KEY);

        assertEquals(CODE, result.getCode());
        verify(idempotency).completeResponse(eq("room:create:" + USER), eq(KEY), any(), eq(dto));
    }

    @Test
    @DisplayName("加入房间: 转发并广播更新与系统消息")
    void joinRoom_forwardsAndBroadcasts() {
        JoinRoomRequest request = new JoinRoomRequest();
        request.setCode(CODE);
        RoomDTO dto = roomDTO(CODE, USER);
        when(roomService.joinRoom(CODE, USER, USER, 3L)).thenReturn(dto);

        RoomDTO result = controller.joinRoom(request, 3L, KEY);

        assertEquals(CODE, result.getCode());
        verify(broadcaster).broadcastRoomUpdate(CODE, dto);
        verify(broadcaster).broadcastSystemMessage(CODE, USER + " 加入了房间");
    }

    @Test
    @DisplayName("房主退出: 广播解散通知并返回 dissolved")
    void leaveRoom_hostDissolvesRoom() {
        when(roomService.getRoomEntity(CODE)).thenReturn(new Room(CODE, USER));

        Map<String, Object> result = controller.leaveRoom(CODE, 3L, KEY);

        verify(roomService).leaveRoom(CODE, USER, 3L);
        assertEquals(true, result.get("dissolved"));
        verify(broadcaster).broadcastSystemMessage(CODE, "房主已退出，房间已解散");
    }

    @Test
    @DisplayName("非房主退出: 广播剩余玩家并返回房间")
    void leaveRoom_guestReturnsRemainingPlayers() {
        when(roomService.getRoomEntity(CODE)).thenReturn(new Room(CODE, "other_host"));
        RoomDTO remaining = roomDTO(CODE, "other_host");
        when(roomService.getRoom(CODE)).thenReturn(remaining);

        Map<String, Object> result = controller.leaveRoom(CODE, 3L, KEY);

        assertEquals(false, result.get("dissolved"));
        assertSame(remaining, result.get("room"));
        verify(broadcaster).broadcastRoomUpdate(CODE, remaining);
    }

    @Test
    @DisplayName("切换准备: 转发并广播房间更新")
    void toggleReady_forwardsAndBroadcasts() {
        RoomDTO dto = roomDTO(CODE, USER);
        when(roomService.toggleReady(CODE, USER, 0L)).thenReturn(dto);

        RoomDTO result = controller.toggleReady(CODE, 0L, KEY);

        assertSame(dto, result);
        verify(broadcaster).broadcastRoomUpdate(CODE, dto);
    }

    @Test
    @DisplayName("选择角色: 解析枚举并转发")
    void selectCharacter_parsesAndForwards() {
        SelectCharacterRequest request = new SelectCharacterRequest();
        request.setCharacterClass("SHA_SENG");
        RoomDTO dto = roomDTO(CODE, USER);
        when(roomService.selectCharacter(CODE, USER, CharacterClass.SHA_SENG, 0L)).thenReturn(dto);

        RoomDTO result = controller.selectCharacter(CODE, request, 0L, KEY);

        assertSame(dto, result);
        verify(broadcaster).broadcastRoomUpdate(CODE, dto);
    }

    @Test
    @DisplayName("非法角色: 抛出异常且不触碰服务层")
    void selectCharacter_invalidThrows() {
        SelectCharacterRequest request = new SelectCharacterRequest();
        request.setCharacterClass("牛魔王");

        assertThrows(InvalidActionException.class, () -> controller.selectCharacter(CODE, request, 0L, KEY));
        verifyNoInteractions(roomService);
    }

    @Test
    @DisplayName("移动节点: 转发并广播房间更新")
    void moveToNode_forwardsAndBroadcasts() {
        RoomDTO dto = roomDTO(CODE, USER);
        Map<String, Object> serviceResult = Map.of("room", dto, "node", "n1", "eventType", "battle");
        when(roomService.moveToNode(CODE, "n1", 3L, USER)).thenReturn(serviceResult);

        Map<String, Object> result = controller.moveToNode(CODE, Map.of("nodeId", "n1"), 3L, KEY);

        assertSame(serviceResult, result);
        verify(broadcaster).broadcastRoomUpdate(CODE, dto);
    }

    @Test
    @DisplayName("节点事件: 转发并广播最新房间状态")
    void handleEvent_forwardsAndBroadcasts() {
        Map<String, Object> serviceResult = Map.of("stateVersion", 9L, "room", roomDTO(CODE, USER));
        when(roomService.handleEvent(CODE, USER, "rest", null, null, 2L)).thenReturn(serviceResult);
        RoomDTO latest = roomDTO(CODE, USER);
        when(roomService.getRoom(CODE)).thenReturn(latest);

        Map<String, Object> result = controller.handleEvent(CODE, Map.of("action", "rest"), 2L, KEY);

        assertSame(serviceResult, result);
        verify(broadcaster).broadcastRoomUpdate(CODE, latest);
    }

    @Test
    @DisplayName("下一层: 转发并广播系统消息")
    void nextLayer_forwardsAndBroadcasts() {
        Map<String, Object> serviceResult = Map.of("stateVersion", 11L, "room", roomDTO(CODE, USER));
        when(roomService.nextLayer(CODE, USER, 4L)).thenReturn(serviceResult);
        RoomDTO latest = roomDTO(CODE, USER);
        when(roomService.getRoom(CODE)).thenReturn(latest);

        Map<String, Object> result = controller.nextLayer(CODE, 4L, KEY);

        assertSame(serviceResult, result);
        verify(broadcaster).broadcastSystemMessage(eq(CODE), contains("层"));
    }

    @Test
    @DisplayName("开始游戏: 转发并广播游戏开始消息")
    void startGame_forwardsAndBroadcasts() {
        RoomDTO dto = roomDTO(CODE, USER);
        when(roomService.startGame(CODE, USER, 3L)).thenReturn(dto);

        RoomDTO result = controller.startGame(CODE, 3L, KEY);

        assertSame(dto, result);
        verify(broadcaster).broadcastRoomUpdate(CODE, dto);
        verify(broadcaster).broadcastSystemMessage(CODE, "游戏开始！探索第1层地图");
    }
}