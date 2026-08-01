package com.xiyouji.service;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.CharacterNotFoundException;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.exception.SessionNotFoundException;
import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import com.xiyouji.repository.*;
import com.xiyouji.service.session.GameSession;
import com.xiyouji.service.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * 游戏核心服务 - 管理游戏状态、角色初始化、卡牌/遗物奖励
 * 地图生成逻辑委托给 MapService，商店逻辑委托给 ShopService
 * 会话存储通过 SessionStore 抽象，支持内存和Redis两种实现
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final CharacterRepository characterRepo;
    private final CardRepository cardRepo;
    private final RelicRepository relicRepo;
    private final MapService mapService;
    private final ShopService shopService;
    private final SessionStore sessionStore;

    public GameService(CharacterRepository characterRepo, CardRepository cardRepo,
                       RelicRepository relicRepo, MapService mapService,
                       ShopService shopService, SessionStore sessionStore) {
        this.characterRepo = characterRepo;
        this.cardRepo = cardRepo;
        this.relicRepo = relicRepo;
        this.mapService = mapService;
        this.shopService = shopService;
        this.sessionStore = sessionStore;
    }

    /** 创建新游戏会话 */
    @Transactional
    public GameSession newGame(String sessionId, CharacterClass charClass) {
        GameCharacter gc = characterRepo.findByCharacterClass(charClass)
                .orElseThrow(() -> new CharacterNotFoundException("角色不存在: " + charClass));

        GameCharacter player = new GameCharacter();
        player.setCharacterClass(charClass);
        player.setMaxHp(gc.getMaxHp());
        player.setHp(gc.getMaxHp());
        player.setGold(gc.getStartingGold());
        player.setMaxEnergy(GameConstants.MAX_ENERGY);
        player.setFloor(0);

        List<Card> basicAttack = cardRepo.findByName("挥棒");
        List<Card> basicDefend = cardRepo.findByName("格挡");
        for (int i = 0; i < GameConstants.INITIAL_HAND_SIZE; i++) {
            if (!basicAttack.isEmpty()) player.addCard(basicAttack.get(0).copy());
            if (!basicDefend.isEmpty()) player.addCard(basicDefend.get(0).copy());
        }

        if (gc.getStartingRelic() != null) {
            // 使用 findByName 替代 findAll 全表遍历
            relicRepo.findByName(gc.getStartingRelic())
                    .ifPresent(relic -> player.getRelics().add(relic));
        }

        List<MapNode> map = mapService.generateLayer(1);
        GameSession session = new GameSession(sessionId, player, map);
        session.setCurrentLayer(1);
        session.setMaxLayer(GameConstants.MAX_LAYERS);
        sessionStore.put(sessionId, session);
        log.info("新游戏创建: sessionId={}, character={}", sessionId, charClass);
        return session;
    }

    public GameSession getSession(String sessionId) {
        GameSession s = sessionStore.get(sessionId);
        if (s == null) throw new SessionNotFoundException("会话不存在: " + sessionId);
        return s;
    }

    @Transactional
    public boolean deleteSession(String sessionId) {
        boolean removed = sessionStore.remove(sessionId);
        if (removed) {
            log.info("会话已删除: sessionId={}", sessionId);
        }
        return removed;
    }

    /**
     * 保存会话到存储（供BattleService等外部调用）
     *
     * @param session 游戏会话
     */
    @Transactional
    public void saveSession(GameSession session) {
        sessionStore.put(session.getSessionId(), session);
    }

    // ====== 地图移动逻辑（委托给MapService） ======

    /** 前往节点 - 基于连接关系解锁 */
    @Transactional
    public MapNode moveToNode(String sessionId, String nodeId) {
        GameSession session = getSession(sessionId);
        MapNode target = mapService.moveToNode(session, nodeId);
        sessionStore.put(sessionId, session);
        return target;
    }

    /** Boss击败后进入下一层 */
    @Transactional
    public boolean advanceToNextLayer(String sessionId) {
        GameSession session = getSession(sessionId);
        boolean success = mapService.advanceToNextLayer(session);
        sessionStore.put(sessionId, session);
        return success;
    }

    /** 检查当前节点是否是Boss */
    public boolean isAtBoss(String sessionId) {
        GameSession session = getSession(sessionId);
        return mapService.isAtBoss(session);
    }

    // ====== 卡牌/遗物/商店 ======

    public List<Card> getCardRewards(String sessionId, int count) {
        GameSession session = getSession(sessionId);
        List<Card> allAvailable = cardRepo.findByCharacterClassOrCharacterClassIsNull(
                session.getPlayer().getCharacterClass());
        Collections.shuffle(allAvailable);
        // ★ 使用 new ArrayList 包装，避免 subList 返回 ArrayList$SubList
        // 该内部类无默认构造器，会导致 Redis JSON 反序列化失败，会话丢失
        return new ArrayList<>(allAvailable.subList(0, Math.min(count, allAvailable.size())));
    }

    @Transactional
    public void addCardToDeck(String sessionId, Long cardId) {
        GameSession session = getSession(sessionId);
        cardRepo.findById(cardId).ifPresent(card ->
                session.getPlayer().addCard(card.copy()));
        sessionStore.put(sessionId, session);
    }

    @Transactional
    public void removeCardFromDeck(String sessionId, int index) {
        GameSession session = getSession(sessionId);
        List<Card> deck = session.getPlayer().getDeck();
        if (index >= 0 && index < deck.size()) {
            session.getPlayer().removeCard(deck.get(index));
            sessionStore.put(sessionId, session);
        }
    }

    @Transactional
    public void heal(String sessionId, int amount) {
        GameSession session = getSession(sessionId);
        session.getPlayer().heal(amount);
        sessionStore.put(sessionId, session);
    }

    @Transactional
    public Card upgradeCard(String sessionId, int handIndex) {
        GameSession session = getSession(sessionId);
        List<Card> deck = session.getPlayer().getDeck();
        if (handIndex >= 0 && handIndex < deck.size()) {
            Card card = deck.get(handIndex);
            card.upgrade();
            sessionStore.put(sessionId, session);
            return card;
        }
        throw new InvalidActionException("无效的卡牌索引");
    }

    /** 购买卡牌（委托给ShopService） */
    @Transactional
    public boolean buyCard(String sessionId, Long cardId, int price) {
        GameSession session = getSession(sessionId);
        boolean result = shopService.buyCard(session, cardId, price);
        sessionStore.put(sessionId, session);
        return result;
    }

    /** 获取商店卡牌列表（委托给ShopService） */
    public List<Card> getShopCards(String sessionId) {
        GameSession session = getSession(sessionId);
        return shopService.getShopCards(session);
    }

    /** 获取商店宝物列表（委托给ShopService） */
    public List<Map<String, Object>> getShopRelics(String sessionId) {
        GameSession session = getSession(sessionId);
        return shopService.getShopRelics(session);
    }

    /** 购买宝物（委托给ShopService） */
    @Transactional
    public boolean buyRelic(String sessionId, Long relicId) {
        GameSession session = getSession(sessionId);
        boolean result = shopService.buyRelic(session, relicId);
        sessionStore.put(sessionId, session);
        return result;
    }

    /**
     * 获取随机宝物（用于随机事件奖励）
     * 使用 @Cacheable 缓存全表数据，避免每次随机都查库
     */
    @Cacheable(value = "relics", key = "'all'")
    public List<Relic> getAllRelics() {
        return relicRepo.findAll();
    }

    public Relic getRandomRelic(String sessionId) {
        List<Relic> relics = new ArrayList<>(getAllRelics());
        Collections.shuffle(relics);
        return relics.isEmpty() ? null : relics.get(0);
    }

    // ====== 唐朝皇帝赐宝 ======

    /**
     * 获取唐朝皇帝三选一的候选宝物列表
     * 从8件御赐宝物中随机抽取3件，排除玩家已拥有的
     *
     * @param sessionId 会话ID
     * @return 3件候选宝物列表
     */
    public List<Relic> getEmperorChoices(String sessionId) {
        GameSession session = getSession(sessionId);
        Set<String> ownedNames = new HashSet<>();
        for (Relic r : session.getPlayer().getRelics()) {
            ownedNames.add(r.getName());
        }

        List<String> candidates = new ArrayList<>(Arrays.asList(GameConstants.EMPEROR_RELICS));
        candidates.removeAll(ownedNames);
        Collections.shuffle(candidates);

        List<Relic> result = new ArrayList<>();
        for (String name : candidates) {
            // 使用 findByName 替代 findAll().stream().filter
            Optional<Relic> relic = relicRepo.findByName(name);
            if (relic.isPresent()) {
                result.add(relic.get());
                if (result.size() >= GameConstants.EMPEROR_REWARD_CHOICES) break;
            }
        }
        return result;
    }

    /**
     * 玩家选择唐朝皇帝赐予的宝物
     *
     * @param sessionId 会话ID
     * @param relicName 选择的宝物名称
     * @return 被选中的宝物，若选择无效则返回null
     */
    @Transactional
    public Relic chooseEmperorRelic(String sessionId, String relicName) {
        GameSession session = getSession(sessionId);
        // 校验：必须为皇帝宝物之一
        boolean valid = Arrays.asList(GameConstants.EMPEROR_RELICS).contains(relicName);
        if (!valid) {
            log.warn("无效的皇帝宝物选择: sessionId={}, relicName={}", sessionId, relicName);
            return null;
        }
        // 校验：玩家尚未拥有该宝物
        for (Relic r : session.getPlayer().getRelics()) {
            if (relicName.equals(r.getName())) {
                log.warn("玩家已拥有该宝物: sessionId={}, relicName={}", sessionId, relicName);
                return null;
            }
        }
        // 使用 findByName 替代 findAll 遍历
        Optional<Relic> relic = relicRepo.findByName(relicName);
        if (relic.isPresent()) {
            session.getPlayer().getRelics().add(relic.get());
            sessionStore.put(sessionId, session);
            log.info("皇帝赐宝选择完成: sessionId={}, relic={}", sessionId, relicName);
            return relic.get();
        }
        return null;
    }
}
