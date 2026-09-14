package com.xiyouji.controller;

import com.xiyouji.dto.PlayerSummaryAssembler;
import com.xiyouji.dto.request.EventRequest;
import com.xiyouji.dto.request.MoveRequest;
import com.xiyouji.dto.request.NewGameRequest;
import com.xiyouji.dto.request.RemoveCardRequest;
import com.xiyouji.dto.response.PlayerDTO;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.service.CommandIdempotencyService;
import com.xiyouji.service.GameEventProcessor;
import com.xiyouji.service.GameService;
import com.xiyouji.service.IdempotencyStore;
import com.xiyouji.service.IdempotentCommandRunner;
import com.xiyouji.service.session.GameSession;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * GameController 单元测试
 * 验证参数转发、幂等重放与事件委托契约（配合真实 IdempotentCommandRunner）。
 */
@DisplayName("游戏主控制器")
@ExtendWith(MockitoExtension.class)
class GameControllerTest {

    @Mock private GameService gameService;
    @Mock private GameEventProcessor eventProcessor;
    @Mock private PlayerSummaryAssembler playerSummaryAssembler;
    @Mock private CommandIdempotencyService idempotency;
    @Mock private GameSession session;

    private GameController controller;

    private static final String USER = "guest_1";
    private static final String SESSION = "session-1";
    private static final String KEY = "k1";

    @BeforeEach
    void setUp() {
        controller = new GameController(gameService, eventProcessor, playerSummaryAssembler,
                new IdempotentCommandRunner(idempotency));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER, null, List.of()));
        lenient().when(session.getStateVersion()).thenReturn(7L);
        lenient().when(session.getPlayer()).thenReturn(null);
        lenient().when(playerSummaryAssembler.toPlayerSummary(any())).thenReturn(mock(PlayerDTO.class));
    }

    @Test
    @DisplayName("新游戏: 解析角色、创建会话并返回状态")
    void newGame_createsSessionAndReturnsResponse() {
        NewGameRequest request = new NewGameRequest();
        request.setCharacterClass("sunwukong");
        GameSession created = mock(GameSession.class);
        when(created.getStateVersion()).thenReturn(0L);
        when(created.getPlayer()).thenReturn(null);
        when(gameService.newGame(any(), eq(CharacterClass.SUN_WUKONG), eq(USER))).thenReturn(created);

        Map<String, Object> result = controller.newGame(request, KEY);

        verify(gameService).newGame(any(), eq(CharacterClass.SUN_WUKONG), eq(USER));
        assertEquals(true, result.get("success"));
        assertNotNull(result.get("sessionId"));
        assertEquals(0L, result.get("stateVersion"));
    }

    @Test
    @DisplayName("新游戏完成重放: 用记录的 sessionId 重建响应")
    void newGame_completedReplay_usesRecordedSessionId() {
        NewGameRequest request = new NewGameRequest();
        request.setCharacterClass("SUN_WUKONG");
        when(idempotency.begin(any(), eq(KEY), any()))
                .thenReturn(new IdempotencyStore.Entry("fp", "old-session", true));
        when(idempotency.replay(any(), eq(Map.class))).thenReturn(null);
        GameSession existing = mock(GameSession.class);
        when(existing.getStateVersion()).thenReturn(9L);
        when(existing.getPlayer()).thenReturn(null);
        when(gameService.getSessionForUser("old-session", USER)).thenReturn(existing);

        Map<String, Object> result = controller.newGame(request, KEY);

        assertEquals("old-session", result.get("sessionId"));
        assertEquals(9L, result.get("stateVersion"));
        verify(gameService, never()).newGame(any(), any(), any());
    }

    @Test
    @DisplayName("无效角色: 直接抛出异常")
    void newGame_invalidClass_throws() {
        NewGameRequest request = new NewGameRequest();
        request.setCharacterClass("蜘蛛侠");

        assertThrows(InvalidActionException.class, () -> controller.newGame(request, KEY));
        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("移动节点: 转发并组装 node/eventType/stateVersion")
    void move_executesAndReturnsNodeInfo() {
        MoveRequest request = new MoveRequest();
        request.setNodeId("n1");
        MapNode node = mock(MapNode.class);
        when(node.getType()).thenReturn("BATTLE");
        when(gameService.moveToNode(SESSION, "n1", 3L, USER)).thenReturn(node);
        when(gameService.getSession(SESSION)).thenReturn(session);

        Map<String, Object> result = controller.move(SESSION, request, 3L, KEY);

        assertSame(node, result.get("node"));
        assertEquals("battle", result.get("eventType"));
        assertEquals(7L, result.get("stateVersion"));
    }

    @Test
    @DisplayName("节点事件: 会话锁内委托给事件处理器")
    void handleEvent_delegatesToEventProcessorUnderLock() {
        EventRequest request = new EventRequest();
        request.setAction("rest");
        when(gameService.withSessionLock(eq(SESSION), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        when(eventProcessor.process(SESSION, request, 2L, USER)).thenReturn(Map.of("message", "休息完毕"));

        Map<String, Object> result = controller.handleEvent(SESSION, request, 2L, KEY);

        verify(eventProcessor).process(SESSION, request, 2L, USER);
        assertEquals("休息完毕", result.get("message"));
    }

    @Test
    @DisplayName("删除会话完成重放: 返回 success")
    void deleteSession_completedReplay_returnsSuccess() {
        when(idempotency.begin(any(), eq(KEY), any()))
                .thenReturn(new IdempotencyStore.Entry("fp", "{\"success\":true}", true));
        when(idempotency.replay(any(), eq(Map.class))).thenReturn(null);

        Map<String, Object> result = controller.deleteSession(SESSION, 3L, KEY);

        assertEquals(true, result.get("success"));
        verify(gameService, never()).deleteSession(any(), anyLong(), any());
    }

    @Test
    @DisplayName("移除卡牌: 转发并附最新玩家摘要")
    void removeCard_forwardsAndReturnsPlayerSummary() {
        RemoveCardRequest request = new RemoveCardRequest();
        request.setIndex(2);
        when(gameService.getSessionForUser(SESSION, USER)).thenReturn(session);
        PlayerDTO summary = mock(PlayerDTO.class);
        when(playerSummaryAssembler.toPlayerSummary(null)).thenReturn(summary);

        Map<String, Object> result = controller.removeCard(SESSION, request, 0L, KEY);

        verify(gameService).removeCardFromDeck(SESSION, 2, 0L, USER);
        assertEquals(true, result.get("success"));
        assertSame(summary, result.get("player"));
    }
}