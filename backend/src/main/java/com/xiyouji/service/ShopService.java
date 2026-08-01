package com.xiyouji.service;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.model.Card;
import com.xiyouji.model.Relic;
import com.xiyouji.model.enums.RelicTier;
import com.xiyouji.repository.CardRepository;
import com.xiyouji.repository.RelicRepository;
import com.xiyouji.service.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商店服务 - 负责商店购买和商品展示逻辑
 * 从GameService中提取，管理商店卡牌和宝物的展示与购买
 */
@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    private final CardRepository cardRepo;
    private final RelicRepository relicRepo;

    public ShopService(CardRepository cardRepo, RelicRepository relicRepo) {
        this.cardRepo = cardRepo;
        this.relicRepo = relicRepo;
    }

    /**
     * 购买卡牌
     * 根据玩家遗物（通关文牒）计算折扣，扣除金币并添加卡牌到牌组
     *
     * @param session 游戏会话
     * @param cardId  要购买的卡牌ID
     * @param price   卡牌价格
     * @return true表示购买成功，false表示金币不足
     */
    @Transactional
    public boolean buyCard(GameSession session, Long cardId, int price) {
        int discount = session.getPlayer().getRelics().stream()
                .anyMatch(r -> GameConstants.RELIC_TONGGUANWENDIE.equals(r.getName()))
                ? GameConstants.SHOP_DISCOUNT_PERCENT : 0;
        int finalPrice = price * (100 - discount) / 100;

        if (session.getPlayer().getGold() < finalPrice) {
            log.info("购买失败，金币不足: sessionId={}, gold={}, price={}",
                    session.getSessionId(), session.getPlayer().getGold(), finalPrice);
            return false;
        }

        session.getPlayer().setGold(session.getPlayer().getGold() - finalPrice);

        cardRepo.findById(cardId).ifPresent(card ->
                session.getPlayer().addCard(card.copy()));

        log.info("购买成功: sessionId={}, cardId={}, finalPrice={}",
                session.getSessionId(), cardId, finalPrice);
        return true;
    }

    /**
     * 购买宝物
     * 根据宝物等级计算价格，通关文牒享受折扣，扣除金币并添加宝物到玩家遗物列表
     *
     * @param session 游戏会话
     * @param relicId 要购买的宝物ID
     * @return true表示购买成功，false表示金币不足或宝物不存在
     */
    @Transactional
    public boolean buyRelic(GameSession session, Long relicId) {
        Optional<Relic> relicOpt = relicRepo.findById(relicId);
        if (relicOpt.isEmpty()) {
            log.warn("宝物不存在: relicId={}", relicId);
            return false;
        }
        Relic relic = relicOpt.get();

        // 检查是否已拥有该宝物
        Set<String> ownedNames = session.getPlayer().getRelics().stream()
                .map(Relic::getName)
                .collect(Collectors.toSet());
        if (ownedNames.contains(relic.getName())) {
            log.warn("已拥有该宝物: sessionId={}, relic={}", session.getSessionId(), relic.getName());
            return false;
        }

        int price = getRelicPrice(relic.getTier());
        int discount = session.getPlayer().getRelics().stream()
                .anyMatch(r -> GameConstants.RELIC_TONGGUANWENDIE.equals(r.getName()))
                ? GameConstants.SHOP_DISCOUNT_PERCENT : 0;
        int finalPrice = price * (100 - discount) / 100;

        if (session.getPlayer().getGold() < finalPrice) {
            log.info("购买宝物失败，金币不足: sessionId={}, gold={}, price={}",
                    session.getSessionId(), session.getPlayer().getGold(), finalPrice);
            return false;
        }

        session.getPlayer().setGold(session.getPlayer().getGold() - finalPrice);
        session.getPlayer().getRelics().add(relic);

        log.info("购买宝物成功: sessionId={}, relic={}, finalPrice={}",
                session.getSessionId(), relic.getName(), finalPrice);
        return true;
    }

    /**
     * 获取商店可购买的卡牌列表
     * 随机抽取当前角色职业可用及通用卡牌
     *
     * @param session 游戏会话
     * @return 随机排序的卡牌列表（最多 SHOP_CARD_COUNT 张）
     */
    public List<Card> getShopCards(GameSession session) {
        List<Card> available = cardRepo.findByCharacterClassOrCharacterClassIsNull(
                session.getPlayer().getCharacterClass());
        Collections.shuffle(available);
        return available.subList(0, Math.min(GameConstants.SHOP_CARD_COUNT, available.size()));
    }

    /**
     * 获取全部宝物（缓存）
     * 使用 @Cacheable 避免每次开商店都查库，与 GameService/RoomService 共享 "relics" 缓存
     */
    @Cacheable(value = "relics", key = "'all'")
    public List<Relic> getAllRelics() {
        return relicRepo.findAll();
    }

    /**
     * 获取商店可购买的宝物列表
     * 从 COMMON/UNCOMMON/RARE 等级中随机抽取玩家未拥有的宝物
     *
     * @param session 游戏会话
     * @return 随机排序的宝物列表（最多 SHOP_RELIC_COUNT 件），包含价格信息
     */
    public List<Map<String, Object>> getShopRelics(GameSession session) {
        Set<String> ownedNames = session.getPlayer().getRelics().stream()
                .map(Relic::getName)
                .collect(Collectors.toSet());

        // 使用缓存的全表数据替代 findAll() 直接查库
        List<Relic> candidates = getAllRelics().stream()
                .filter(r -> r.getTier() == RelicTier.COMMON
                        || r.getTier() == RelicTier.UNCOMMON
                        || r.getTier() == RelicTier.RARE)
                .filter(r -> !ownedNames.contains(r.getName()))
                .collect(Collectors.toList());

        Collections.shuffle(candidates);
        int count = Math.min(GameConstants.SHOP_RELIC_COUNT, candidates.size());

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Relic relic = candidates.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", relic.getId());
            item.put("name", relic.getName());
            item.put("description", relic.getDescription());
            item.put("tier", relic.getTier().name());
            item.put("emoji", relic.getEmoji());
            item.put("price", getRelicPrice(relic.getTier()));
            result.add(item);
        }
        return result;
    }

    /**
     * 根据宝物等级获取商店售价
     */
    public int getRelicPrice(RelicTier tier) {
        return switch (tier) {
            case COMMON -> GameConstants.SHOP_RELIC_PRICE_COMMON;
            case UNCOMMON -> GameConstants.SHOP_RELIC_PRICE_UNCOMMON;
            case RARE -> GameConstants.SHOP_RELIC_PRICE_RARE;
            default -> GameConstants.SHOP_RELIC_PRICE_RARE; // BOSS/SPECIAL 不在商店出售，兜底用 RARE 价格
        };
    }
}
