package com.xiyouji.service.event;

import com.xiyouji.dto.response.EventPreview;
import com.xiyouji.dto.response.StoryEvent;
import com.xiyouji.model.Enemy;
import com.xiyouji.model.MapNode;
import java.util.ArrayList;
import java.util.List;

public final class StoryCatalog {
    private StoryCatalog() { }
    private static final String[] CHAPTERS = {"黑风山·护袈裟", "火焰山·借清风", "狮驼岭·救苍生"};
    private static final String[] OPEN = {
        "黑风山雾气渐浓，禅院钟声从林间传来。袈裟虽是宝物，师徒更要护住沿途百姓。穿过这片山林，西行才算真正开始。",
        "火焰山挡住西行的路，村中井水将尽。师徒决定借来芭蕉扇，先为百姓留下一阵清风，再踏过灼热的山口。",
        "狮驼岭妖风蔽日，求救声却仍从山谷传来。灵山近在前方，你们放慢脚步，决意带被困的人们一同走出阴影。"
    };
    private static final String[] BEFORE = {
        "黑熊精抱着袈裟守在洞口，不肯轻易归还。悟空提棒上前，唐僧低声提醒：夺回袈裟，也要让山中重归安宁。",
        "牛魔王踏着热浪现身，铁扇在风中猎猎作响。这一战关系着山下田地与井水；师徒站定阵势，准备迎住冲锋。",
        "大鹏从云顶俯冲，双翼遮住灵山前最后一道光。你们身后是刚获救的人群，眼前这一步，谁也不会后退。"
    };
    private static final String[] HALF = {
        "黑熊精退后半步，仍把袈裟攥得很紧：好本领！再接我一招！师徒互相照应，洞外的山风终于吹散了浓烟。",
        "牛魔王喘着粗气，将兵器重重顿在地上：想借这阵风，还得过我这一关！你们稳住脚步，等他露出下一处破绽。",
        "大鹏的羽翼掠过岩壁：灵山就在眼前，你们还能走多远？悟空举棒答道：只要众人平安，我们就走到底。"
    };
    private static final String[] AFTER = {
        "袈裟终于物归原主，黑风洞外的百姓点起灯火。师徒收拾行囊，向更远的山路走去；护住的，远不止一件宝物。",
        "清风越过山脊，火焰渐渐熄灭。干裂的田地迎来第一场雨，师徒听着村中欢呼，再次踏上通往西方的路。",
        "大鹏收拢羽翼，狮驼岭重见天光。被救的百姓走出山谷，向师徒挥手。前方钟声响起，灵山已不再遥远。"
    };
    private static StoryEvent.Scene scene(String id, String trigger, String title, String text) {
        return new StoryEvent.Scene(id + ":" + trigger, trigger, title, text, true);
    }
    public static StoryEvent map(String id, int floor, MapNode node, boolean completed, List<EventActor> actors) {
        List<StoryEvent.Scene> scenes = new ArrayList<>();
        int chapter = Math.max(0, Math.min(2, floor - 1));
        if (completed) scenes.add(scene(id,"COMPLETE","取经归来","灵山钟声回荡，师徒终于走到经卷之前。回望来路，有黑风山的灯火、火焰山的清雨，也有狮驼岭获救的人们。真经记下道理，这一路的相扶相助则让道理有了温度。带着它们回到人间，西行的故事仍会继续。"));
        else if (node == null) {
            if (floor == 1) scenes.add(scene(id,"DEPARTURE","长安辞行","长安城门缓缓打开，唐僧接过通关文牒，师徒约定一路相扶。山高水长，前方既有妖魔，也有盼望援手的凡人。悟空望向西方，笑着催大家上路：不必一口气走到灵山，先把眼前这一程走好。"));
            scenes.add(scene(id + ":" + floor,"CHAPTER_START",CHAPTERS[chapter],OPEN[chapter]));
        } else if ("BOSS".equals(node.getType())) scenes.add(scene(id + ":" + node.getId(),"BOSS_BEFORE",CHAPTERS[chapter],BEFORE[chapter]));
        EventPreview event = node != null && node.getEventState() != null ? EventEngine.preview(node.getEventState(),actors) : null;
        return new StoryEvent(scenes,event);
    }
    public static StoryEvent event(EventPreview event) {
        return new StoryEvent(List.of(scene(event.eventInstanceId(),"EVENT",event.title(),event.text())),event);
    }
    public static StoryEvent battle(String id, int floor, Enemy enemy, boolean over, boolean victory) {
        int chapter = Math.max(0, Math.min(2, floor - 1));
        List<StoryEvent.Scene> scenes = new ArrayList<>();
        if (enemy.isBoss()) {
            scenes.add(scene(id,"BOSS_BEFORE",CHAPTERS[chapter],BEFORE[chapter]));
            if (enemy.getHp() * 2L <= enemy.getMaxHp()) scenes.add(scene(id,"BOSS_HALF",enemy.getName(),HALF[chapter]));
        }
        if (over && victory) scenes.add(scene(id,"AFTER_BATTLE","山路再启",enemy.isBoss() ? AFTER[chapter] : "山道恢复了平静，师徒清点行囊，为彼此包扎伤口。远处还有新的路口与等候帮助的人，稍作整顿便继续向西。"));
        return new StoryEvent(scenes,null);
    }
}
