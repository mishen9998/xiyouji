package com.xiyouji.service;

import com.xiyouji.config.RedisConfig;
import com.xiyouji.dto.PlayerSummaryAssembler;
import com.xiyouji.dto.request.EventRequest;
import com.xiyouji.dto.response.StoryEvent;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import com.xiyouji.port.*;
import com.xiyouji.service.event.*;
import com.xiyouji.service.room.*;
import com.xiyouji.service.session.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventPersistenceAndAtomicityTest {
    CardRepositoryPort cards = mock(CardRepositoryPort.class);
    RelicRepositoryPort relics = mock(RelicRepositoryPort.class);
    RedisSerializer<Object> serializer;
    RoomStore store;
    RoomEventProcessor processor;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        serializer = (RedisSerializer<Object>)new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class)).getValueSerializer();
        store = new SnapshotRooms(serializer);
        processor = new RoomEventProcessor(new RoomAccess(store,new LocalDistributedLockService()),relics,cards,new RoomDTOAssembler());
    }
    Room room(int event, int members) {
        Room room = new Room("room","u0"); room.setStatus(RoomStatus.IN_MAP);
        room.setFloor(event / 3 + 1);
        MapNode node = new MapNode("event",event / 3 + 1,event % 3,0,"RANDOM","event");
        room.setCurrentNode(node); room.getMap().add(node);
        for (int i = 0; i < members; i++) {
            RoomPlayer p = new RoomPlayer("u" + i,"name" + i); p.setCharacterClass(CharacterClass.SUN_WUKONG);
            p.setHp(80); p.setMaxHp(101 + i); p.setGold(100); p.getDeck().add(EventEngineTest.card("基础",Rarity.BASIC));
            room.getPlayers().add(p);
        }
        store.save(room); return room;
    }
    Map<String,Object> command(String user, String action) {
        return processor.handleEvent("room",user,action,null,null,store.get("room").getStateVersion());
    }
    @Test void fivePlayersCommitIndependentCostsExactlyOnceAndDeadMemberExcluded() {
        Room room = room(0,5); room.getPlayers().get(4).setHp(0); store.save(room);
        command("u0","trigger"); long version = store.get("room").getStateVersion();
        assertEquals(4,store.get("room").getCurrentNode().getEventState().members.size());
        command("u0","option1"); Room saved = store.get("room");
        assertEquals(version + 1,saved.getStateVersion());
        for (int i = 0; i < 4; i++) { assertEquals(71,saved.getPlayers().get(i).getHp()); assertEquals(120,saved.getPlayers().get(i).getGold()); }
        assertEquals(0,saved.getPlayers().get(4).getHp()); assertEquals(100,saved.getPlayers().get(4).getGold());
        assertTrue(saved.getCurrentNode().getEventState().resolved);
        assertTrue(saved.getMap().get(0).getEventState().resolved);
        command("u0","option1"); command("u0","trigger");
        assertEquals(version + 1,store.get("room").getStateVersion());
        assertEquals(120,store.get("room").getPlayers().get(0).getGold());
    }
    @Test void onePoorMemberMeansNoMemberChangesAndNoVersionBump() {
        Room r = room(0,5); r.getPlayers().get(4).setGold(9); store.save(r);
        var result = command("u0","trigger");
        var story = (StoryEvent)result.get("storyEvent"); assertFalse(story.event().options().get(1).enabled());
        byte[] before = serializer.serialize(store.get("room"));
        assertThrows(InvalidActionException.class,() -> command("u0","option2"));
        assertArrayEquals(before,serializer.serialize(store.get("room")));
    }
    @Test void membersUseTheirOwnMaximumHpForRoundedCosts() {
        Room r = room(0,2); r.getPlayers().get(1).setMaxHp(201); store.save(r);
        command("u0","trigger"); command("u0","option1");
        assertEquals(71,store.get("room").getPlayers().get(0).getHp());
        assertEquals(63,store.get("room").getPlayers().get(1).getHp());
    }
    @Test void oneMissingCardMeansNobodyPaysHp() {
        Room r = room(1,5); r.getPlayers().get(4).getDeck().clear(); store.save(r);
        command("u0","trigger"); byte[] before = serializer.serialize(store.get("room"));
        assertThrows(InvalidActionException.class,() -> command("u0","option1"));
        assertArrayEquals(before,serializer.serialize(store.get("room")));
        command("u0","leave"); assertTrue(store.get("room").getCurrentNode().getEventState().resolved);
    }
    @Test void nonHostMayPreviewButCannotChooseOrLeaveForTeam() {
        room(0,2); command("u1","trigger"); long version = store.get("room").getStateVersion();
        assertThrows(InvalidActionException.class,() -> command("u1","option1"));
        assertThrows(InvalidActionException.class,() -> command("u1","leave"));
        assertThrows(InvalidActionException.class,() -> command("outsider","trigger"));
        assertEquals(version,store.get("room").getStateVersion());
    }
    @Test void concurrentChoicesUseOneSnapshotAndCommitOnlyOneOutcome() throws Exception {
        room(0,2); command("u0","trigger"); long version = store.get("room").getStateVersion();
        CountDownLatch start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return processor.handleEvent("room","u0","option1",null,null,-1); });
            var second = pool.submit(() -> { start.await(); return processor.handleEvent("room","u0","option2",null,null,-1); });
            start.countDown(); first.get(10,TimeUnit.SECONDS); second.get(10,TimeUnit.SECONDS);
            Room saved = store.get("room"); assertEquals(version + 1,saved.getStateVersion());
            boolean one = saved.getCurrentNode().getEventState().selectedOption.equals("option1");
            for (RoomPlayer p : saved.getPlayers()) { assertEquals(one ? 120 : 90,p.getGold()); assertEquals(one ? 71 : 91,p.getHp()); }
        } finally { pool.shutdownNow(); }
    }
    @Test void restartAndRepositoryChangesDoNotRerollReward() {
        when(cards.findByCharacterClassOrCharacterClassIsNull(any())).thenReturn(List.of(EventEngineTest.card("原始奖励",Rarity.COMMON)));
        room(2,2); var initial = (StoryEvent) command("u0","trigger").get("storyEvent");
        when(cards.findByCharacterClassOrCharacterClassIsNull(any())).thenReturn(List.of(EventEngineTest.card("另一奖励",Rarity.COMMON)));
        processor = new RoomEventProcessor(new RoomAccess(store,new LocalDistributedLockService()),relics,cards,new RoomDTOAssembler());
        assertEquals(initial,command("u0","trigger").get("storyEvent"));
        command("u0","option1");
        assertEquals("原始奖励",store.get("room").getPlayers().get(0).getDeck().get(1).getName());
    }
    @Test void soloHasSameSemanticsAndRepeatedPreviewDoesNotBumpVersion() {
        var sessions = new SnapshotSessions(serializer);
        GameService service = new GameService(mock(CharacterRepositoryPort.class),cards,relics,mock(MapService.class),new ShopService(cards),sessions,new LocalDistributedLockService());
        var events = new GameEventProcessor(service,new PlayerSummaryAssembler(),new EventEngine(cards,relics));
        GameCharacter p = new GameCharacter(); p.setHp(80); p.setMaxHp(101); p.setGold(100); p.setCharacterClass(CharacterClass.SUN_WUKONG);
        MapNode node = new MapNode("node",1,0,0,"RANDOM","event");
        GameSession s = new GameSession("solo",p,new ArrayList<>(List.of(node))); s.setCurrentNode(node); s.setOwnerUserId("owner"); sessions.put("solo",s);
        EventRequest request = new EventRequest(); request.setAction("trigger");
        events.process("solo",request,-1,"owner"); long version = sessions.get("solo").getStateVersion();
        events.process("solo",request,-1,"owner"); assertEquals(version,sessions.get("solo").getStateVersion());
        request.setAction("option2"); events.process("solo",request,-1,"owner"); events.process("solo",request,-1,"owner");
        assertEquals(90,sessions.get("solo").getPlayer().getGold()); assertEquals(91,sessions.get("solo").getPlayer().getHp());
        assertEquals(version + 1,sessions.get("solo").getStateVersion());
        assertTrue(sessions.get("solo").getMap().get(0).getEventState().resolved);
    }
    static class SnapshotRooms implements RoomStore {
        final RedisSerializer<Object> serializer; byte[] data;
        SnapshotRooms(RedisSerializer<Object> serializer) { this.serializer = serializer; }
        public synchronized void save(Room room) { room.setStateVersion(room.getStateVersion() + 1); data = serializer.serialize(room); }
        public synchronized Room get(String code) { return data == null ? null : (Room)serializer.deserialize(data); }
        public boolean remove(String code) { data = null; return true; }
        public boolean exists(String code) { return data != null; }
        public boolean codeExists(String code) { return exists(code); }
        public List<Room> findAll() { return List.of(get("room")); }
    }
    static class SnapshotSessions implements com.xiyouji.service.session.SessionStore {
        final RedisSerializer<Object> serializer; byte[] data;
        SnapshotSessions(RedisSerializer<Object> serializer) { this.serializer = serializer; }
        public void put(String id,GameSession s) { s.setStateVersion(s.getStateVersion() + 1); data = serializer.serialize(s); }
        public GameSession get(String id) { return data == null ? null : (GameSession)serializer.deserialize(data); }
        public boolean remove(String id) { data = null; return true; }
        public boolean exists(String id) { return data != null; }
    }
}
