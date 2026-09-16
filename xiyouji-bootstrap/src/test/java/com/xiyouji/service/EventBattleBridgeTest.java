package com.xiyouji.service;

import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import com.xiyouji.port.*;
import com.xiyouji.service.battle.*;
import com.xiyouji.service.room.*;
import com.xiyouji.service.session.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventBattleBridgeTest {
    private Enemy enemy() { Enemy e = new Enemy("enemy",100,10,3,false,1); e.setId(1L); return e; }
    private MapNode node() { MapNode n = new MapNode("battle",1,1,0,"BATTLE","battle"); n.setEnemyId("1"); return n; }
    @Test void soloEightBlockIsConsumedForExactlyTheNextBattleBeforeForecast() {
        EnemyRepositoryPort enemies = mock(EnemyRepositoryPort.class); when(enemies.findById(1L)).thenReturn(Optional.of(enemy()));
        InMemorySessionStore sessions = new InMemorySessionStore();
        GameService game = new GameService(mock(CharacterRepositoryPort.class),mock(CardRepositoryPort.class),mock(RelicRepositoryPort.class),mock(MapService.class),mock(ShopService.class),sessions,new LocalDistributedLockService());
        GameCharacter p = new GameCharacter(); p.setCharacterClass(CharacterClass.SUN_WUKONG); p.setMaxHp(80); p.setHp(50);
        GameSession s = new GameSession("solo",p,new ArrayList<>()); s.setCurrentNode(node()); s.setNextBattleBlock(8); sessions.put("solo",s);
        SoloBattleStarter starter = new SoloBattleStarter(game,enemies,new SoloRelicTriggers());
        starter.start("solo"); assertEquals(8,p.getBlock()); assertEquals(0,s.getNextBattleBlock());
        assertNotNull(s.getBattle().getEnemy().getLockedAction());
        starter.start("solo"); assertEquals(0,p.getBlock());
    }
    @Test void roomPersistsConsumptionAndEachLivingPlayerStartsWithOwnBlock() {
        EnemyRepositoryPort enemies = mock(EnemyRepositoryPort.class); when(enemies.findById(1L)).thenReturn(Optional.of(enemy()));
        CharacterRepositoryPort chars = mock(CharacterRepositoryPort.class);
        GameCharacter template = new GameCharacter(); template.setMaxHp(80);
        when(chars.findByCharacterClass(any())).thenReturn(Optional.of(template));
        InMemoryRoomStore rooms = new InMemoryRoomStore(); Room r = new Room("room","u0"); r.setStatus(RoomStatus.IN_MAP); r.setCurrentNode(node());
        for (int i = 0; i < 2; i++) { RoomPlayer p = new RoomPlayer("u" + i,"name"); p.setCharacterClass(CharacterClass.SUN_WUKONG); p.setHp(50); p.setMaxHp(80); p.setNextBattleBlock(8); r.getPlayers().add(p); }
        rooms.save(r);
        RoomService service = new RoomService(new RoomAccess(rooms,new LocalDistributedLockService()),new RoomDTOAssembler(),null,null,null);
        MultiplayerBattleStarter starter = new MultiplayerBattleStarter(service,mock(MultiplayerBattleStore.class),chars,enemies);
        var battle = starter.start("room","u0",r.getStateVersion()).state();
        assertTrue(battle.getPlayers().stream().allMatch(p -> p.getCharacter().getBlock() == 8));
        assertTrue(rooms.get("room").getPlayers().stream().allMatch(p -> p.getNextBattleBlock() == 0));
        assertNotNull(battle.getEnemy().getLockedAction());
        r.setStatus(RoomStatus.IN_MAP);
        var next = starter.start("room","u0",r.getStateVersion()).state();
        assertTrue(next.getPlayers().stream().allMatch(p -> p.getCharacter().getBlock() == 0));
    }
}
