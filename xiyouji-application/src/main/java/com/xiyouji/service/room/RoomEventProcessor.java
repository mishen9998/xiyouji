package com.xiyouji.service.room;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.Relic;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.port.RelicRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 房间节点事件组件
 *
 * 处理地图节点的交互事件：休息（REST）、篝火升级（BONFIRE）、
 * 宝箱（TREASURE）、商店（SHOP）、随机事件（RANDOM）。
 */
@Component
public class RoomEventProcessor {

    private final RoomAccess access;
    private final RelicRepositoryPort relicRepo;
    private final CardRepositoryPort cardRepo;
    private final RoomDTOAssembler assembler;
    private final com.xiyouji.service.event.EventEngine eventEngine;
    private final com.xiyouji.service.ShopService shopService;

    public RoomEventProcessor(RoomAccess access, RelicRepositoryPort relicRepo,
                              CardRepositoryPort cardRepo, RoomDTOAssembler assembler) {
        this.access = access;
        this.relicRepo = relicRepo;
        this.cardRepo = cardRepo;
        this.assembler = assembler;
        this.eventEngine = new com.xiyouji.service.event.EventEngine(cardRepo, relicRepo);
        this.shopService = new com.xiyouji.service.ShopService(cardRepo);
    }

    /** 处理节点事件（rest/upgrade/buy/browse/trigger） */
    public Map<String, Object> handleEvent(String code, String userId,
                                           String action, Long cardId, Integer cardIndex,
                                           long expectedVersion) {
        return handleEvent(code, userId, action, cardId, cardIndex, null, expectedVersion);
    }
    public Map<String, Object> handleEvent(String code, String userId,
                                           String action, Long cardId, Integer cardIndex,
                                           Integer quotedPrice, long expectedVersion) {
        return access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            access.checkVersion(code, room, expectedVersion);
            MapNode node = room.getCurrentNode();

            if (node == null) {
                throw new InvalidActionException("不在任何节点");
            }
            if (room.getStatus() != RoomStatus.IN_MAP) {
                throw new InvalidActionException("当前不在地图探索阶段");
            }

            RoomPlayer player = room.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(userId))
                    .findFirst()
                    .orElseThrow(() -> new InvalidActionException("你不在该房间内"));

            Map<String, Object> result = new HashMap<>();
            boolean changed = true;

            switch (node.getType()) {
                case "REST" -> {
                    if ("rest".equals(action)) {
                        int healAmount = player.getMaxHp() / 3;
                        player.setHp(Math.min(player.getMaxHp(), player.getHp() + healAmount));
                        result.put("message", "休息完毕，全队生命值已恢复");
                        result.put("healAmount", healAmount);
                    }
                    result.put("players", room.getPlayers());
                }
                case "BONFIRE" -> {
                    if ("upgrade".equals(action) && cardIndex != null && cardIndex >= 0) {
                        if (room.getBonfireUpgradesLeft() <= 0) {
                            result.put("error", "升级次数已用完");
                        } else {
                            List<Card> deck = player.getDeck();
                            if (cardIndex < deck.size()) {
                                deck.get(cardIndex).upgrade();
                                room.setBonfireUpgradesLeft(room.getBonfireUpgradesLeft() - 1);
                                result.put("message", "卡牌已升级");
                            }
                        }
                    }
                    result.put("bonfireUpgradesLeft", room.getBonfireUpgradesLeft());
                    result.put("players", room.getPlayers());
                }
                case "TREASURE" -> {
                    Relic relic = getRandomRelic();
                    if (relic != null) {
                        player.getRelics().add(relic);
                        result.put("relic", relic);
                        result.put("message", player.getUsername() + " 获得遗物: " + relic.getName());
                    }
                    result.put("players", room.getPlayers());
                }
                case "SHOP" -> {
                    if ("buy".equals(action)) {
                        Card card = shopService.validatePurchase(node,userId,player.getCharacterClass(),cardId,quotedPrice,player.getRelics(),player.getGold());
                        int price = shopService.price(player.getRelics());
                        player.setGold(player.getGold() - price);
                        player.getDeck().add(card);
                        shopService.sold(node,userId,cardId);
                        result.put("bought",true);
                        result.put("message","购买成功，花费 " + price + " 金币");
                    } else {
                        changed = !node.getShopStock().containsKey(userId);
                        List<Card> shopCards = shopService.stock(node,userId,player.getCharacterClass());
                        result.put("shopCards", shopCards);
                    }
                    result.put("price",shopService.price(player.getRelics()));
                    result.put("players", room.getPlayers());
                }
                case "RANDOM" -> {
                    boolean preview = List.of("none", "view", "browse", "trigger").contains(action);
                    if (!preview && !room.getHostUserId().equals(userId)) throw new InvalidActionException("只有房主才能选择团队事件");
                    var actors = room.getPlayers().stream().filter(p -> p.getHp() > 0)
                        .map(com.xiyouji.service.event.EventActor::room).toList();
                    changed = false;
                    if (node.getEventState() == null) {
                        if (!preview && !"leave".equals(action)) throw new InvalidActionException("请先预览事件");
                    node.setEventState(eventEngine.create(code, node, "leave".equals(action) ? List.of() : actors));
                        changed = true;
                    }
                    if (!preview) changed |= eventEngine.resolve(node.getEventState(), action, actors);
                    var event = eventEngine.preview(node.getEventState(), actors);
                    result.put("storyEvent",com.xiyouji.service.event.StoryCatalog.event(event));
                    result.put("message",event.text());
                    result.put("players", room.getPlayers());
                }
                default -> result.put("error", "未知节点类型: " + node.getType());
            }

            if (changed) {
                room.getMap().stream().filter(n -> n.getId().equals(node.getId())).forEach(n -> {
                    n.setEventState(node.getEventState()); n.setShopStock(node.getShopStock());
                });
                access.save(room);
            }
            result.put("stateVersion", room.getStateVersion());
            result.put("room", assembler.toDTO(room));
            return result;
        });
    }

    private Relic getRandomRelic() {
        List<Relic> relics = relicRepo.findAll();
        Collections.shuffle(relics);
        return relics.isEmpty() ? null : relics.get(0);
    }

}
