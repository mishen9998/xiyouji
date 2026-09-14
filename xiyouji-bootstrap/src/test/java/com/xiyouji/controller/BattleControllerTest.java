package com.xiyouji.controller;

import com.xiyouji.dto.PlayerSummaryAssembler;
import com.xiyouji.dto.request.BattlePlayRequest;
import com.xiyouji.dto.request.ChooseCardRequest;
import com.xiyouji.dto.response.PlayerDTO;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.service.BattleService;
import com.xiyouji.service.CommandIdempotencyService;
import com.xiyouji.service.GameService;
import com.xiyouji.service.IdempotencyStore;
import com.xiyouji.service.IdempotentCommandRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单人战斗控制器单元测试
 * 手工构造 Controller + mock 服务，锁定参数转发、幂等重放与异常兜底契约。
 */
@DisplayName("单人战斗控制器测试")
class BattleControllerTest {

    private static final String SESSION = "session-1";
    private static final String USER = "user-1";
    private static final String KEY = "k1";

    private BattleService battleService;
    private GameService gameService;
    private PlayerSummaryAssembler playerSummaryAssembler;
    private CommandIdempotencyService idempotency;
    private BattleController controller;

    @BeforeEach
    void setUp() {
        battleService = mock(BattleService.class);
        gameService = mock(GameService.class);
        playerSummaryAssembler = mock(PlayerSummaryAssembler.class);
        idempotency = mock(CommandIdempotencyService.class);
        controller = new BattleController(battleService, gameService, playerSummaryAssembler,
                new IdempotentCommandRunner(idempotency));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("startBattle: 转发版本参数并返回战斗信息")
    void startBattle_forwardsAndReturnsBattleInfo() {
        Map<String, Object> info = Map.of("inBattle", true);
        when(battleService.getBattleInfo(SESSION)).thenReturn(info);

        Map<String, Object> result = controller.startBattle(SESSION, 3L, KEY);

        verify(battleService).startBattle(SESSION, 3L, USER);
        assertSame(info, result);
        verify(idempotency).completeResponse(eq("game:battle:start:" + USER + ":" + SESSION), eq(KEY), any(), eq(info));
    }

    @Test
    @DisplayName("playCard: 转发手牌索引与版本参数")
    void playCard_forwardsHandIndex() {
        BattlePlayRequest request = new BattlePlayRequest();
        request.setHandIndex(2);
        when(battleService.playCardAndResolve(SESSION, 2, 5L, USER)).thenReturn(Map.of("inBattle", true));

        controller.playCard(SESSION, request, 5L, KEY);

        verify(battleService).playCardAndResolve(SESSION, 2, 5L, USER);
    }

    @Test
    @DisplayName("endTurn: 转发版本参数")
    void endTurn_forwards() {
        when(battleService.endTurnAndResolve(SESSION, 0L, USER)).thenReturn(Map.of("inBattle", true));

        controller.endTurn(SESSION, 0L, KEY);

        verify(battleService).endTurnAndResolve(SESSION, 0L, USER);
    }

    @Test
    @DisplayName("battleState: 校验归属后直通战斗信息")
    void battleState_checksOwnershipAndPassesThrough() {
        when(battleService.getBattleInfo(SESSION)).thenReturn(Map.of("stateVersion", 7));

        assertEquals(7, controller.battleState(SESSION).get("stateVersion"));

        verify(gameService).getSessionForUser(SESSION, USER);
    }

    @Test
    @DisplayName("chooseCardReward: 转发索引并附带玩家摘要")
    void chooseCardReward_forwardsAndAttachesPlayerSummary() {
        ChooseCardRequest request = new ChooseCardRequest();
        request.setCardIndex(1);
        Map<String, Object> result = new HashMap<>();
        when(battleService.chooseCardReward(SESSION, 1, 6L, USER)).thenReturn(result);
        var session = mock(com.xiyouji.service.session.GameSession.class);
        when(gameService.getSessionForUser(SESSION, USER)).thenReturn(session);
        var player = mock(com.xiyouji.model.GameCharacter.class);
        when(session.getPlayer()).thenReturn(player);
        PlayerDTO summary = new PlayerDTO();
        when(playerSummaryAssembler.toPlayerSummary(player)).thenReturn(summary);

        Map<String, Object> outcome = controller.chooseCardReward(SESSION, request, 6L, KEY);

        assertSame(summary, outcome.get("player"));
        verify(battleService).chooseCardReward(SESSION, 1, 6L, USER);
    }

    @Test
    @DisplayName("skipReward: 转发版本参数")
    void skipReward_forwards() {
        when(battleService.skipReward(SESSION, 0L, USER)).thenReturn(Map.of("success", true));

        controller.skipReward(SESSION, 0L, KEY);

        verify(battleService).skipReward(SESSION, 0L, USER);
    }

    @Test
    @DisplayName("幂等重放: 命中缓存直接返回，不再执行命令")
    void replay_returnsCachedResponse() {
        var previous = new IdempotencyStore.Entry("fp", "{\"inBattle\":true}", true);
        when(idempotency.begin(any(), eq(KEY), any())).thenReturn(previous);
        Map<String, Object> cached = Map.of("inBattle", true);
        when(idempotency.replay(previous, Map.class)).thenReturn(cached);

        Map<String, Object> result = controller.endTurn(SESSION, 0L, KEY);

        assertSame(cached, result);
        verify(battleService, never()).endTurnAndResolve(SESSION, 0L, USER);
    }

    @Test
    @DisplayName("幂等重放: 已完成后未缓存时返回最新战斗信息")
    void completedReplay_returnsCurrentBattleInfo() {
        var previous = new IdempotencyStore.Entry("fp", " ", true);
        when(idempotency.begin(any(), eq(KEY), any())).thenReturn(previous);
        when(battleService.getBattleInfo(SESSION)).thenReturn(Map.of("stateVersion", 9));

        Map<String, Object> result = controller.endTurn(SESSION, 0L, KEY);

        assertEquals(9, result.get("stateVersion"));
        verify(battleService, never()).endTurnAndResolve(SESSION, 0L, USER);
    }

    @Test
    @DisplayName("命令失败: 中断幂等标记并重抛")
    void failure_abortsAndRethrows() {
        when(battleService.startBattle(SESSION, 3L, USER)).thenThrow(new InvalidActionException("超出范围"));

        assertThrows(InvalidActionException.class, () -> controller.startBattle(SESSION, 3L, KEY));

        verify(idempotency).abort(eq("game:battle:start:" + USER + ":" + SESSION), eq(KEY));
    }
}