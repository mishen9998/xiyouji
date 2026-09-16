package com.xiyouji.service.event;

import com.xiyouji.event.EventDefinition;
import com.xiyouji.event.EventDefinition.Option;
import com.xiyouji.event.EventDefinition.Reward;
import com.xiyouji.model.MapNode;
import java.util.List;

public final class EventCatalog {
    private EventCatalog() { }
    private static Option option(String id, String label, int hpPercent, int hp, int gold,
                                 Reward reward, int amount, int goldReward) {
        return new Option(id, label, hpPercent, hp, gold, reward, amount, goldReward);
    }
    public static final List<EventDefinition> ALL = List.of(
        new EventDefinition("fork-road", "双叉岭迷途", "暮色里山道分岔，樵夫愿带你们穿过荆棘，也肯用随身草药换些盘缠。西行的第一道选择，就藏在脚下的路里。", List.of(
            option("option1", "穿过荆棘：失去最大生命8%，获得20金币", 8,0,0,Reward.GOLD,20,0),
            option("option2", "向樵夫买药：花10金币，回复最大生命10%",0,0,10,Reward.HEAL,10,0))),
        new EventDefinition("cassock", "禅院护袈裟", "禅院夜火将起，师徒赶忙护住袈裟。老僧见你们不为宝物争执，愿传一式护身功夫，或替伤者煎一碗汤药。", List.of(
            option("option1", "护住袈裟：失去最大生命10%，升级1张牌",10,0,0,Reward.UPGRADE,1,0),
            option("option2", "请僧人煎药：花15金币，回复最大生命20%",0,0,15,Reward.HEAL,20,0))),
        new EventDefinition("mountain-path", "山神密径", "山神拨开青藤，露出一条少有人知的窄径。前路崎岖，却能磨练本领；若沿溪慢行，也能拾得旅人遗落的铜钱。", List.of(
            option("option1", "踏上密径：失去6生命，获得普通卡牌",0,6,0,Reward.CARD,1,0),
            option("option2", "沿溪寻路：获得10金币",0,0,0,Reward.GOLD,10,0))),
        new EventDefinition("fire-song", "火云洞童谣", "火云洞外传来童谣，孩子们把清水藏在阴凉处。你们可以留下盘缠换取歇脚之地，也可顶着热浪替他们送一趟信。", List.of(
            option("option1", "换水歇脚：花15金币，回复最大生命15%",0,0,15,Reward.HEAL,15,0),
            option("option2", "穿越热浪送信：失去最大生命8%，获得25金币",8,0,0,Reward.GOLD,25,0))),
        new EventDefinition("fan-wind", "芭蕉扇借风", "铁扇公主尚未应允借扇，山口已卷起狂风。师徒在风里演练招式，也可以整理行囊，放下不再需要的基础功夫。", List.of(
            option("option1", "迎风练功：失去最大生命12%，升级1张牌",12,0,0,Reward.UPGRADE,1,0),
            option("option2", "整理行囊：花10金币，移除1张基础牌",0,0,10,Reward.REMOVE_BASIC,1,0))),
        new EventDefinition("spring", "落胎泉取水", "泉水清凉，守泉人愿以一瓢甘泉助你们继续西行。泉底还有一件古物，若愿潜入冷水，便能将它带回岸上。", List.of(
            option("option1", "换取泉水：花20金币，回复最大生命25%",0,0,20,Reward.HEAL,25,0),
            option("option2", "潜水寻宝：失去8生命，获得普通遗物",0,8,0,Reward.RELIC,1,0))),
        new EventDefinition("refugees", "狮驼岭难民", "山路旁挤着逃难的百姓，锅里只剩一点薄粥。你们可以买粮与大家同食，也能冒险穿过妖风，把失散的乡亲接回来。", List.of(
            option("option1", "买粮休整：花20金币，回复最大生命12%",0,0,20,Reward.HEAL,12,0),
            option("option2", "穿过妖风救人：失去最大生命10%，获得30金币",10,0,0,Reward.GOLD,30,0))),
        new EventDefinition("patrol", "小钻风巡山", "小钻风拦住去路，口中反复念着巡山口令。你们可以用一门独门本领换通行盘缠，或迎上巡逻队，在交手中精进招式。", List.of(
            option("option1", "交换本领：移除1张非基础牌，获得45金币",0,0,0,Reward.REMOVE_NON_BASIC,1,45),
            option("option2", "迎战巡逻队：失去最大生命12%，升级1张牌",12,0,0,Reward.UPGRADE,1,0))),
        new EventDefinition("resolve", "灵山前问心", "灵山已在云端，师徒停步回望来路。走过火焰与妖风，最后要带上的是一副健全身躯，还是临阵不退的坚定心意？", List.of(
            option("option1", "整顿身心：花30金币，生命回复至至少80%",0,0,30,Reward.HEAL_TO,80,0),
            option("option2", "坚定心意：下一战开局获得8格挡",0,0,0,Reward.NEXT_BATTLE_BLOCK,8,0)))
    );
    public static EventDefinition get(String id) {
        return ALL.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }
    public static EventDefinition forNode(MapNode node) {
        int chapter = Math.max(0, Math.min(2, node.getLayer() - 1));
        return ALL.get(chapter * 3 + Math.floorMod(node.getRow() + node.getCol(), 3));
    }
}
