package com.xiyouji.service;

import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.port.EnemyRepositoryPort;
import com.xiyouji.service.session.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapRewardGuardTest {
    @Test void cannotLeavePendingRewardsAndClearsOldBattleAfterResolution() {
        MapService maps = new MapService(mock(EnemyRepositoryPort.class));
        MapNode next = new MapNode("next", 1, 2, 0, "REST", "rest");
        next.setAccessible(true);
        GameSession session = new GameSession("test", new GameCharacter(), List.of(next));
        BattleState battle = new BattleState();
        battle.setVictory(true); battle.setBattleOver(true); battle.setRewardsHandled(true);
        battle.setCardRewards(List.of(new Card("reward", "", CardType.ATTACK, Rarity.COMMON, null, 1)));
        session.setBattle(battle);
        assertThrows(InvalidActionException.class, () -> maps.moveToNode(session, "next"));
        assertThrows(InvalidActionException.class, () -> maps.advanceToNextLayer(session));
        assertSame(battle, session.getBattle());
        battle.setCardRewards(null);
        assertSame(next, maps.moveToNode(session, "next"));
        assertNull(session.getBattle());
    }
}
