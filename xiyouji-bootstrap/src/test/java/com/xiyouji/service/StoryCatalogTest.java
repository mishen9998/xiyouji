package com.xiyouji.service;

import com.xiyouji.model.*;
import com.xiyouji.service.event.StoryCatalog;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StoryCatalogTest {
    @Test void allNarrativeTriggersAreReadOnlySkippableAndStable() {
        var opening = StoryCatalog.map("run",1,null,false,List.of());
        assertEquals(List.of("DEPARTURE","CHAPTER_START"),opening.scenes().stream().map(s -> s.trigger()).toList());
        for (int floor = 1; floor <= 3; floor++) {
            assertEquals("CHAPTER_START",StoryCatalog.map("run",floor,null,false,List.of()).scenes().get(floor == 1 ? 1 : 0).trigger());
            MapNode node = new MapNode("boss",floor,26,1,"BOSS","boss");
            assertEquals("BOSS_BEFORE",StoryCatalog.map("run",floor,node,false,List.of()).scenes().get(0).trigger());
            Enemy enemy = new Enemy("Boss",100,10,3,true,floor); enemy.setHp(50);
            var half = StoryCatalog.battle("run:boss",floor,enemy,false,false);
            assertEquals(List.of("BOSS_BEFORE","BOSS_HALF"),half.scenes().stream().map(s -> s.trigger()).toList());
            var after = StoryCatalog.battle("run:boss",floor,enemy,true,true);
            assertEquals("AFTER_BATTLE",after.scenes().get(2).trigger());
            assertTrue(after.scenes().stream().allMatch(s -> s.skippable() && s.text().length() >= 30 && s.text().length() <= 100));
            assertEquals(50,enemy.getHp());
            assertEquals(after,StoryCatalog.battle("run:boss",floor,enemy,true,true));
        }
        assertEquals("COMPLETE",StoryCatalog.map("run",3,null,true,List.of()).scenes().get(0).trigger());
    }
}
