package com.xiyouji.service;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.SessionNotFoundException;
import com.xiyouji.model.Card;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.enums.CardType;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.model.enums.Rarity;
import com.xiyouji.repository.CardRepository;
import com.xiyouji.repository.CharacterRepository;
import com.xiyouji.repository.RelicRepository;
import com.xiyouji.service.session.GameSession;
import com.xiyouji.service.session.SessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * GameService 单元测试
 * 使用 @ExtendWith(MockitoExtension.class) 和 @Mock/@InjectMocks
 */
@DisplayName("GameService 单元测试")
@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private CharacterRepository characterRepo;

    @Mock
    private CardRepository cardRepo;

    @Mock
    private RelicRepository relicRepo;

    @Mock
    private MapService mapService;

    @Mock
    private ShopService shopService;

    @Mock
    private SessionStore sessionStore;

    @InjectMocks
    private GameService gameService;

    @Test
    @DisplayName("newGame 创建正确的游戏会话")
    void testNewGame_createsSession() {
        String sessionId = "test-session-1";
        CharacterClass charClass = CharacterClass.SUN_WUKONG;

        // 模拟数据库中的角色模板
        GameCharacter dbChar = new GameCharacter();
        dbChar.setCharacterClass(charClass);
        dbChar.setMaxHp(80);
        dbChar.setStartingGold(100);
        dbChar.setStartingRelic(null); // 无初始遗物，简化测试

        // 模拟基础卡牌
        Card attackCard = new Card("挥棒", "造成6点伤害", CardType.ATTACK, Rarity.BASIC, null, 1);
        attackCard.setDamage(6);
        Card defendCard = new Card("格挡", "获得5点格挡", CardType.DEFENSE, Rarity.BASIC, null, 1);
        defendCard.setBlock(5);

        when(characterRepo.findByCharacterClass(charClass)).thenReturn(Optional.of(dbChar));
        when(cardRepo.findByName("挥棒")).thenReturn(List.of(attackCard));
        when(cardRepo.findByName("格挡")).thenReturn(List.of(defendCard));
        when(mapService.generateLayer(1)).thenReturn(
                List.of(new MapNode("L1-R0-C0", 1, 0, 0, GameConstants.NODE_BATTLE, "黑风山")));

        GameSession session = gameService.newGame(sessionId, charClass);

        assertNotNull(session, "会话不应为 null");
        assertEquals(sessionId, session.getSessionId(), "会话 ID 应匹配");
        assertNotNull(session.getPlayer(), "玩家不应为 null");
        assertEquals(charClass, session.getPlayer().getCharacterClass(), "角色职业应匹配");
        assertEquals(80, session.getPlayer().getMaxHp(), "最大生命值应为 80");
        assertEquals(80, session.getPlayer().getHp(), "当前 hp 应为 80");
        assertEquals(100, session.getPlayer().getGold(), "初始金币应为 100");
        assertEquals(GameConstants.MAX_ENERGY, session.getPlayer().getMaxEnergy(), "最大能量应为 3");
        assertEquals(0, session.getPlayer().getFloor(), "初始楼层应为 0");
        // INITIAL_HAND_SIZE=5，每次循环加 1 攻 + 1 防 = 10 张
        assertEquals(10, session.getPlayer().getDeck().size(), "初始牌组应有 10 张牌（5 攻 5 防）");
        assertEquals(1, session.getCurrentLayer(), "当前层应为 1");
        assertEquals(GameConstants.MAX_LAYERS, session.getMaxLayer(), "最大层数应为 3");
        assertNotNull(session.getMap(), "地图不应为 null");
        assertFalse(session.getMap().isEmpty(), "地图不应为空");

        verify(sessionStore).put(eq(sessionId), any(GameSession.class));
        verify(mapService).generateLayer(1);
    }

    @Test
    @DisplayName("getSession 会话不存在时抛出 SessionNotFoundException")
    void testGetSession_notFound_throwsException() {
        String sessionId = "nonexistent-session";

        when(sessionStore.get(sessionId)).thenReturn(null);

        assertThrows(SessionNotFoundException.class,
                () -> gameService.getSession(sessionId),
                "会话不存在时应抛出 SessionNotFoundException");

        verify(sessionStore).get(sessionId);
    }

    @Test
    @DisplayName("deleteSession 删除存在的会话")
    void testDeleteSession_existing_removesSession() {
        String sessionId = "test-session-to-delete";

        when(sessionStore.remove(sessionId)).thenReturn(true);

        boolean result = gameService.deleteSession(sessionId);

        assertTrue(result, "删除存在的会话应返回 true");
        verify(sessionStore).remove(sessionId);
    }

    @Test
    @DisplayName("getCardRewards 返回正确数量的卡牌奖励")
    void testGetCardRewards_returnsCorrectCount() {
        String sessionId = "test-session-rewards";

        GameCharacter player = new GameCharacter();
        player.setCharacterClass(CharacterClass.SUN_WUKONG);
        GameSession session = new GameSession(sessionId, player, List.of());

        // 模拟 10 张可用卡牌
        List<Card> availableCards = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            availableCards.add(new Card("卡" + i, "测试卡", CardType.ATTACK, Rarity.COMMON, null, 1));
        }

        when(sessionStore.get(sessionId)).thenReturn(session);
        when(cardRepo.findByCharacterClassOrCharacterClassIsNull(CharacterClass.SUN_WUKONG))
                .thenReturn(availableCards);

        List<Card> rewards = gameService.getCardRewards(sessionId, 3);

        assertEquals(3, rewards.size(), "应返回 3 张卡牌奖励");
        verify(sessionStore).get(sessionId);
        verify(cardRepo).findByCharacterClassOrCharacterClassIsNull(CharacterClass.SUN_WUKONG);
    }
}
