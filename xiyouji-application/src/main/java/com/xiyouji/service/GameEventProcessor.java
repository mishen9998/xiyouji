package com.xiyouji.service;

import com.xiyouji.dto.PlayerSummaryAssembler;
import com.xiyouji.dto.request.EventRequest;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.Relic;
import com.xiyouji.service.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单人节点事件处理器
 *
 * 处理地图节点交互：休息（REST）、篝火升级（BONFIRE）、宝箱（TREASURE）、
 * 皇帝赐宝（EMPEROR）、商店（SHOP）、随机事件（RANDOM）。
 * 由 GameController 在会话锁内委托调用；本组件不负责持锁与幂等。
 */
@Component
public class GameEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(GameEventProcessor.class);

    private final GameService gameService;
    private final PlayerSummaryAssembler playerSummaryAssembler;
    private final com.xiyouji.service.event.EventEngine eventEngine;

    public GameEventProcessor(GameService gameService,
                              PlayerSummaryAssembler playerSummaryAssembler,
                              com.xiyouji.service.event.EventEngine eventEngine) {
        this.gameService = gameService;
        this.playerSummaryAssembler = playerSummaryAssembler;
        this.eventEngine = eventEngine;
    }

    /** 处理节点事件（调用方须已持会话锁并已校验归属与版本） */
    public Map<String, Object> process(String sessionId, EventRequest request,
                                       long expectedVersion, String username) {
        return gameService.withSessionLock(sessionId, () -> processLocked(sessionId, request, expectedVersion, username));
    }

    private Map<String, Object> processLocked(String sessionId, EventRequest request,
                                       long expectedVersion, String username) {
        String action = request.getAction() != null ? request.getAction() : "none";
        log.info("Handling event action '{}' for session: {}", action, sessionId);
        gameService.assertOwnerAndVersion(sessionId, username, expectedVersion);

        Map<String, Object> result = new HashMap<>();
        GameSession session = gameService.getSession(sessionId);
        boolean persistAfterEvent = false;
        MapNode node = session.getCurrentNode();
        if (node == null) {
            log.warn("No current node for session: {}", sessionId);
            result.put("error", "不在任何节点");
            return result;
        }

        switch (node.getType()) {
            case "REST" -> {
                if ("rest".equals(action)) {
                    gameService.heal(sessionId, session.getPlayer().getMaxHp() / 3);
                    // Redis-backed service calls deserialize their own copy;
                    // reload so the response is based on the persisted state
                    // and never writes an older snapshot back over it.
                    session = gameService.getSession(sessionId);
                    result.put("message", "休息完毕，生命值已恢复");
                    result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
                }
            }
            case "BONFIRE" -> {
                if ("upgrade".equals(action)) {
                    if (session.getBonfireUpgradesLeft() <= 0) {
                        result.put("error", "升级次数已用完");
                        result.put("bonfireUpgradesLeft", 0);
                        result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
                        break;
                    }
                    int cardIdx = request.getCardIndex() != null ? request.getCardIndex() : -1;
                    if (cardIdx >= 0) {
                        gameService.upgradeCard(sessionId, cardIdx);
                        session = gameService.getSession(sessionId);
                        Card upgraded = session.getPlayer().getDeck().get(cardIdx);
                        session.setBonfireUpgradesLeft(session.getBonfireUpgradesLeft() - 1);
                        // upgradeCard persists the card mutation itself; the
                        // remaining bonfire counter must be persisted by the
                        // outer command as part of the same locked transition.
                        persistAfterEvent = true;
                        result.put("upgraded", upgraded);
                    }
                }
                result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
                result.put("bonfireUpgradesLeft", session.getBonfireUpgradesLeft());
            }
            case "TREASURE" -> {
                Relic relic = gameService.getRandomRelic(sessionId);
                if (relic != null) {
                    session.getPlayer().getRelics().add(relic);
                    persistAfterEvent = true;
                    result.put("relic", relic);
                    result.put("message", "获得遗物: " + relic.getName());
                    log.info("Relic '{}' obtained for session: {}", relic.getName(), sessionId);
                }
            }
            case "EMPEROR" -> {
                // 唐朝皇帝赐宝：第一次进入返回三选一候选；选择后入库
                if ("choose".equals(action)) {
                    String relicName = request.getRelicName();
                    if (relicName == null || relicName.isBlank()) {
                        log.warn("Missing relicName for emperor choose, session: {}", sessionId);
                        throw new InvalidActionException("选择皇帝宝物时必须提供relicName");
                    }
                    Relic chosen = gameService.chooseEmperorRelic(sessionId, relicName);
                    if (chosen != null) {
                        result.put("relic", chosen);
                        result.put("message", "唐太宗李世民赐予你: " + chosen.getName() + "！");
                        result.put("player", playerSummaryAssembler.toPlayerSummary(gameService.getSession(sessionId).getPlayer()));
                        log.info("Emperor relic chosen: {} for session: {}", relicName, sessionId);
                    } else {
                        result.put("error", "无效或已拥有的宝物: " + relicName);
                    }
                } else {
                    // 默认动作：获取3件候选宝物
                    List<Relic> choices = gameService.getEmperorChoices(sessionId);
                    result.put("choices", choices);
                    result.put("message", "唐太宗李世民设宴相送，请从三件御赐宝物中选择一件：");
                    log.info("Emperor choices offered for session: {}", sessionId);
                }
            }
            case "SHOP" -> {
                if ("buy".equals(action)) {
                    Long cardId = request.getCardId();
                    if (cardId == null) {
                        log.warn("Missing cardId for shop purchase, session: {}", sessionId);
                        throw new InvalidActionException("购买卡牌时必须提供cardId");
                    }
                    int price = request.getPrice() != null ? request.getPrice() : 50;
                    boolean bought = gameService.buyCard(sessionId, cardId, price);
                    session = gameService.getSession(sessionId);
                    result.put("bought", bought);
                    result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
                    log.info("Shop purchase cardId={}, price={}, bought={} for session: {}",
                            cardId, price, bought, sessionId);
                } else {
                    List<Card> shopCards = gameService.getShopCards(sessionId);
                    result.put("shopCards", shopCards);
                }
            }
            case "RANDOM" -> {
                var actors = List.of(com.xiyouji.service.event.EventActor.solo(session));
                boolean preview = List.of("none", "view", "browse", "trigger").contains(action);
                if (node.getEventState() == null) {
                    if (!preview && !"leave".equals(action)) throw new InvalidActionException("请先预览事件");
                    node.setEventState(eventEngine.create(sessionId, node, "leave".equals(action) ? List.of() : actors));
                    persistAfterEvent = true;
                }
                if (!preview) persistAfterEvent |= eventEngine.resolve(node.getEventState(), action, actors);
                var event = eventEngine.preview(node.getEventState(), actors);
                result.put("storyEvent", com.xiyouji.service.event.StoryCatalog.event(event));
                result.put("message", event.text());
                result.put("player", playerSummaryAssembler.toPlayerSummary(session.getPlayer()));
            }
        }

        if (persistAfterEvent) {
            gameService.saveSession(session);
        }

        result.put("stateVersion", gameService.getSession(sessionId).getStateVersion());

        return result;
    }

}
