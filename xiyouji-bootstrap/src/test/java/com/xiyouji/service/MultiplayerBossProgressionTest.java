package com.xiyouji.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyouji.constants.GameConstants;
import com.xiyouji.dto.response.room.RoomDTO;
import com.xiyouji.model.Card;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.Relic;
import com.xiyouji.model.enums.CardType;
import com.xiyouji.model.enums.Rarity;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.port.CharacterRepositoryPort;
import com.xiyouji.port.EnemyRepositoryPort;
import com.xiyouji.service.battle.CardPlayHandler;
import com.xiyouji.service.battle.MultiplayerBattleInfoAssembler;
import com.xiyouji.service.battle.MultiplayerBattleStarter;
import com.xiyouji.service.battle.MultiplayerTurnCoordinator;
import com.xiyouji.service.battle.RewardService;
import com.xiyouji.service.room.LocalDistributedLockService;
import com.xiyouji.service.room.MultiplayerBattleState;
import com.xiyouji.service.room.MultiplayerBattleStore;
import com.xiyouji.service.room.MultiplayerMapService;
import com.xiyouji.service.room.MultiplayerPlayer;
import com.xiyouji.service.room.Room;
import com.xiyouji.service.room.RoomAccess;
import com.xiyouji.service.room.RoomDTOAssembler;
import com.xiyouji.service.room.RoomEventProcessor;
import com.xiyouji.service.room.RoomEventPublisher;
import com.xiyouji.service.room.RoomMembershipService;
import com.xiyouji.service.room.RoomPlayer;
import com.xiyouji.service.room.RoomProgressionService;
import com.xiyouji.service.room.RoomService;
import com.xiyouji.service.room.RoomStatus;
import com.xiyouji.service.room.RoomStore;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiplayerBossProgressionTest {

    private static final String ROOM_CODE = "BOSS1234";
    private static final String HOST_ID = "host";

    @ParameterizedTest(name = "{0} floor {1} -> {2} {3}")
    @CsvSource({
            "BATTLE, 1, 1, IN_MAP",
            "BOSS, 1, 2, IN_MAP",
            "BOSS, 2, 3, IN_MAP",
            "BOSS, 3, 3, FINISHED"
    })
    void returnToMapPersistsPlayersAndReportsAuthoritativeRoom(
            String nodeType, int initialFloor, int expectedFloor, RoomStatus expectedStatus) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        AtomicReference<byte[]> persisted = new AtomicReference<>();
        RoomStore roomStore = mock(RoomStore.class);
        // Mimic Redis: every read and write crosses a serialization boundary.
        when(roomStore.get(ROOM_CODE)).thenAnswer(invocation ->
                mapper.readValue(persisted.get(), Room.class));
        doAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setStateVersion(room.getStateVersion() + 1);
            persisted.set(mapper.writeValueAsBytes(room));
            return null;
        }).when(roomStore).save(any(Room.class));

        Room initialRoom = new Room(ROOM_CODE, HOST_ID);
        initialRoom.setFloor(initialFloor);
        initialRoom.setStatus(RoomStatus.IN_BATTLE);
        initialRoom.setCurrentNode(new MapNode("node", initialFloor, 9, 1, nodeType, "Battle"));
        RoomPlayer member = new RoomPlayer(HOST_ID, "Host");
        member.setHp(80);
        member.setMaxHp(80);
        member.setGold(100);
        initialRoom.getPlayers().add(member);
        roomStore.save(initialRoom);

        LocalDistributedLockService locks = new LocalDistributedLockService();
        RoomAccess access = new RoomAccess(roomStore, locks);
        RoomDTOAssembler assembler = new RoomDTOAssembler();
        EnemyRepositoryPort enemyRepository = mock(EnemyRepositoryPort.class);
        java.util.concurrent.atomic.AtomicLong enemyIds = new java.util.concurrent.atomic.AtomicLong(1);
        when(enemyRepository.findAll()).thenReturn(com.xiyouji.combat.EnemyContentCatalog.entries().stream()
                .map(entry -> {
                    var enemy = new com.xiyouji.model.Enemy(entry.name(), 100, 10, 5, entry.boss(), entry.level());
                    enemy.setId(enemyIds.getAndIncrement());
                    return enemy;
                }).toList());
        RoomProgressionService progression = new RoomProgressionService(access,
                new MultiplayerMapService(enemyRepository),
                mock(CharacterRepositoryPort.class), mock(CardRepositoryPort.class), assembler);
        RoomService roomService = new RoomService(access, assembler,
                mock(RoomMembershipService.class), progression, mock(RoomEventProcessor.class));
        MultiplayerBattleStore battleStore = mock(MultiplayerBattleStore.class);
        RoomEventPublisher broadcaster = mock(RoomEventPublisher.class);
        MultiplayerBattleService battleService = new MultiplayerBattleService(
                roomService, battleStore, broadcaster, locks, null,
                mock(MultiplayerBattleStarter.class), mock(CardPlayHandler.class),
                mock(MultiplayerTurnCoordinator.class), mock(RewardService.class),
                new MultiplayerBattleInfoAssembler());

        GameCharacter character = new GameCharacter();
        character.setMaxHp(85);
        character.setHp(43);
        character.setGold(187);
        character.getDeck().add(new Card("reward", "", CardType.ATTACK, Rarity.COMMON, null, 1));
        Relic relic = new Relic();
        relic.setName("relic reward");
        character.getRelics().add(relic);
        MultiplayerBattleState battle = new MultiplayerBattleState(ROOM_CODE);
        battle.setBattleOver(true);
        battle.setVictory(true);
        battle.setRewardsHandled(true);
        battle.getPlayers().add(new MultiplayerPlayer(HOST_ID, "Host", character));
        when(battleStore.get(ROOM_CODE)).thenReturn(battle);
        Room staleSnapshot = roomStore.get(ROOM_CODE);

        Map<String, Object> result = battleService.returnToMap(ROOM_CODE, HOST_ID);

        Room savedRoom = roomStore.get(ROOM_CODE);
        assertNotSame(staleSnapshot, savedRoom);
        assertEquals(initialFloor, staleSnapshot.getFloor());
        assertEquals(RoomStatus.IN_BATTLE, staleSnapshot.getStatus());
        assertEquals(80, staleSnapshot.getPlayers().get(0).getHp());
        assertEquals(expectedFloor, savedRoom.getFloor());
        assertEquals(expectedStatus, savedRoom.getStatus());
        assertEquals(2, savedRoom.getStateVersion());
        assertPlayerState(savedRoom.getPlayers().get(0));

        boolean boss = GameConstants.NODE_BOSS.equals(nodeType);
        boolean completed = expectedStatus == RoomStatus.FINISHED;
        String expectedMessage = completed ? "恭喜通关！西天取经圆满！"
                : boss ? "进入第 " + expectedFloor + " 层" : "返回地图探索";
        assertEquals(completed, Boolean.TRUE.equals(result.get("completed")));
        assertEquals(boss && !completed, Boolean.TRUE.equals(result.get("nextLayer")));
        assertEquals(expectedMessage, result.get("message"));
        if (boss && !completed) {
            assertEquals(expectedFloor, result.get("floor"));
            assertNull(savedRoom.getCurrentNode());
            assertFalse(savedRoom.getMap().isEmpty());
        }
        ArgumentCaptor<RoomDTO> broadcastRoom = ArgumentCaptor.forClass(RoomDTO.class);
        verify(broadcaster).broadcastRoomUpdate(eq(ROOM_CODE), broadcastRoom.capture());
        assertEquals(expectedFloor, broadcastRoom.getValue().getFloor());
        assertEquals(expectedStatus, broadcastRoom.getValue().getStatus());
        assertEquals(savedRoom.getStateVersion(), broadcastRoom.getValue().getStateVersion());
        assertPlayerState(broadcastRoom.getValue().getPlayers().get(0));
        verify(broadcaster).broadcastSystemMessage(ROOM_CODE, expectedMessage);
        verify(battleStore).remove(ROOM_CODE);
        verify(roomStore, times(2)).save(any(Room.class));
    }

    private static void assertPlayerState(RoomPlayer player) {
        assertEquals(43, player.getHp());
        assertEquals(85, player.getMaxHp());
        assertEquals(187, player.getGold());
        assertEquals(1, player.getDeck().size());
        assertEquals("reward", player.getDeck().get(0).getName());
        assertEquals(1, player.getRelics().size());
        assertEquals("relic reward", player.getRelics().get(0).getName());
    }
}
