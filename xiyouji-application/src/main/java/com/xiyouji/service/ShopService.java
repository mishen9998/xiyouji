package com.xiyouji.service;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.Relic;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.service.session.GameSession;
import org.springframework.stereotype.Service;
import java.util.*;

/** Server-owned per-node inventory. Clients may quote the base price (legacy) or final price. */
@Service
public class ShopService {
    public static final int BASE_PRICE = 50;
    private final CardRepositoryPort cardRepo;
    public ShopService(CardRepositoryPort cardRepo) { this.cardRepo = cardRepo; }
    public int price(List<Relic> relics) {
        return relics.stream().anyMatch(r -> GameConstants.RELIC_TONGGUANWENDIE.equals(r.getName()))
            ? BASE_PRICE * (100 - GameConstants.SHOP_DISCOUNT_PERCENT) / 100 : BASE_PRICE;
    }
    private static void validateNode(MapNode node) {
        if (node == null || !"SHOP".equals(node.getType())) throw new InvalidActionException("当前不在商店");
    }
    public List<Card> stock(MapNode node, String userId, CharacterClass characterClass) {
        validateNode(node);
        return node.getShopStock().computeIfAbsent(userId, key -> {
            List<Card> available = new ArrayList<>(cardRepo.findByCharacterClassOrCharacterClassIsNull(characterClass));
            available.removeIf(c -> c.getId() == null);
            available.sort(Comparator.comparing(Card::getId));
            Collections.shuffle(available, new Random(Objects.hash(node.getId(), userId)));
            return new ArrayList<>(available.subList(0, Math.min(GameConstants.CARD_REWARD_COUNT, available.size())));
        });
    }
    public Card validatePurchase(MapNode node, String userId, CharacterClass characterClass,
                                 Long cardId, Integer quotedPrice, List<Relic> relics, int gold) {
        validateNode(node);
        int finalPrice = price(relics);
        if (quotedPrice != null && quotedPrice != BASE_PRICE && quotedPrice != finalPrice)
            throw new InvalidActionException("商品价格与服务端报价不符");
        if (cardId == null) throw new InvalidActionException("必须提供商品cardId");
        List<Card> displayed = node.getShopStock().get(userId);
        if (displayed == null || displayed.stream().noneMatch(c -> cardId.equals(c.getId())))
            throw new InvalidActionException("商品不存在、未展示或已经售出");
        Card card = cardRepo.findById(cardId).orElseThrow(() -> new InvalidActionException("卡牌不存在"));
        if (card.getCharacterClass() != null && card.getCharacterClass() != characterClass)
            throw new InvalidActionException("此角色不可购买该卡牌");
        if (gold < finalPrice) throw new InvalidActionException("金币不足");
        return card.copy();
    }
    public void sold(MapNode node, String userId, Long cardId) {
        node.getShopStock().get(userId).removeIf(c -> cardId.equals(c.getId()));
    }
    public boolean buyCard(GameSession session, Long cardId, int quotedPrice) {
        var p = session.getPlayer();
        String userId = session.getOwnerUserId() == null ? "solo" : session.getOwnerUserId();
        Card card = validatePurchase(session.getCurrentNode(),userId,p.getCharacterClass(),cardId,quotedPrice,p.getRelics(),p.getGold());
        p.setGold(p.getGold() - price(p.getRelics())); p.addCard(card);
        sold(session.getCurrentNode(),userId,cardId);
        return true;
    }
    public List<Card> getShopCards(GameSession session) {
        return stock(session.getCurrentNode(),session.getOwnerUserId() == null ? "solo" : session.getOwnerUserId(),session.getPlayer().getCharacterClass());
    }
}
