package com.xiyouji.service.event;

import com.xiyouji.model.GameCharacter;
import com.xiyouji.service.room.RoomPlayer;
import com.xiyouji.service.session.GameSession;
import java.util.ArrayList;
import java.util.function.IntConsumer;

/** Adapts both modes to the same rule engine; room changes are staged on copies. */
public record EventActor(String userId, GameCharacter character, IntConsumer blockReward, Runnable commit) {
    public static EventActor solo(GameSession session) {
        return new EventActor(session.getOwnerUserId() == null ? "solo" : session.getOwnerUserId(),
            session.getPlayer(), n -> session.setNextBattleBlock(session.getNextBattleBlock() + n), () -> { });
    }
    public static EventActor room(RoomPlayer player) {
        GameCharacter copy = new GameCharacter();
        copy.setHp(player.getHp()); copy.setMaxHp(player.getMaxHp()); copy.setGold(player.getGold());
        copy.setCharacterClass(player.getCharacterClass());
        copy.setDeck(new ArrayList<>(player.getDeck().stream().map(c -> c.copy()).toList()));
        copy.setRelics(new ArrayList<>(player.getRelics()));
        return new EventActor(player.getUserId(), copy,
            n -> player.setNextBattleBlock(player.getNextBattleBlock() + n), () -> player.syncFromCharacter(copy));
    }
}
