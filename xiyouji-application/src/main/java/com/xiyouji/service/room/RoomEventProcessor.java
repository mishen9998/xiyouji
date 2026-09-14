package com.xiyouji.service.room;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.Relic;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.port.RelicRepositoryPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
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

    private final SecureRandom random = new SecureRandom();

    private final RoomAccess access;
    private final RelicRepositoryPort relicRepo;
    private final CardRepositoryPort cardRepo;
    private final RoomDTOAssembler assembler;

    public RoomEventProcessor(RoomAccess access, RelicRepositoryPort relicRepo,
                              CardRepositoryPort cardRepo, RoomDTOAssembler assembler) {
        this.access = access;
        this.relicRepo = relicRepo;
        this.cardRepo = cardRepo;
        this.assembler = assembler;
    }

    /** 处理节点事件（rest/upgrade/buy/browse/trigger） */
    public Map<String, Object> handleEvent(String code, String userId,
                                           String action, Long cardId, Integer cardIndex,
                                           long expectedVersion) {
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
                    if ("buy".equals(action) && cardId != null) {
                        int price = 50;
                        // 通关文牒折扣
                        boolean hasDiscount = player.getRelics().stream()
                                .anyMatch(r -> GameConstants.RELIC_TONGGUANWENDIE.equals(r.getName()));
                        if (hasDiscount) price = price * 80 / 100;

                        if (player.getGold() >= price) {
                            player.setGold(player.getGold() - price);
                            cardRepo.findById(cardId).ifPresent(card ->
                                    player.getDeck().add(card.copy()));
                            result.put("bought", true);
                            result.put("message", "购买成功，花费 " + price + " 金币");
                        } else {
                            result.put("bought", false);
                            result.put("error", "金币不足");
                        }
                    } else {
                        // 浏览商店
                        List<Card> shopCards = getShopCards(player);
                        result.put("shopCards", shopCards);
                    }
                    result.put("players", room.getPlayers());
                }
                case "RANDOM" -> {
                    String event = doRandomEvent(player);
                    result.put("message", event);
                    result.put("players", room.getPlayers());
                }
                default -> result.put("error", "未知节点类型: " + node.getType());
            }

            access.save(room);
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

    private List<Card> getShopCards(RoomPlayer player) {
        List<Card> available = cardRepo.findByCharacterClassOrCharacterClassIsNull(
                player.getCharacterClass());
        Collections.shuffle(available);
        return available.subList(0, Math.min(GameConstants.CARD_REWARD_COUNT, available.size()));
    }

    private String doRandomEvent(RoomPlayer player) {
        String[] events = {
            "你遇到了一位老神仙，他给了你一些指引。获得10金币。",
            "路边有棵人参果树，摘了一颗吃。回复8点生命值。",
            "遇到小妖怪打劫！失去10金币。",
            "发现了太上老君的丹炉遗迹，获得了一件遗物。",
            "山间的温泉让你神清气爽。回复5点生命值。"
        };
        String event = events[random.nextInt(events.length)];

        if (event.contains("获得10金币")) {
            player.setGold(player.getGold() + 10);
        }
        if (event.contains("8点生命")) {
            player.setHp(Math.min(player.getMaxHp(), player.getHp() + 8));
        }
        if (event.contains("失去")) {
            player.setGold(Math.max(0, player.getGold() - 10));
        }
        if (event.contains("5点生命")) {
            player.setHp(Math.min(player.getMaxHp(), player.getHp() + 5));
        }
        if (event.contains("遗物")) {
            Relic relic = getRandomRelic();
            if (relic != null) player.getRelics().add(relic);
        }
        return event;
    }
}