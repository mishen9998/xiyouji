package com.xiyouji.service.battle;

import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.service.GameService;
import com.xiyouji.service.session.BattleState;
import com.xiyouji.service.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 单人战斗回合组件
 *
 * 负责：结束玩家回合（触发敌人行动）、应用 TURN_START 遗物效果并保存会话。
 */
@Component
public class SoloTurnCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SoloTurnCoordinator.class);

    private final GameService gameService;
    private final SoloRelicTriggers relicTriggers;

    public SoloTurnCoordinator(GameService gameService, SoloRelicTriggers relicTriggers) {
        this.gameService = gameService;
        this.relicTriggers = relicTriggers;
    }

    /** 结束回合 */
    public BattleState endTurn(String sessionId) {
        GameSession session = gameService.getSession(sessionId);
        BattleState battle = session.getBattle();
        if (battle == null) throw new InvalidActionException("不在战斗中");

        battle.endPlayerTurn(session.getPlayer());

        // ★ 宝物回合开始效果：九转金丹（TURN_START;HEAL:N）、御赐琉璃盏（TURN_START;DRAW:N）
        if (!battle.isBattleOver()) relicTriggers.applyTurnStartEffects(session.getPlayer(),
                (relic, kind, desc) -> battle.getCombatLog().add(
                        ("HEAL".equals(kind) ? "💊 " : "🍶 ") + relic.getName() + "触发！" + desc));

        gameService.saveSession(session);
        log.debug("结束回合: sessionId={}, turn={}", sessionId, battle.getTurnNumber());
        return battle;
    }
}
