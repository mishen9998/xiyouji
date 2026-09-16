package com.xiyouji.service;

import com.xiyouji.dto.response.EventPreview;
import com.xiyouji.event.EventState;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import com.xiyouji.port.*;
import com.xiyouji.service.event.*;
import com.xiyouji.service.session.GameSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventEngineTest {
    CardRepositoryPort cards;
    RelicRepositoryPort relics;
    EventEngine engine;
    GameSession session;
    List<EventActor> actors;

    @BeforeEach void setup() {
        cards = mock(CardRepositoryPort.class); relics = mock(RelicRepositoryPort.class);
        engine = new EventEngine(cards,relics);
        when(cards.findByCharacterClassOrCharacterClassIsNull(CharacterClass.SUN_WUKONG))
            .thenReturn(List.of(card("普通奖励",Rarity.COMMON)));
        when(relics.findAll()).thenReturn(List.of(new Relic("普通遗物","test",RelicTier.COMMON,"")));
        GameCharacter player = new GameCharacter();
        player.setCharacterClass(CharacterClass.SUN_WUKONG); player.setMaxHp(101); player.setHp(80); player.setGold(100);
        player.getDeck().add(card("基础",Rarity.BASIC)); player.getDeck().add(card("独门",Rarity.RARE));
        session = new GameSession("session",player,new ArrayList<>()); session.setOwnerUserId("user");
        actors = List.of(EventActor.solo(session));
    }
    static Card card(String name, Rarity rarity) {
        Card c = new Card(name,"test",CardType.ATTACK,rarity,null,1); c.setDamage(6); c.setUpgradeable(true); return c;
    }
    EventState state(int index) {
        int chapter = index / 3 + 1;
        MapNode node = new MapNode("node-" + index,chapter,index % 3,0,"RANDOM","event");
        return engine.create("session",node,actors);
    }
    @ParameterizedTest
    @CsvSource({
        "0,option1,71,120,2,0,0", "0,option2,91,90,2,0,0",
        "1,option1,69,100,2,0,1", "1,option2,101,85,2,0,0",
        "2,option1,74,100,3,0,0", "2,option2,80,110,2,0,0",
        "3,option1,96,85,2,0,0", "3,option2,71,125,2,0,0",
        "4,option1,67,100,2,0,1", "4,option2,80,90,1,0,0",
        "5,option1,101,80,2,0,0", "5,option2,72,100,2,1,0",
        "6,option1,93,80,2,0,0", "6,option2,69,130,2,0,0",
        "7,option1,80,145,1,0,0", "7,option2,67,100,2,0,1",
        "8,option1,81,70,2,0,0", "8,option2,80,100,2,0,0"
    })
    void exactEighteenBranches(int event, String choice, int hp, int gold, int deck, int relicCount, int upgraded) {
        EventState state = state(event);
        assertEquals(3,EventEngine.preview(state,actors).options().size());
        assertTrue(engine.resolve(state,choice,actors));
        assertEquals(hp,session.getPlayer().getHp()); assertEquals(gold,session.getPlayer().getGold());
        assertEquals(deck,session.getPlayer().getDeck().size()); assertEquals(relicCount,session.getPlayer().getRelics().size());
        assertEquals(upgraded,session.getPlayer().getDeck().stream().filter(Card::isUpgraded).count());
        assertEquals(event == 8 && choice.equals("option2") ? 8 : 0, session.getNextBattleBlock());
        assertFalse(engine.resolve(state,choice,actors));
        assertEquals(hp,session.getPlayer().getHp()); assertEquals(gold,session.getPlayer().getGold());
        assertTrue(EventEngine.preview(state,actors).options().get(2).enabled());
    }
    @Test void leaveAlwaysWorksWithoutCostsOrTargets() {
        session.getPlayer().setHp(1); session.getPlayer().setGold(0); session.getPlayer().getDeck().clear();
        for (int i = 0; i < 9; i++) { var s = state(i); assertTrue(engine.resolve(s,"leave",actors)); }
        assertEquals(1,session.getPlayer().getHp()); assertEquals(0,session.getPlayer().getGold());
        assertTrue(session.getPlayer().getDeck().isEmpty()); assertEquals(0,session.getNextBattleBlock());
    }
    @Test void hpMustPayInFullAndLeaveOne() {
        var s = state(0); // ceil(101 * 8%) = 9
        for (int hp : List.of(1,8,9)) {
            session.getPlayer().setHp(hp);
            assertFalse(EventEngine.preview(s,actors).options().get(0).enabled());
            assertThrows(InvalidActionException.class,() -> engine.resolve(s,"option1",actors));
            assertEquals(hp,session.getPlayer().getHp()); assertEquals(100,session.getPlayer().getGold());
        }
        session.getPlayer().setHp(10); assertTrue(engine.resolve(s,"option1",actors)); assertEquals(1,session.getPlayer().getHp());
    }
    @Test void insufficientGoldDisablesBeforeMutation() {
        var s = state(0); session.getPlayer().setGold(9);
        assertFalse(EventEngine.preview(s,actors).options().get(1).enabled());
        assertThrows(InvalidActionException.class,() -> engine.resolve(s,"option2",actors));
        assertEquals(9,session.getPlayer().getGold()); assertEquals(80,session.getPlayer().getHp()); assertFalse(s.resolved);
    }
    @Test void allMissingTargetKindsAreDisabledAndNeverCharge() {
        session.getPlayer().getDeck().clear();
        when(cards.findByCharacterClassOrCharacterClassIsNull(any())).thenReturn(List.of());
        when(relics.findAll()).thenReturn(List.of());
        for (int[] test : List.of(new int[]{1,0},new int[]{2,0},new int[]{4,1},new int[]{5,1},new int[]{7,0})) {
            var s = state(test[0]);
            assertFalse(EventEngine.preview(s,actors).options().get(test[1]).enabled());
            assertThrows(InvalidActionException.class,() -> engine.resolve(s,"option" + (test[1] + 1),actors));
        }
        assertEquals(100,session.getPlayer().getGold()); assertEquals(80,session.getPlayer().getHp());
    }
    @Test void upgradedOrCardsWithoutAnyUpgradeableEffectAreNotTargets() {
        session.getPlayer().getDeck().get(0).setUpgraded(true);
        session.getPlayer().getDeck().get(1).setUpgradeable(false);
        session.getPlayer().getDeck().get(1).setDamage(0);
        assertFalse(EventEngine.preview(state(1),actors).options().get(0).enabled());
    }
    @Test void legacySeedWithUnsetUpgradeableFlagCanStillUpgradeItsRealEffect() {
        session.getPlayer().getDeck().forEach(c -> c.setUpgradeable(false));
        var state = state(1);
        assertTrue(engine.resolve(state,"option1",actors));
        assertTrue(session.getPlayer().getDeck().stream().anyMatch(c -> c.isUpgraded() && c.getDamage() == 9));
    }
    @Test void lockedTargetsCannotBeReplacedOrRerolled() {
        var s = state(1); var o = s.members.get("user").get(0);
        var before = EventEngine.preview(s,actors);
        assertEquals(before,EventEngine.preview(s,actors));
        session.getPlayer().getDeck().set(o.cardIndex,card("替换",Rarity.COMMON));
        assertFalse(EventEngine.preview(s,actors).options().get(0).enabled());
        assertThrows(InvalidActionException.class,() -> engine.resolve(s,"option1",actors));
        assertEquals(before.options().get(0).members().get("user").targetName(),o.targetName);
    }
    @Test void healToNeverReducesHp() {
        session.getPlayer().setHp(100); var s = state(8); engine.resolve(s,"option1",actors);
        assertEquals(100,session.getPlayer().getHp()); assertEquals(70,session.getPlayer().getGold());
    }
}
