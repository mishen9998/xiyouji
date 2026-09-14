package com.xiyouji.controller;

import com.xiyouji.dto.request.BattlePlayRequest;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.service.MultiplayerBattleService;
import com.xiyouji.service.room.RoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多人战斗控制器单元测试
 * 手工构造 Controller + mock 服务，锁定参数转发与鉴权契约。
 */
@DisplayName("多人战斗控制器测试")
class MultiplayerBattleControllerTest {

    private static final String ROOM = "ROOM0001";
    private static final String USER = "user-1";

    private MultiplayerBattleService battleService;
    private RoomService roomService;
    private MultiplayerBattleController controller;

    @BeforeEach
    void setUp() {
        battleService = mock(MultiplayerBattleService.class);
        roomService = mock(RoomService.class);
        controller = new MultiplayerBattleController(battleService, roomService);
        loginAs(USER);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @Test
    @DisplayName("startBattle: 未登录抛异常")
    void startBattle_requiresLogin() {
        SecurityContextHolder.clearContext();

        assertThrows(InvalidActionException.class, () -> controller.startBattle(ROOM, null, 0L));
    }

    @Test
    @DisplayName("startBattle: 转发参数并返回战斗信息")
    void startBattle_forwardsAndReturnsBattleInfo() {
        when(battleService.getBattleInfo(ROOM)).thenReturn(Map.of("roomCode", ROOM));

        Map<String, Object> info = controller.startBattle(ROOM, "k1", 3L);

        verify(battleService).startBattle(ROOM, USER, 3L, "k1");
        assertEquals(ROOM, info.get("roomCode"));
    }

    @Test
    @DisplayName("playCard: 转发手牌索引与幂等/版本参数")
    void playCard_forwardsHandIndex() {
        BattlePlayRequest request = new BattlePlayRequest();
        request.setHandIndex(2);
        when(battleService.getBattleInfo(ROOM)).thenReturn(Map.of());

        controller.playCard(ROOM, request, "k1", 5L);

        verify(battleService).playCard(ROOM, USER, 2, 5L, "k1");
    }

    @Test
    @DisplayName("endTurn: 转发参数")
    void endTurn_forwards() {
        when(battleService.getBattleInfo(ROOM)).thenReturn(Map.of());

        controller.endTurn(ROOM, "k1", 0L);

        verify(battleService).endTurn(ROOM, USER, 0L, "k1");
    }

    @Test
    @DisplayName("claimReward: 缺少卡牌名抛异常")
    void claimReward_missingCardName_throws() {
        assertThrows(InvalidActionException.class,
                () -> controller.claimReward(ROOM, Map.of(), "k1", 0L));
    }

    @Test
    @DisplayName("claimReward: 转发卡牌名与参数")
    void claimReward_forwardsCardName() {
        when(battleService.getBattleInfo(ROOM)).thenReturn(Map.of());

        controller.claimReward(ROOM, Map.of("cardName", "挥棒"), "k1", 0L);

        verify(battleService).claimReward(ROOM, USER, "挥棒", 0L, "k1");
    }

    @Test
    @DisplayName("skipReward: 转发参数")
    void skipReward_forwards() {
        when(battleService.getBattleInfo(ROOM)).thenReturn(Map.of());

        controller.skipReward(ROOM, "k1", 0L);

        verify(battleService).skipReward(ROOM, USER, 0L, "k1");
    }

    @Test
    @DisplayName("getBattleState: 直通战斗信息")
    void getBattleState_passesThrough() {
        when(battleService.getBattleInfo(ROOM)).thenReturn(Map.of("stateVersion", 7));

        assertEquals(7, controller.getBattleState(ROOM).get("stateVersion"));
    }

    @Test
    @DisplayName("battleExists: 直通房间存在性")
    void battleExists_passesThrough() {
        when(roomService.roomExists(ROOM)).thenReturn(true);

        assertTrue(controller.battleExists(ROOM));
    }

    @Test
    @DisplayName("returnToMap: 转发参数并返回结果")
    void returnToMap_forwardsAndReturnsOutcome() {
        Map<String, Object> outcome = Map.of("message", "返回地图探索");
        when(battleService.returnToMap(ROOM, USER, 0L, "k1")).thenReturn(outcome);

        assertEquals(outcome, controller.returnToMap(ROOM, "k1", 0L));
    }
}