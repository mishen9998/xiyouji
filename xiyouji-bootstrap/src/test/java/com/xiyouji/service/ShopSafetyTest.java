package com.xiyouji.service;

import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import com.xiyouji.port.*;
import com.xiyouji.service.room.*;
import com.xiyouji.service.session.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopSafetyTest {
    CardRepositoryPort cards;
    ShopService shop;
    GameSession session;
    GameService game;
    @BeforeEach void setup() {
        cards = mock(CardRepositoryPort.class); shop = new ShopService(cards);
        Card card = EventEngineTest.card("商品",Rarity.COMMON); card.setId(1L);
        when(cards.findById(1L)).thenReturn(Optional.of(card));
        when(cards.findByCharacterClassOrCharacterClassIsNull(any())).thenReturn(List.of(card));
        GameCharacter p = new GameCharacter(); p.setHp(50); p.setMaxHp(80); p.setGold(100); p.setCharacterClass(CharacterClass.SUN_WUKONG);
        MapNode node = new MapNode("shop",1,2,0,"SHOP","土地庙");
        session = new GameSession("session",p,new ArrayList<>(List.of(node))); session.setCurrentNode(node); session.setOwnerUserId("user");
        InMemorySessionStore sessions = new InMemorySessionStore(); sessions.put("session",session);
        game = new GameService(mock(CharacterRepositoryPort.class),cards,mock(RelicRepositoryPort.class),mock(MapService.class),shop,sessions,new LocalDistributedLockService());
        game.getShopCards("session");
    }
    @ParameterizedTest @ValueSource(ints={-1,0,1,49,51,Integer.MAX_VALUE})
    void forgedPriceDoesNotChangeGoldDeckOrVersion(int price) {
        long version = session.getStateVersion();
        assertThrows(InvalidActionException.class,() -> game.buyCard("session",1L,price));
        assertEquals(100,session.getPlayer().getGold()); assertTrue(session.getPlayer().getDeck().isEmpty()); assertEquals(version,session.getStateVersion());
    }
    @Test void missingOrUnlistedCardsCannotCharge() {
        long version = session.getStateVersion();
        assertThrows(InvalidActionException.class,() -> game.buyCard("session",999L,50));
        when(cards.findById(1L)).thenReturn(Optional.empty());
        assertThrows(InvalidActionException.class,() -> game.buyCard("session",1L,50));
        assertEquals(100,session.getPlayer().getGold()); assertTrue(session.getPlayer().getDeck().isEmpty()); assertEquals(version,session.getStateVersion());
    }
    @Test void poorPlayerCannotChargeAndInventoryRemains() {
        session.getPlayer().setGold(49); long version = session.getStateVersion();
        assertThrows(InvalidActionException.class,() -> game.buyCard("session",1L,50));
        assertEquals(49,session.getPlayer().getGold()); assertEquals(1,shop.getShopCards(session).size()); assertEquals(version,session.getStateVersion());
    }
    @Test void discountedPurchaseConsumesInventoryOnceAndBrowseDoesNotReroll() {
        session.getPlayer().getRelics().add(new Relic("通关文牒","discount",RelicTier.COMMON,""));
        long version = session.getStateVersion();
        assertEquals(1L,game.getShopCards("session").get(0).getId()); assertEquals(version,session.getStateVersion());
        assertTrue(game.buyCard("session",1L,50));
        assertEquals(60,session.getPlayer().getGold()); assertEquals(1,session.getPlayer().getDeck().size());
        assertEquals(version + 1,session.getStateVersion());
        assertThrows(InvalidActionException.class,() -> game.buyCard("session",1L,40));
        assertTrue(game.getShopCards("session").isEmpty()); assertEquals(version + 1,session.getStateVersion());
    }
    @Test void multiplayerUsesSameValidationBeforeSave() {
        Room room = new Room("room","user"); room.setStatus(RoomStatus.IN_MAP); room.setCurrentNode(session.getCurrentNode());
        RoomPlayer p = new RoomPlayer("user","user"); p.setCharacterClass(CharacterClass.SUN_WUKONG); p.setHp(50); p.setMaxHp(80); p.setGold(100); room.getPlayers().add(p);
        InMemoryRoomStore rooms = new InMemoryRoomStore(); rooms.save(room);
        var processor = new RoomEventProcessor(new RoomAccess(rooms,new LocalDistributedLockService()),mock(RelicRepositoryPort.class),cards,new RoomDTOAssembler());
        long version = room.getStateVersion();
        assertThrows(InvalidActionException.class,() -> processor.handleEvent("room","user","buy",1L,null,0,version));
        assertThrows(InvalidActionException.class,() -> processor.handleEvent("room","user","buy",999L,null,50,version));
        assertEquals(100,p.getGold()); assertTrue(p.getDeck().isEmpty()); assertEquals(version,room.getStateVersion());
        processor.handleEvent("room","user","buy",1L,null,50,version);
        assertEquals(50,p.getGold()); assertEquals(1,p.getDeck().size()); assertEquals(version + 1,room.getStateVersion());
    }
}
