package com.xiyouji.constants;

/**
 * 游戏常量 - 集中管理魔法数字和硬编码字符串
 */
public final class GameConstants {
    private GameConstants() {}

    // 地图配置 - 27行地图，行0=唐朝皇帝赐宝，行26=Boss，行1-25=随机多路线
    public static final int ROWS_PER_LAYER = 27;
    public static final int MAX_LAYERS = 3;
    public static final int EMPEROR_ROW = 0;       // 唐朝皇帝赐宝行
    public static final int BOSS_ROW = 26;         // Boss行
    public static final int FIRST_RANDOM_ROW = 1;  // 随机节点起始行
    public static final int LAST_RANDOM_ROW = 25;   // 随机节点结束行

    // 节点概率（中间行随机分布）
    public static final double BATTLE_NODE_PROBABILITY = 0.50;
    public static final double REST_NODE_PROBABILITY = 0.62;
    public static final double TREASURE_NODE_PROBABILITY = 0.74;
    public static final double SHOP_NODE_PROBABILITY = 0.84;
    public static final double BONFIRE_NODE_PROBABILITY = 0.92;

    // 唐朝皇帝三选一物品数量
    public static final int EMPEROR_REWARD_CHOICES = 3;

    // 奖励
    public static final int BASE_GOLD_REWARD = 25;
    public static final int GOLD_PER_LAYER = 5;
    public static final int BOSS_GOLD_BONUS = 50;
    public static final int CARD_REWARD_COUNT = 5;
    public static final double RELIC_DROP_RATE = 0.25;

    // 商店
    public static final int SHOP_CARD_PRICE = 50;
    public static final int SHOP_DISCOUNT_PERCENT = 20;

    // 篝火
    public static final int BONFIRE_UPGRADE_LIMIT = 2;

    // 战斗
    public static final int INITIAL_HAND_SIZE = 5;
    public static final int MAX_HAND_SIZE = 10;
    public static final int MAX_ENERGY = 3;

    // 地点名称
    public static final String[] PLACE_NAMES = {
        "黑风山","黄风岭","盘丝洞","火焰山","莲花洞","通天河",
        "高老庄","女儿国","宝象国","乌鸡国","车迟国","比丘国",
        "凤仙郡","翠云山","芭蕉洞","积雷山","碧波潭","荆棘岭",
        "小西天","朱紫国","狮驼岭","流沙河","白虎岭","平顶山"
    };

    // 节点类型
    public static final String NODE_BATTLE = "BATTLE";
    public static final String NODE_BOSS = "BOSS";
    public static final String NODE_REST = "REST";
    public static final String NODE_TREASURE = "TREASURE";
    public static final String NODE_SHOP = "SHOP";
    public static final String NODE_RANDOM = "RANDOM";
    public static final String NODE_BONFIRE = "BONFIRE";
    public static final String NODE_EMPEROR = "EMPEROR"; // 唐朝皇帝赐宝节点

    // ====== 唐朝皇帝8件御赐宝物 ======
    public static final String RELIC_EMPEROR_GOLDEN_BOWL = "御赐金钵";          // 战斗开始+30金币
    public static final String RELIC_EMPEROR_PURPLE_BOWL = "紫金钵盂";          // 战斗开始+1能量
    public static final String RELIC_EMPEROR_PASSPORT = "大唐通关文牒";         // 每层开始+20生命
    public static final String RELIC_EMPEROR_SWORD = "李世民御剑";              // 战斗开始+2力量
    public static final String RELIC_EMPEROR_STAFF = "玄奘九环锡杖";           // 战斗开始+2敏捷
    public static final String RELIC_EMPEROR_TIGER_TALLY = "御林军虎符";        // 战斗开始+10格挡
    public static final String RELIC_EMPEROR_GLASS_CUP = "御赐琉璃盏";          // 每回合多抽1牌
    public static final String RELIC_EMPEROR_JADE_SEAL = "太宗玉玺";           // 战斗金币翻倍
    public static final String[] EMPEROR_RELICS = {
        RELIC_EMPEROR_GOLDEN_BOWL,
        RELIC_EMPEROR_PURPLE_BOWL,
        RELIC_EMPEROR_PASSPORT,
        RELIC_EMPEROR_SWORD,
        RELIC_EMPEROR_STAFF,
        RELIC_EMPEROR_TIGER_TALLY,
        RELIC_EMPEROR_GLASS_CUP,
        RELIC_EMPEROR_JADE_SEAL
    };

    // 遗物名称常量
    public static final String RELIC_JINGUZHOU = "紧箍咒";
    public static final String RELIC_DINGHAI = "定海神针";
    public static final String RELIC_RENSHENGUO = "人参果";
    public static final String RELIC_ZHAOYAOJING = "照妖镜";
    public static final String RELIC_LONGLINJIA = "龙鳞甲";
    public static final String RELIC_FENGHUOLUN = "风火轮";
    public static final String RELIC_BAGUALU = "八卦炉";
    public static final String RELIC_ZIJINLING = "紫金铃";
    public static final String RELIC_JIUCHIDINGPA = "九齿钉耙";
    public static final String RELIC_TONGGUANWENDIE = "通关文牒";
    public static final String RELIC_JINLANJIASHA = "锦襕袈裟";
}
