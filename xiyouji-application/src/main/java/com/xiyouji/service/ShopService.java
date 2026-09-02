package com.xiyouji.service;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.model.Card;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.service.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 商店服务 - 负责商店购买和商品展示逻辑
 * 从GameService中提取，管理商店卡牌的展示和购买
 */
@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    private final CardRepositoryPort cardRepo;

    public ShopService(CardRepositoryPort cardRepo) {
        this.cardRepo = cardRepo;
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
     * 获取商店可购买的卡牌列表
     * 随机抽取当前角色职业可用及通用卡牌
     *
     * @param session 游戏会话
     * @return 随机排序的卡牌列表（最多 CARD_REWARD_COUNT 张）
     */
    public List<Card> getShopCards(GameSession session) {
        List<Card> available = cardRepo.findByCharacterClassOrCharacterClassIsNull(
                session.getPlayer().getCharacterClass());
        Collections.shuffle(available);
        return available.subList(0, Math.min(GameConstants.CARD_REWARD_COUNT, available.size()));
    }
}
