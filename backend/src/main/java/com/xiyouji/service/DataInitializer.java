package com.xiyouji.service;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import com.xiyouji.repository.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CardRepository cr;
    private final EnemyRepository er;
    private final CharacterRepository chr;
    private final RelicRepository rr;

    public DataInitializer(CardRepository cr, EnemyRepository er,
                          CharacterRepository chr, RelicRepository rr) {
        this.cr = cr; this.er = er; this.chr = chr; this.rr = rr;
    }

    @PostConstruct
    public void init() {
        if (cr.count() == 0) {
            initCards();
            initEnemies();
            initCharacters();
            initRelics();
            log.info("种子数据已加载完成");
        } else {
            // 增量迁移：已有数据库中补充唐三藏相关数据
            migrateTangSanzang();
            // 增量迁移：扩展更多卡牌
            migrateExtraCards();
            // 增量迁移：第四批扩展卡牌
            migrateExpansionCards();
            // 增量迁移：第五批扩展卡牌
            migrateFifthBatchCards();
            // 增量迁移：第六批扩展卡牌（冲刺200张）
            migrateSixthBatchCards();
        }
        // 增量迁移：扩展更多宝物
        migrateExtraRelics();
        // 增量迁移：扩展更多敌人（按西游妖怪榜）
        migrateExtraEnemies();
        // 增量迁移：唐朝皇帝8件御赐宝物
        migrateEmperorRelics();
        // 增量迁移：第二批扩展宝物
        migrateExpansionRelics();
        // 增量迁移：第三批扩展宝物（冲刺60件）
        migrateExpansionRelics2();
        // 同步卡牌"下回合"效果字段（兼容已有数据库，补齐新增字段）
        syncCardNextTurnEffects();
        // 同步扩展敌人emoji字段（用于无图时头像回退显示）
        syncEnemyEmojis();
    }

    /** 增量迁移：添加唐三藏角色、卡牌、遗物到已有数据库 */
    private void migrateTangSanzang() {
        // 检查唐三藏角色是否已存在
        if (chr.findByCharacterClass(CharacterClass.TANG_SANZANG).isPresent()) {
            return; // 已迁移过
        }
        log.info("开始迁移：添加唐三藏角色数据...");

        // 添加唐三藏专属卡牌
        mkb("紧箍咒念","造成5点伤害。施加1层虚弱。",CardType.ATTACK,Rarity.COMMON,CharacterClass.TANG_SANZANG,1,5,0,3,0, 0,0,0,1,0,0,0);
        mkb("大乘佛法","回复6点生命值。获得3点格挡。",CardType.SKILL,Rarity.COMMON,CharacterClass.TANG_SANZANG,1,0,3,0,2, 0,0,0,0,0,6,0);
        mkb("金蝉脱壳","获得8点格挡。抽1张牌。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.TANG_SANZANG,1,0,8,0,3, 0,0,0,0,0,0,1);
        mkb("普渡众生","回复10点生命值。获得5点格挡。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,2,0,5,0,3, 0,0,0,0,0,10,0);
        mkb("心经","获得2点敏捷。下回合多抽1张牌。",CardType.POWER,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,0,0,0,0, 0,2,0,0,0,0,0);
        mkb("取经誓愿","造成14点伤害。回复5点生命值。",CardType.ATTACK,Rarity.RARE,CharacterClass.TANG_SANZANG,2,14,0,5,0, 0,0,0,0,0,5,0);

        // 添加唐三藏角色
        sc(CharacterClass.TANG_SANZANG, 80, "锦襕袈裟");

        // 添加唐三藏专属遗物
        rl("锦襕袈裟","每回合开始时回复2点生命值。（唐三藏专属）",RelicTier.SPECIAL,CharacterClass.TANG_SANZANG);

        log.info("唐三藏角色数据迁移完成");
    }

    /** 增量迁移：扩展更多卡牌到已有数据库 */
    private void migrateExtraCards() {
        // 第一批扩展卡牌检查（以"旋风斩"为标志）
        if (cr.findByName("旋风斩").isEmpty()) {
            log.info("开始迁移：添加第一批扩展卡牌...");
            // 只添加第一批（前17张），然后继续检查第二批
            migrateFirstBatchCards();
        }
        // 第二批扩展卡牌检查（以"怒目金刚"为标志）
        if (cr.findByName("怒目金刚").isEmpty()) {
            log.info("开始迁移：添加第二批扩展卡牌...");
            migrateSecondBatchCards();
        }
        // 第三批扩展卡牌检查（以"烈焰掌"为标志）
        if (cr.findByName("烈焰掌").isEmpty()) {
            log.info("开始迁移：添加第三批扩展卡牌...");
            migrateThirdBatchCards();
        }
    }

    /** 第一批扩展卡牌（兼容已有调用） */
    private void migrateFirstBatchCards() {
        mkb("旋风斩","造成7点伤害。抽1张牌。",CardType.ATTACK,Rarity.COMMON,null,1,7,0,3,0, 0,0,0,0,0,0,1);
        mkb("铁布衫","获得6点格挡。回复3点生命值。",CardType.DEFENSE,Rarity.COMMON,null,1,0,6,0,3, 0,0,0,0,0,3,0);
        mkb("飞镖","造成2点伤害。施加2层中毒。抽1张牌。",CardType.ATTACK,Rarity.UNCOMMON,null,1,2,0,2,0, 0,0,0,0,2,0,1);
        mkb("回春术","回复8点生命值。抽2张牌。",CardType.SKILL,Rarity.UNCOMMON,null,2,0,0,0,0, 0,0,0,0,0,8,2);
        mkb("狂暴","获得3点力量。失去2点生命值。消耗。",CardType.POWER,Rarity.RARE,null,1,0,0,0,0, 3,0,0,0,0,0,0);
        mkb("天罡阵","获得12点格挡。施加1层虚弱。",CardType.DEFENSE,Rarity.RARE,null,2,0,12,0,4, 0,0,0,1,0,0,0);
        mkb("定海神针","造成12点伤害。获得2点力量。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,2,12,0,4,0, 2,0,0,0,0,0,0);
        mkb("身外身","获得4点力量。抽2张牌。消耗。",CardType.POWER,Rarity.RARE,CharacterClass.SUN_WUKONG,1,0,0,0,0, 4,0,0,0,0,0,2);
        mkb("三十六变","获得10点格挡。回复5点生命值。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.ZHU_BAJIE,2,0,10,0,3, 0,0,0,0,0,5,0);
        mkb("净坛使者","获得2点力量。获得2点敏捷。",CardType.POWER,Rarity.RARE,CharacterClass.ZHU_BAJIE,2,0,0,0,0, 2,2,0,0,0,0,0);
        mkb("降魔阵","造成6点伤害。获得6点格挡。施加1层易伤。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SHA_SENG,1,6,6,3,2, 0,0,1,0,0,0,0);
        mkb("琉璃盏","回复8点生命值。获得8点格挡。消耗。",CardType.SKILL,Rarity.RARE,CharacterClass.SHA_SENG,1,0,8,0,3, 0,0,0,0,0,8,0);
        mkb("龙息","造成6点伤害。施加1层虚弱。抽1张牌。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,1,6,0,3,0, 0,0,0,1,0,0,1);
        mkb("水遁","获得9点格挡。抽1张牌。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,1,0,9,0,3, 0,0,0,0,0,0,1);
        mkb("九环锡杖","造成8点伤害。回复4点生命值。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,8,0,3,0, 0,0,0,0,0,4,0);
        mkb("袈裟护体","获得10点格挡。回复5点生命值。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,2,0,10,0,3, 0,0,0,0,0,5,0);
        mkb("超度","回复12点生命值。获得3点格挡。抽1张牌。",CardType.SKILL,Rarity.RARE,CharacterClass.TANG_SANZANG,2,0,3,0,2, 0,0,0,0,0,12,1);
        mkb("金身护体","获得2点敏捷。每回合开始时获得4点格挡。",CardType.POWER,Rarity.RARE,CharacterClass.TANG_SANZANG,2,0,0,0,0, 0,2,0,0,0,0,0);
        log.info("第一批扩展卡牌迁移完成");
    }

    /** 第二批扩展卡牌 */
    private void migrateSecondBatchCards() {
        mkb("怒目金刚","造成9点伤害。施加1层虚弱。",CardType.ATTACK,Rarity.UNCOMMON,null,1,9,0,4,0, 0,0,0,1,0,0,0);
        mkb("金刚经","获得7点格挡。抽1张牌。",CardType.DEFENSE,Rarity.COMMON,null,1,0,7,0,3, 0,0,0,0,0,0,1);
        mkb("迷魂术","施加2层易伤。抽1张牌。",CardType.SKILL,Rarity.UNCOMMON,null,1,0,0,0,0, 0,0,2,0,0,0,1);
        mkb("破甲击","造成5点伤害。对有格挡的敌人伤害翻倍。",CardType.ATTACK,Rarity.UNCOMMON,null,1,5,0,3,0, 0,0,0,0,0,0,0);
        mkb("法天象地","造成18点伤害。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.SUN_WUKONG,3,18,0,6,0, 0,0,0,0,0,0,0);
        mkb("哮天犬","造成4点伤害。抽2张牌。",CardType.ATTACK,Rarity.COMMON,CharacterClass.SUN_WUKONG,1,4,0,2,0, 0,0,0,0,0,0,2);
        mkb("倒打一耙","造成8点伤害。回复3点生命值。",CardType.ATTACK,Rarity.COMMON,CharacterClass.ZHU_BAJIE,1,8,0,3,0, 0,0,0,0,0,3,0);
        mkb("饕餮之口","造成10点伤害。回复等量生命值。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.ZHU_BAJIE,2,10,0,4,0, 0,0,0,0,0,0,0);
        mkb("降妖宝杖·真","造成15点伤害。获得5点格挡。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.SHA_SENG,2,15,5,5,2, 0,0,0,0,0,0,0);
        mkb("龙战于野","造成12点伤害。获得3点格挡。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,1,12,3,4,2, 0,0,0,0,0,0,0);
        mkb("佛光普照","回复6点生命值。获得5点格挡。施加1层虚弱。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,0,5,0,3, 0,0,0,1,0,6,0);
        mkb("紧箍咒·禁","造成3点伤害。施加2层虚弱和1层易伤。",CardType.ATTACK,Rarity.RARE,CharacterClass.TANG_SANZANG,1,3,0,2,0, 0,0,1,2,0,0,0);
        log.info("第二批扩展卡牌迁移完成，新增12张");
    }

    /** 第三批扩展卡牌 */
    private void migrateThirdBatchCards() {
        // ====== 通用卡牌（4张） ======
        mkb("烈焰掌","造成8点伤害。",CardType.ATTACK,Rarity.COMMON,null,1,8,0,4,0, 0,0,0,0,0,0,0);
        mkb("铜皮铁骨","获得9点格挡。回复2点生命值。",CardType.DEFENSE,Rarity.COMMON,null,1,0,9,0,3, 0,0,0,0,0,2,0);
        mkb("毒雾弥漫","施加3层中毒。抽1张牌。",CardType.SKILL,Rarity.UNCOMMON,null,1,0,0,0,0, 0,0,0,0,3,0,1);
        // 蓄势待发：需要设置 drawNextTurn
        Card xsdf = mkb("蓄势待发","获得1点力量。下回合多抽2张牌。",CardType.POWER,Rarity.RARE,null,1,0,0,0,0, 1,0,0,0,0,0,0);
        xsdf.setDrawNextTurn(2);
        cr.save(xsdf);

        // ====== 孙悟空（2张） ======
        mkb("筋斗云翻","获得6点格挡。抽1张牌。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.SUN_WUKONG,1,0,6,0,3, 0,0,0,0,0,0,1);
        // 斗战胜佛：消耗
        Card dzsf = mkb("斗战胜佛","获得3点力量。获得3点敏捷。消耗。",CardType.POWER,Rarity.RARE,CharacterClass.SUN_WUKONG,3,0,0,0,0, 3,3,0,0,0,0,0);
        dzsf.setExhaust(true);
        cr.save(dzsf);

        // ====== 猪八戒（2张） ======
        mkb("九齿横扫","造成6点伤害。获得4点格挡。",CardType.ATTACK,Rarity.COMMON,CharacterClass.ZHU_BAJIE,1,6,4,3,2, 0,0,0,0,0,0,0);
        // 天蓬元帅：消耗
        Card tpys = mkb("天蓬元帅","获得4点力量。消耗。",CardType.POWER,Rarity.RARE,CharacterClass.ZHU_BAJIE,2,0,0,0,0, 4,0,0,0,0,0,0);
        tpys.setExhaust(true);
        cr.save(tpys);

        // ====== 沙僧（2张） ======
        mkb("降妖钵盂","施加2层虚弱。回复5点生命值。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.SHA_SENG,1,0,0,0,0, 0,0,0,2,0,5,0);
        mkb("金身罗汉","获得3点敏捷。获得2点力量。",CardType.POWER,Rarity.RARE,CharacterClass.SHA_SENG,2,0,0,0,0, 2,3,0,0,0,0,0);

        // ====== 白龙马（1张） ======
        mkb("龙卷风暴","造成10点伤害。施加1层易伤。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,2,10,0,4,0, 0,0,1,0,0,0,0);

        // ====== 唐三藏（1张） ======
        // 般若波罗蜜：消耗
        Card brblm = mkb("般若波罗蜜","回复15点生命值。获得5点格挡。抽2张牌。消耗。",CardType.SKILL,Rarity.RARE,CharacterClass.TANG_SANZANG,2,0,5,0,3, 0,0,0,0,0,15,2);
        brblm.setExhaust(true);
        cr.save(brblm);

        log.info("第三批扩展卡牌迁移完成，新增12张");
    }

    /** 扩展卡牌定义（供 initCards 和 migrateExtraCards 共用） */
    private void addExtraCards() {
        // ====== 通用扩展卡牌 ======
        mkb("旋风斩","造成7点伤害。抽1张牌。",CardType.ATTACK,Rarity.COMMON,null,1,7,0,3,0, 0,0,0,0,0,0,1);
        mkb("铁布衫","获得6点格挡。回复3点生命值。",CardType.DEFENSE,Rarity.COMMON,null,1,0,6,0,3, 0,0,0,0,0,3,0);
        mkb("飞镖","造成2点伤害。施加2层中毒。抽1张牌。",CardType.ATTACK,Rarity.UNCOMMON,null,1,2,0,2,0, 0,0,0,0,2,0,1);
        mkb("回春术","回复8点生命值。抽2张牌。",CardType.SKILL,Rarity.UNCOMMON,null,2,0,0,0,0, 0,0,0,0,0,8,2);
        mkb("狂暴","获得3点力量。失去2点生命值。消耗。",CardType.POWER,Rarity.RARE,null,1,0,0,0,0, 3,0,0,0,0,0,0);
        mkb("天罡阵","获得12点格挡。施加1层虚弱。",CardType.DEFENSE,Rarity.RARE,null,2,0,12,0,4, 0,0,0,1,0,0,0);

        // ====== 孙悟空扩展 ======
        mkb("定海神针","造成12点伤害。获得2点力量。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,2,12,0,4,0, 2,0,0,0,0,0,0);
        mkb("身外身","获得4点力量。抽2张牌。消耗。",CardType.POWER,Rarity.RARE,CharacterClass.SUN_WUKONG,1,0,0,0,0, 4,0,0,0,0,0,2);

        // ====== 猪八戒扩展 ======
        mkb("三十六变","获得10点格挡。回复5点生命值。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.ZHU_BAJIE,2,0,10,0,3, 0,0,0,0,0,5,0);
        mkb("净坛使者","获得2点力量。获得2点敏捷。",CardType.POWER,Rarity.RARE,CharacterClass.ZHU_BAJIE,2,0,0,0,0, 2,2,0,0,0,0,0);

        // ====== 沙僧扩展 ======
        mkb("降魔阵","造成6点伤害。获得6点格挡。施加1层易伤。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SHA_SENG,1,6,6,3,2, 0,0,1,0,0,0,0);
        mkb("琉璃盏","回复8点生命值。获得8点格挡。消耗。",CardType.SKILL,Rarity.RARE,CharacterClass.SHA_SENG,1,0,8,0,3, 0,0,0,0,0,8,0);

        // ====== 白龙马扩展 ======
        mkb("龙息","造成6点伤害。施加1层虚弱。抽1张牌。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,1,6,0,3,0, 0,0,0,1,0,0,1);
        mkb("水遁","获得9点格挡。抽1张牌。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,1,0,9,0,3, 0,0,0,0,0,0,1);

        // ====== 唐三藏扩展 ======
        mkb("九环锡杖","造成8点伤害。回复4点生命值。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,8,0,3,0, 0,0,0,0,0,4,0);
        mkb("袈裟护体","获得10点格挡。回复5点生命值。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,2,0,10,0,3, 0,0,0,0,0,5,0);
        mkb("超度","回复12点生命值。获得3点格挡。抽1张牌。",CardType.SKILL,Rarity.RARE,CharacterClass.TANG_SANZANG,2,0,3,0,2, 0,0,0,0,0,12,1);
        mkb("金身护体","获得2点敏捷。每回合开始时获得4点格挡。",CardType.POWER,Rarity.RARE,CharacterClass.TANG_SANZANG,2,0,0,0,0, 0,2,0,0,0,0,0);

        // ====== 第二批扩展卡牌（12张） ======
        // 通用
        mkb("怒目金刚","造成9点伤害。施加1层虚弱。",CardType.ATTACK,Rarity.UNCOMMON,null,1,9,0,4,0, 0,0,0,1,0,0,0);
        mkb("金刚经","获得7点格挡。抽1张牌。",CardType.DEFENSE,Rarity.COMMON,null,1,0,7,0,3, 0,0,0,0,0,0,1);
        mkb("迷魂术","施加2层易伤。抽1张牌。",CardType.SKILL,Rarity.UNCOMMON,null,1,0,0,0,0, 0,0,2,0,0,0,1);
        mkb("破甲击","造成5点伤害。对有格挡的敌人伤害翻倍。",CardType.ATTACK,Rarity.UNCOMMON,null,1,5,0,3,0, 0,0,0,0,0,0,0);
        // 孙悟空
        mkb("法天象地","造成18点伤害。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.SUN_WUKONG,3,18,0,6,0, 0,0,0,0,0,0,0);
        mkb("哮天犬","造成4点伤害。抽2张牌。",CardType.ATTACK,Rarity.COMMON,CharacterClass.SUN_WUKONG,1,4,0,2,0, 0,0,0,0,0,0,2);
        // 猪八戒
        mkb("倒打一耙","造成8点伤害。回复3点生命值。",CardType.ATTACK,Rarity.COMMON,CharacterClass.ZHU_BAJIE,1,8,0,3,0, 0,0,0,0,0,3,0);
        mkb("饕餮之口","造成10点伤害。回复等量生命值。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.ZHU_BAJIE,2,10,0,4,0, 0,0,0,0,0,0,0);
        // 沙僧
        mkb("降妖宝杖·真","造成15点伤害。获得5点格挡。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.SHA_SENG,2,15,5,5,2, 0,0,0,0,0,0,0);
        // 白龙马
        mkb("龙战于野","造成12点伤害。获得3点格挡。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,1,12,3,4,2, 0,0,0,0,0,0,0);
        // 唐三藏
        mkb("佛光普照","回复6点生命值。获得5点格挡。施加1层虚弱。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,0,5,0,3, 0,0,0,1,0,6,0);
        mkb("紧箍咒·禁","造成3点伤害。施加2层虚弱和1层易伤。",CardType.ATTACK,Rarity.RARE,CharacterClass.TANG_SANZANG,1,3,0,2,0, 0,0,1,2,0,0,0);

        // ====== 第三批扩展卡牌（12张） ======
        // 通用
        mkb("烈焰掌","造成8点伤害。",CardType.ATTACK,Rarity.COMMON,null,1,8,0,4,0, 0,0,0,0,0,0,0);
        mkb("铜皮铁骨","获得9点格挡。回复2点生命值。",CardType.DEFENSE,Rarity.COMMON,null,1,0,9,0,3, 0,0,0,0,0,2,0);
        mkb("毒雾弥漫","施加3层中毒。抽1张牌。",CardType.SKILL,Rarity.UNCOMMON,null,1,0,0,0,0, 0,0,0,0,3,0,1);
        // 蓄势待发：drawNextTurn 在 syncCardNextTurnEffects 中补齐
        mkb("蓄势待发","获得1点力量。下回合多抽2张牌。",CardType.POWER,Rarity.RARE,null,1,0,0,0,0, 1,0,0,0,0,0,0);
        // 孙悟空
        mkb("筋斗云翻","获得6点格挡。抽1张牌。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.SUN_WUKONG,1,0,6,0,3, 0,0,0,0,0,0,1);
        mkb("斗战胜佛","获得3点力量。获得3点敏捷。消耗。",CardType.POWER,Rarity.RARE,CharacterClass.SUN_WUKONG,3,0,0,0,0, 3,3,0,0,0,0,0);
        // 猪八戒
        mkb("九齿横扫","造成6点伤害。获得4点格挡。",CardType.ATTACK,Rarity.COMMON,CharacterClass.ZHU_BAJIE,1,6,4,3,2, 0,0,0,0,0,0,0);
        mkb("天蓬元帅","获得4点力量。消耗。",CardType.POWER,Rarity.RARE,CharacterClass.ZHU_BAJIE,2,0,0,0,0, 4,0,0,0,0,0,0);
        // 沙僧
        mkb("降妖钵盂","施加2层虚弱。回复5点生命值。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.SHA_SENG,1,0,0,0,0, 0,0,0,2,0,5,0);
        mkb("金身罗汉","获得3点敏捷。获得2点力量。",CardType.POWER,Rarity.RARE,CharacterClass.SHA_SENG,2,0,0,0,0, 2,3,0,0,0,0,0);
        // 白龙马
        mkb("龙卷风暴","造成10点伤害。施加1层易伤。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,2,10,0,4,0, 0,0,1,0,0,0,0);
        // 唐三藏
        mkb("般若波罗蜜","回复15点生命值。获得5点格挡。抽2张牌。消耗。",CardType.SKILL,Rarity.RARE,CharacterClass.TANG_SANZANG,2,0,5,0,3, 0,0,0,0,0,15,2);
    }

    /** 补齐卡牌的"下回合抽牌/能量"字段（用于已有数据库升级） */
    private void syncCardNextTurnEffects() {
        List<Card> jdcList = cr.findByName("筋斗云");
        if (!jdcList.isEmpty() && jdcList.get(0).getDrawNextTurn() == 0) {
            Card jdc = jdcList.get(0);
            jdc.setDrawNextTurn(1);
            cr.save(jdc);
        }
        List<Card> tyjmList = cr.findByName("腾云驾雾");
        if (!tyjmList.isEmpty() && tyjmList.get(0).getEnergyNextTurn() == 0) {
            Card tyjm = tyjmList.get(0);
            tyjm.setEnergyNextTurn(1);
            cr.save(tyjm);
        }
        // 蓄势待发：下回合多抽2张牌
        List<Card> xsdfList = cr.findByName("蓄势待发");
        if (!xsdfList.isEmpty() && xsdfList.get(0).getDrawNextTurn() == 0) {
            Card xsdf = xsdfList.get(0);
            xsdf.setDrawNextTurn(2);
            cr.save(xsdf);
        }
        // 龙魂觉醒：下回合多抽2张牌
        List<Card> lhxjList = cr.findByName("龙魂觉醒");
        if (!lhxjList.isEmpty() && lhxjList.get(0).getDrawNextTurn() == 0) {
            Card lhxj = lhxjList.get(0);
            lhxj.setDrawNextTurn(2);
            cr.save(lhxj);
        }
        // 筋斗云·翔：下回合+1能量
        List<Card> jdysList = cr.findByName("筋斗云·翔");
        if (!jdysList.isEmpty() && jdysList.get(0).getEnergyNextTurn() == 0) {
            Card jdys = jdysList.get(0);
            jdys.setEnergyNextTurn(1);
            cr.save(jdys);
        }
        // 斗战之心：下回合多抽1张牌
        List<Card> dzxzList = cr.findByName("斗战之心");
        if (!dzxzList.isEmpty() && dzxzList.get(0).getDrawNextTurn() == 0) {
            Card dzxz = dzxzList.get(0);
            dzxz.setDrawNextTurn(1);
            cr.save(dzxz);
        }
        // 禅心定意：下回合多抽1张牌
        List<Card> cxdyList = cr.findByName("禅心定意");
        if (!cxdyList.isEmpty() && cxdyList.get(0).getDrawNextTurn() == 0) {
            Card cxdy = cxdyList.get(0);
            cxdy.setDrawNextTurn(1);
            cr.save(cxdy);
        }
        // 补齐消耗标记（描述含"消耗"但 exhaust=false 的卡牌）
        for (Card c : cr.findAll()) {
            if (!c.isExhaust() && c.getDescription() != null && c.getDescription().contains("消耗")) {
                c.setExhaust(true);
                cr.save(c);
            }
        }
    }

    // -------- helpers --------

    private Card mk(String name, String desc, CardType type, Rarity rarity, CharacterClass cc,
                    int cost, int dmg, int blk, int dmgU, int blkU) {
        Card c = new Card(name, desc, type, rarity, cc, cost);
        c.setDamage(dmg);
        c.setBlock(blk);
        c.setDamageUpgrade(dmgU);
        c.setBlockUpgrade(blkU);
        return cr.save(c);
    }

    /** 带buff效果的卡牌创建方法 */
    private Card mkb(String name, String desc, CardType type, Rarity rarity, CharacterClass cc,
                     int cost, int dmg, int blk, int dmgU, int blkU,
                     int strBonus, int dexBonus, int vuln, int weak, int poison, int heal, int draw) {
        Card c = new Card(name, desc, type, rarity, cc, cost);
        c.setDamage(dmg);
        c.setBlock(blk);
        c.setDamageUpgrade(dmgU);
        c.setBlockUpgrade(blkU);
        c.setStrengthBonus(strBonus);
        c.setDexterityBonus(dexBonus);
        c.setVulnerableTurns(vuln);
        c.setWeakTurns(weak);
        c.setPoisonAmount(poison);
        c.setHealAmount(heal);
        c.setDrawCards(draw);
        return cr.save(c);
    }

    private void me(String name, String desc, int hp, int atk, int def,
                    boolean boss, int level, String... moves) {
        Enemy e = new Enemy(name, hp, atk, def, boss, level);
        e.setDescription(desc);
        e.setMovePattern(Arrays.asList(moves));
        er.save(e);
    }

    private void sc(CharacterClass cc, int hp, String relic) {
        GameCharacter g = new GameCharacter();
        g.setCharacterClass(cc);
        g.setMaxHp(hp);
        g.setStartingGold(100);
        g.setStartingRelic(relic);
        g.setDescription(cc.getDescription());
        g.setStartingDeck("1,1,1,1,1,2,2,2,2,2");
        chr.save(g);
    }

    private void rl(String name, String desc, RelicTier tier, CharacterClass cc) {
        Relic r = new Relic(name, desc, tier, "R");
        r.setCharacterClass(cc);
        rr.save(r);
    }

    // -------- data --------

    private void initCards() {
        mk("挥棒","造成6点伤害。",CardType.ATTACK,Rarity.BASIC,null,1,6,0,3,0);
        mk("格挡","获得5点格挡。",CardType.DEFENSE,Rarity.BASIC,null,1,0,5,0,3);
        mkb("蓄力","获得2点力量。",CardType.SKILL,Rarity.BASIC,null,1,0,0,0,0, 2,0,0,0,0,0,0);
        mk("闪避","获得6点格挡。",CardType.DEFENSE,Rarity.BASIC,null,1,0,6,0,3);
        mk("金箍棒法","造成8点伤害。对易伤目标伤害翻倍。",CardType.ATTACK,Rarity.COMMON,CharacterClass.SUN_WUKONG,1,8,0,4,0);
        mkb("七十二变","施加2层虚弱。抽1张牌。",CardType.SKILL,Rarity.COMMON,CharacterClass.SUN_WUKONG,1,0,0,0,0, 0,0,0,2,0,0,1);
        mk("筋斗云","获得10点格挡。下回合多抽1张牌。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.SUN_WUKONG,2,0,10,0,3);
        mkb("火眼金睛","造成3点伤害。施加1层易伤。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,2,3,0,2,0, 0,0,1,0,0,0,0);
        mk("大闹天宫","造成15点伤害。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.SUN_WUKONG,2,15,0,5,0);
        mkb("毫毛分身","获得3点力量。抽3张牌。消耗。",CardType.POWER,Rarity.RARE,CharacterClass.SUN_WUKONG,2,0,0,0,0, 3,0,0,0,0,0,3);
        mk("九齿钉耙","造成5点伤害。获得3点格挡。",CardType.ATTACK,Rarity.COMMON,CharacterClass.ZHU_BAJIE,1,5,3,2,2);
        mkb("狼吞虎咽","回复5点生命值。消耗。",CardType.SKILL,Rarity.COMMON,CharacterClass.ZHU_BAJIE,1,0,0,0,0, 0,0,0,0,0,5,0);
        mkb("厚皮","获得8点格挡。回复3点生命值。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.ZHU_BAJIE,2,0,8,0,2, 0,0,0,0,0,3,0);
        mk("天河水军","造成15点伤害。生命低于一半时获得5点格挡。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.ZHU_BAJIE,2,15,0,5,0);
        mkb("贪食","回复8点生命值。获得2点力量。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.ZHU_BAJIE,1,0,0,0,0, 2,0,0,0,0,8,0);
        mk("天蓬之怒","造成25点伤害。",CardType.ATTACK,Rarity.RARE,CharacterClass.ZHU_BAJIE,3,25,0,8,0);
        mk("降妖宝杖","造成4点伤害。获得4点格挡。",CardType.ATTACK,Rarity.COMMON,CharacterClass.SHA_SENG,1,4,4,2,2);
        mk("金刚不坏","获得12点格挡。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.SHA_SENG,2,0,12,0,4);
        mkb("流沙河","获得3点敏捷。抽1张牌。",CardType.SKILL,Rarity.COMMON,CharacterClass.SHA_SENG,1,0,0,0,0, 0,3,0,0,0,0,1);
        mk("负重前行","造成10点伤害。获得6点格挡。消耗。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SHA_SENG,2,10,6,3,2);
        mkb("罗汉金身","获得3点敏捷。每回合获得3点格挡。",CardType.POWER,Rarity.UNCOMMON,CharacterClass.SHA_SENG,2,0,0,0,0, 0,3,0,0,0,0,0);
        mk("天河倒灌","造成20点伤害。获得10点格挡。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.SHA_SENG,3,20,10,6,3);
        mkb("龙吟","造成3点伤害。抽2张牌。",CardType.ATTACK,Rarity.COMMON,CharacterClass.BAI_LONGMA,1,3,0,2,0, 0,0,0,0,0,0,2);
        mk("腾云驾雾","获得7点格挡。下回合增加1点能量。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.BAI_LONGMA,1,0,7,0,3);
        mkb("疾风步","抽3张牌。消耗。",CardType.SKILL,Rarity.COMMON,CharacterClass.BAI_LONGMA,0,0,0,0,0, 0,0,0,0,0,0,3);
        mk("龙爪","造成7点伤害。使用过技能则12点。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,1,7,0,4,0);
        mkb("呼风唤雨","抽4张牌。消耗。",CardType.SKILL,Rarity.RARE,CharacterClass.BAI_LONGMA,1,0,0,0,0, 0,0,0,0,0,0,4);
        mkb("龙威","造成14点伤害。抽3张牌。",CardType.ATTACK,Rarity.RARE,CharacterClass.BAI_LONGMA,2,14,0,6,0, 0,0,0,0,0,0,3);
        // ====== 唐三藏专属卡牌（治疗/防御/辅助型） ======
        mkb("紧箍咒念","造成5点伤害。施加1层虚弱。",CardType.ATTACK,Rarity.COMMON,CharacterClass.TANG_SANZANG,1,5,0,3,0, 0,0,0,1,0,0,0);
        mkb("大乘佛法","回复6点生命值。获得3点格挡。",CardType.SKILL,Rarity.COMMON,CharacterClass.TANG_SANZANG,1,0,3,0,2, 0,0,0,0,0,6,0);
        mkb("金蝉脱壳","获得8点格挡。抽1张牌。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.TANG_SANZANG,1,0,8,0,3, 0,0,0,0,0,0,1);
        mkb("普渡众生","回复10点生命值。获得5点格挡。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,2,0,5,0,3, 0,0,0,0,0,10,0);
        mkb("心经","获得2点敏捷。下回合多抽1张牌。",CardType.POWER,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,0,0,0,0, 0,2,0,0,0,0,0);
        mkb("取经誓愿","造成14点伤害。回复5点生命值。",CardType.ATTACK,Rarity.RARE,CharacterClass.TANG_SANZANG,2,14,0,5,0, 0,0,0,0,0,5,0);
        mk("重击","造成10点伤害。",CardType.ATTACK,Rarity.COMMON,null,2,10,0,4,0);
        mk("铁壁","获得8点格挡。",CardType.DEFENSE,Rarity.COMMON,null,2,0,8,0,4);
        mkb("突刺","造成7点伤害。抽1张牌。",CardType.ATTACK,Rarity.UNCOMMON,null,1,7,0,3,0, 0,0,0,0,0,0,1);
        mkb("冥想","获得2点敏捷。回复3点生命值。",CardType.SKILL,Rarity.UNCOMMON,null,1,0,0,0,0, 0,2,0,0,0,3,0);
        mk("金钟罩","获得15点格挡。消耗。",CardType.DEFENSE,Rarity.RARE,null,2,0,15,0,5);
        mkb("天雷破","造成20点伤害。施加1层易伤。",CardType.ATTACK,Rarity.RARE,null,3,20,0,5,0, 0,0,1,0,0,0,0);
        mk("致命一击","造成5点伤害。对易伤目标造成三倍伤害。",CardType.ATTACK,Rarity.RARE,null,2,5,0,5,0);
        mkb("仙丹","回复10点生命值。获得2点力量。消耗。",CardType.SKILL,Rarity.RARE,null,1,0,0,0,0, 2,0,0,0,0,10,0);
        mkb("毒镖","造成3点伤害。施加3层中毒。",CardType.ATTACK,Rarity.COMMON,null,1,3,0,2,0, 0,0,0,0,3,0,0);
        mk("横扫千军","造成8点伤害。攻击所有敌人。",CardType.ATTACK,Rarity.UNCOMMON,null,2,8,0,3,0);

        // 扩展卡牌
        addExtraCards();
        // 第四批扩展卡牌
        addExpansionCards();
        // 第五批扩展卡牌
        addFifthBatchCards();
        // 第六批扩展卡牌（冲刺200张）
        addSixthBatchCards();
    }

    private void initEnemies() {
        me("小妖","山间出没的普通小妖怪。",30,5,0,false,1,"attack","attack","defend");
        me("妖兵","妖王麾下的精兵，训练有素。",40,7,2,false,1,"attack","attack_defend","attack");
        me("白骨精","善于变化的白骨夫人，诡计多端。",55,8,5,false,2,"attack","attack","buff","attack");
        me("蜘蛛精","盘丝洞里的蜘蛛精，擅长缠斗。",45,6,3,false,2,"attack","defend","attack");
        me("红孩儿","牛魔王之子，善使三昧真火。",55,12,2,false,2,"attack","attack","attack","buff");
        me("黄风怪","黄风岭上的妖怪，卷起漫天黄沙。",60,9,6,false,2,"attack_defend","attack","attack_defend");
        me("银角大王","莲花洞的二大王，与金角大王为兄弟。",65,10,5,false,2,"attack","attack","buff","attack");
        me("牛魔王","平天大圣，力大无穷，威震八方。",90,12,8,true,1,"attack","attack_defend","attack","buff");
        me("铁扇公主","牛魔王之妻，手持芭蕉宝扇。",75,7,10,true,1,"attack","defend","attack","buff");
        me("金角大王","莲花洞妖王，有紫金红葫芦等法宝。",85,11,5,true,1,"attack","attack","attack_defend","buff");
        me("黄袍怪","宝象国附近的妖王，法力高深。",80,12,6,true,1,"attack","attack_defend","attack");
        me("灵感大王","通天河里的妖怪，兴风作浪。",70,8,7,true,1,"attack","buff","attack","attack_defend");
    }

    /** 增量迁移：按西游妖怪榜扩展更多敌人 */
    private void migrateExtraEnemies() {
        // 检查是否已迁移（以"黑熊精"为标志）
        if (!er.findByName("黑熊精").isEmpty()) {
            return;
        }
        log.info("开始迁移：按西游妖怪榜添加扩展敌人...");

        // ====== 小怪级（level=1，送经验的初阶小妖） ======
        me("寅将军","双叉岭的虎妖，与熊山君、特处士结拜，初出长安第一难。",28,5,0,false,1,"attack","attack","defend");
        me("熊山君","双叉岭的熊妖，寅将军的结拜兄弟。",32,5,1,false,1,"attack","defend","attack");
        me("特处士","双叉岭的牛妖，寅将军的结拜兄弟。",30,4,1,false,1,"defend","attack","attack");
        me("白衣秀士","黑风山的白花蛇怪，黑熊精的道友。",30,5,1,false,1,"attack","attack","defend");
        me("凌虚子","黑风山的苍狼怪，黑熊精的道友。",32,6,1,false,1,"attack","attack","attack_defend");
        me("精细鬼","平顶山莲花洞的小妖，智商感人，负责看管紫金红葫芦。",20,3,0,false,1,"attack","defend","attack");
        me("伶俐虫","平顶山莲花洞的小妖，与精细鬼搭档，智商同样感人。",20,3,0,false,1,"defend","attack","attack");
        me("巴山虎","金角银角派去请老母亲的小妖，惨遭悟空顶替。",25,4,1,false,1,"attack","attack","defend");
        me("倚海龙","金角银角派去请老母亲的小妖，与巴山虎同行。",25,4,1,false,1,"attack","defend","attack");
        me("压龙大仙","金角银角的老母亲，老狐狸精，虽有法宝幌金绳，但自身战力为零。",35,3,2,false,1,"buff","defend","attack");
        me("六健将","红孩儿的六名健将：云里雾、雾里云、急如火、快如风、兴烘掀、掀烘兴，名号响亮实则战力平平。",30,5,1,false,1,"attack","attack","attack_defend");
        me("奔波儿灞","碧波潭万圣龙王的鲇鱼精，巡塔被抓，贡献了本剧最萌名字。",28,4,1,false,1,"attack","defend","attack");
        me("灞波儿奔","碧波潭万圣龙王的黑鱼精，与奔波儿灞搭档巡塔。",28,4,1,false,1,"defend","attack","attack");
        me("小钻风","狮驼岭年度最佳员工，'大王叫我来巡山'的原唱，被悟空顶替。",25,4,0,false,1,"attack","attack","defend");
        me("刁钻古怪","玉华州豹头山黄狮精手下，负责去集市买猪羊，堪称妖怪界的生活委员。",25,4,0,false,1,"attack","defend","attack");
        me("古怪刁钻","玉华州豹头山黄狮精手下，与刁钻古怪搭档买猪羊。",25,4,0,false,1,"defend","attack","attack");
        me("有来有去","朱紫国獬豸洞赛太岁手下传令兵，爱碎碎念的小妖，透露了紫金铃的情报。",28,4,1,false,1,"attack","attack","defend");
        me("斑衣鳜婆","通天河灵感大王的军师，献计吃唐僧肉，计谋点满，战斗力为零。",30,3,2,false,1,"buff","defend","attack");
        me("玉面公主","积雷山摩云洞牛魔王的小妾，继承了百万家财，却经不起猪八戒一钯。",35,5,2,false,1,"attack","defend","buff");
        me("白面狐狸","比丘国美后，和鹿精国丈配合祸国，被八戒一钯筑死。",35,5,2,false,1,"attack","buff","defend");
        me("杏仙","荆棘岭木仙庵的树精，风雅有余战力全无，擅长吟诗作对。",30,4,1,false,1,"buff","attack","defend");
        me("虫妖干儿子","盘丝洞蜘蛛精的义子：蜜、蚂、蜍、蝉等七个虫妖，被悟空毫毛变的鹰团灭。",20,3,0,false,1,"attack","attack","defend");

        // ====== 精英怪级（level=2，头目与强敌） ======
        me("虎先锋","黄风岭的头目，会用金蝉脱壳计抓走唐僧，与悟空正面能走二三十合。",50,8,3,false,2,"attack","attack","attack_defend","defend");
        me("狐阿七大王","金角银角的舅舅，率兵叫阵，能从悟空棒下走几回合，后被八戒偷袭打死。",55,9,3,false,2,"attack","attack_defend","attack","buff");
        me("如意真仙","牛魔王的兄弟，霸占落胎泉，会牵制拉扯，有特殊机制的守门精英。",50,7,4,false,2,"attack","defend","attack","buff");
        me("黄狮精","九灵元圣的孙子，做事讲究规矩，买猪羊都付钱，能独斗悟空八戒沙僧数合。",60,10,4,false,2,"attack","attack_defend","attack","buff");
        me("七狮","九灵元圣麾下的狮子军团：猱、雪、狻猊、白泽等，单个不如黄狮，但擅长群战布阵。",55,9,3,false,2,"attack","attack","attack_defend","buff");
        me("铁背苍狼怪","隐雾山折岳连环洞南山大王的军师，'分瓣梅花计'极有智谋，罕见的策略型妖怪。",50,7,4,false,2,"buff","attack","defend","attack_defend");

        // ====== Boss级（关底妖王） ======
        me("黑熊精","黑风山妖王，初出茅庐即遇强敌，武力与悟空持平，需观音菩萨用禁箍咒降服。",80,11,6,true,1,"attack","attack_defend","attack","buff");
        me("鼍龙","黑水河泾河龙王的儿子，战力尚可，但后台不硬，被西海太子轻松收服。",65,9,5,true,1,"attack","attack","attack_defend");
        me("虎力大仙","车迟国三大仙之一，机制Boss，擅长赌斗法术：求雨、坐禅、砍头。",60,7,4,true,2,"buff","attack","defend","attack");
        me("鹿力大仙","车迟国三大仙之一，与虎力羊力配合，擅长赌斗法术。",60,7,4,true,2,"buff","defend","attack","attack");
        me("羊力大仙","车迟国三大仙之一，擅长赌斗法术，武力是短板。",60,7,4,true,2,"defend","buff","attack","attack");
        me("独角兕大王","金兜洞青牛精，顶级法宝Boss，金刚琢能收一切兵器，让满天神佛束手无策。",95,13,8,true,3,"attack","attack_defend","attack","buff");
        me("蝎子精","琵琶洞妖女，近战极强，倒马毒桩连如来佛祖都吃过亏，唯昴日星官可破。",85,14,5,true,2,"attack","attack","attack","buff");
        me("六耳猕猴","真假美猴王，镜像Boss，与悟空战力、外观完全相同，只能由如来佛祖鉴别。",100,14,6,true,3,"attack","attack_defend","attack","buff");
        me("九头虫","碧波潭九头驸马，造型极有压迫感，能于二郎神和悟空联手下逃生，血厚防高。",90,12,6,true,2,"attack","attack","attack_defend","buff");
        me("黄眉大王","小雷音寺妖王，最绝望的Boss之一，人种袋能大范围捕获，非弥勒佛亲至不可解。",95,13,7,true,3,"attack","buff","attack","attack_defend");
        me("赛太岁","朱紫国獬豸洞金毛犼，拥有范围杀伤法宝紫金铃，威力极大，被观音菩萨收回。",85,12,6,true,2,"buff","attack","attack_defend","attack");
        me("百眼魔君","黄花观蜈蚣精，大招是肋下千眼金光，造成全屏致盲与伤害，唯毗蓝婆可破解。",85,12,5,true,2,"buff","attack","attack","buff");
        me("青狮","狮驼岭三魔王之首，文殊菩萨坐骑青毛狮子，曾一口吞下十万天兵。",90,12,6,true,3,"attack","attack","attack_defend","buff");
        me("白象","狮驼岭三魔王之二，普贤菩萨坐骑白象，六牙四足，鼻一卷便将人擒住。",90,12,7,true,3,"attack","attack_defend","attack","defend");
        me("大鹏","狮驼岭三魔王之三，如来亲舅舅，隐藏终极Boss，实力与背景双双封顶。",100,14,6,true,3,"attack","attack","attack","buff");
        me("白鹿精","比丘国国丈，寿星坐骑，与狐狸精配合迷惑国王取小儿心肝，后由寿星收回。",75,10,5,true,2,"buff","attack","defend","attack");
        me("金鼻白毛老鼠精","无底洞妖女，纠缠能力很强，有托塔天王和哪吒当义父兄，机制麻烦。",70,10,4,true,2,"attack","defend","attack","buff");
        me("南山大王","隐雾山折岳连环洞豹子精，最弱的Boss之一，纯靠军师计谋撑场面，终为猪八戒所杀。",65,9,4,true,1,"buff","attack","defend","attack");
        me("玉兔精","天竺国假公主，太阴星君的玉兔，捣药杵为武器，近战尚可，很快被带回。",70,10,4,true,2,"attack","attack","buff","defend");
        me("辟寒大王","金平府三只犀牛精之一，擅长联手，畏惧星辰之力，被四木禽星克制击杀。",80,11,5,true,2,"attack","attack_defend","attack","defend");
        me("辟暑大王","金平府三只犀牛精之一，与辟寒辟尘联手，擅长联手作战。",80,11,5,true,2,"attack_defend","attack","attack","defend");
        me("辟尘大王","金平府三只犀牛精之一，与辟寒辟暑联手，畏惧星辰之力。",80,11,5,true,2,"defend","attack","attack_defend","attack");
        me("九灵元圣","玉华州竹节山九头狮子，顶级'吼叫'机制Boss，不靠法宝，一声吼便轻松拿下师徒。",100,13,7,true,3,"buff","attack","attack","attack_defend");

        log.info("扩展敌人迁移完成，新增51个妖怪（小怪22+精英6+Boss23）");
    }

    private void initCharacters() {
        sc(CharacterClass.SUN_WUKONG, 75, "紧箍咒");
        sc(CharacterClass.ZHU_BAJIE, 85, "九齿钉耙");
        sc(CharacterClass.SHA_SENG, 90, "降魔宝杖");
        sc(CharacterClass.BAI_LONGMA, 70, "龙鳞甲");
        sc(CharacterClass.TANG_SANZANG, 80, "锦襕袈裟");
    }

    private void initRelics() {
        rl("紧箍咒","战斗开始时获得2点力量。（孙悟空专属）",RelicTier.SPECIAL,CharacterClass.SUN_WUKONG);
        rl("九齿钉耙","每使用一张攻击牌，回复1点生命。（猪八戒专属）",RelicTier.SPECIAL,CharacterClass.ZHU_BAJIE);
        rl("降魔宝杖","获得格挡时，额外获得2点格挡。（沙僧专属）",RelicTier.SPECIAL,CharacterClass.SHA_SENG);
        rl("龙鳞甲","每回合开始时多抽1张牌。（白龙马专属）",RelicTier.SPECIAL,CharacterClass.BAI_LONGMA);
        rl("锦襕袈裟","每回合开始时回复2点生命值。（唐三藏专属）",RelicTier.SPECIAL,CharacterClass.TANG_SANZANG);
        rl("定海神针","战斗开始时获得1点额外能量。",RelicTier.BOSS,null);
        rl("蟠桃","最大生命值增加10点，并恢复全部生命。",RelicTier.RARE,null);
        rl("八卦炉","每打出3张攻击牌，获得1点力量。",RelicTier.UNCOMMON,null);
        rl("紫金铃","每打出5张牌，对随机敌人造成8点伤害。",RelicTier.UNCOMMON,null);
        rl("袈裟","在休息点可以额外回复10点生命值。",RelicTier.COMMON,null);
        rl("通关文牒","在商店购物享受8折优惠。",RelicTier.COMMON,null);
        rl("人参果","每场战斗开始时回复5点生命值。",RelicTier.RARE,null);
        rl("避水珠","受到伤害时，获得1点格挡。",RelicTier.COMMON,null);
        rl("风火轮","每回合额外获得1点能量。",RelicTier.BOSS,null);
        rl("照妖镜","战斗开始时，对敌人施加1层易伤。",RelicTier.COMMON,null);
        rl("甘露瓶","在休息点可以升级一张卡牌。",RelicTier.RARE,null);
        // 扩展宝物
        addExtraRelics();
        // 第二批扩展宝物
        addExpansionRelics();
        // 第三批扩展宝物（冲刺60件）
        addExpansionRelics2();
    }

    /** 增量迁移：扩展更多宝物到已有数据库 */
    private void migrateExtraRelics() {
        // 检查是否已迁移（以"芭蕉扇"为标志）
        for (Relic r : rr.findAll()) {
            if ("芭蕉扇".equals(r.getName())) return;
        }
        log.info("开始迁移：添加扩展宝物...");
        addExtraRelics();
        log.info("扩展宝物迁移完成，新增10个宝物");
    }

    /** 扩展宝物定义（供 initRelics 和 migrateExtraRelics 共用） */
    private void addExtraRelics() {
        // ====== BOSS 级宝物 ======
        rle("芭蕉扇","战斗开始时，对敌人施加2层虚弱。",RelicTier.BOSS,null,"🌀","BATTLE_START;ENEMY_DEBUFF;WEAK:2");
        rle("九转金丹","最大生命值+15。每回合开始时回复2点生命值。",RelicTier.BOSS,null,"💊","MAX_HP:15;TURN_START;HEAL:2");
        rle("七星剑","所有攻击牌的伤害+2。",RelicTier.BOSS,null,"⚔️","ATTACK_BONUS:2");

        // ====== RARE 级宝物 ======
        rle("紫金红葫芦","战斗开始时，对敌人造成10点伤害。",RelicTier.RARE,null,"🏺","BATTLE_START;DAMAGE:10");
        rle("玉净瓶","在休息点额外回复15点生命值，并可以移除一张卡牌。",RelicTier.RARE,null,"🧫","REST_HEAL_BONUS:15;REST_REMOVE_CARD");
        rle("生死簿","最大生命值减少5点，但战斗开始时获得2点力量。",RelicTier.RARE,null,"📕","MAX_HP:-5;BATTLE_START;STRENGTH:2");

        // ====== UNCOMMON 级宝物 ======
        rle("金刚琢","受到伤害时，获得2点格挡。",RelicTier.UNCOMMON,null,"💍","ON_DAMAGE;BLOCK:2");
        rle("炼妖壶","每击杀一个敌人，最大生命值+3。",RelicTier.UNCOMMON,null,"🫖","ON_KILL;MAX_HP:3");
        rle("镇妖塔","每打出4张防御牌，获得1点敏捷。",RelicTier.UNCOMMON,null,"🗼","COMBO_DEFENSE:4;DEXTERITY:1");
        rle("捆仙绳","战斗开始时，对敌人施加1层虚弱和1层易伤。",RelicTier.UNCOMMON,null,"🪢","BATTLE_START;ENEMY_DEBUFF;WEAK:1;VULNERABLE:1");
    }

    /** 增量同步：为51个扩展敌人补充emoji字段（用于无图时的头像回退显示） */
    private void syncEnemyEmojis() {
        // 已迁移标志：检查"九灵元圣"是否已有emoji
        Optional<Enemy> check = er.findByName("九灵元圣").stream().findFirst();
        if (check.isPresent() && check.get().getEmoji() != null && !check.get().getEmoji().isEmpty()) {
            return;
        }
        log.info("开始同步：为扩展敌人补充emoji字段...");

        // 名称 -> emoji 映射表（51个）
        Map<String, String> emojis = new HashMap<>();
        // ====== 小怪级 ======
        emojis.put("寅将军", "🐯");
        emojis.put("熊山君", "🐻");
        emojis.put("特处士", "🐂");
        emojis.put("白衣秀士", "🐍");
        emojis.put("凌虚子", "🐺");
        emojis.put("精细鬼", "👹");
        emojis.put("伶俐虫", "👺");
        emojis.put("巴山虎", "🐅");
        emojis.put("倚海龙", "🐉");
        emojis.put("压龙大仙", "🦊");
        emojis.put("六健将", "👥");
        emojis.put("奔波儿灞", "🐟");
        emojis.put("灞波儿奔", "🐡");
        emojis.put("小钻风", "🪖");
        emojis.put("刁钻古怪", "🦝");
        emojis.put("古怪刁钻", "🦡");
        emojis.put("有来有去", "📯");
        emojis.put("斑衣鳜婆", "🐟");
        emojis.put("玉面公主", "🦊");
        emojis.put("白面狐狸", "🦊");
        emojis.put("杏仙", "🌸");
        emojis.put("虫妖干儿子", "🐝");
        // ====== 精英怪级 ======
        emojis.put("虎先锋", "🐅");
        emojis.put("狐阿七大王", "🦊");
        emojis.put("如意真仙", "🦏");
        emojis.put("黄狮精", "🦁");
        emojis.put("七狮", "🦁");
        emojis.put("铁背苍狼怪", "🐺");
        // ====== Boss级 ======
        emojis.put("黑熊精", "🐻");
        emojis.put("鼍龙", "🐊");
        emojis.put("虎力大仙", "🐯");
        emojis.put("鹿力大仙", "🦌");
        emojis.put("羊力大仙", "🐐");
        emojis.put("独角兕大王", "🐂");
        emojis.put("蝎子精", "🦂");
        emojis.put("六耳猕猴", "🐒");
        emojis.put("九头虫", "🐛");
        emojis.put("黄眉大王", "👺");
        emojis.put("赛太岁", "🐕");
        emojis.put("百眼魔君", "🕷️");
        emojis.put("青狮", "🦁");
        emojis.put("白象", "🐘");
        emojis.put("大鹏", "🦅");
        emojis.put("白鹿精", "🦌");
        emojis.put("金鼻白毛老鼠精", "🐀");
        emojis.put("南山大王", "🐆");
        emojis.put("玉兔精", "🐰");
        emojis.put("辟寒大王", "🦏");
        emojis.put("辟暑大王", "🦏");
        emojis.put("辟尘大王", "🦏");
        emojis.put("九灵元圣", "🦁");

        int updated = 0;
        for (Map.Entry<String, String> entry : emojis.entrySet()) {
            List<Enemy> list = er.findByName(entry.getKey());
            for (Enemy e : list) {
                if (e.getEmoji() == null || e.getEmoji().isEmpty()) {
                    e.setEmoji(entry.getValue());
                    er.save(e);
                    updated++;
                }
            }
        }
        log.info("扩展敌人emoji同步完成，更新{}个敌人", updated);
    }

    /** 带effect字段的宝物创建方法 */
    private void rle(String name, String desc, RelicTier tier, CharacterClass cc, String emoji, String effect) {
        Relic r = new Relic(name, desc, tier, emoji);
        r.setCharacterClass(cc);
        r.setEffect(effect);
        rr.save(r);
    }

    /** 增量迁移：唐朝皇帝8件御赐宝物 */
    private void migrateEmperorRelics() {
        // 检查是否已迁移（以"太宗玉玺"为标志）
        for (Relic r : rr.findAll()) {
            if (GameConstants.RELIC_EMPEROR_JADE_SEAL.equals(r.getName())) return;
        }
        log.info("开始迁移：添加唐朝皇帝8件御赐宝物...");

        // 御赐金钵：战斗开始+30金币
        rle(GameConstants.RELIC_EMPEROR_GOLDEN_BOWL,
            "唐太宗御赐化缘金钵，战斗开始时获得30金币。",
            RelicTier.BOSS, null, "🥣",
            "BATTLE_START;GOLD:30");
        // 紫金钵盂：战斗开始+1能量
        rle(GameConstants.RELIC_EMPEROR_PURPLE_BOWL,
            "御赐紫金钵盂，战斗开始时获得1点额外能量。",
            RelicTier.BOSS, null, "📿",
            "BATTLE_START;ENERGY:1");
        // 大唐通关文牒：每层开始+20生命
        rle(GameConstants.RELIC_EMPEROR_PASSPORT,
            "大唐通关文牒，每进入新一层时回复20点生命值。",
            RelicTier.BOSS, null, "📜",
            "LAYER_START;HEAL:20");
        // 李世民御剑：战斗开始+2力量
        rle(GameConstants.RELIC_EMPEROR_SWORD,
            "唐太宗亲征佩剑，战斗开始时获得2点力量。",
            RelicTier.BOSS, null, "⚔️",
            "BATTLE_START;STRENGTH:2");
        // 玄奘九环锡杖：战斗开始+2敏捷
        rle(GameConstants.RELIC_EMPEROR_STAFF,
            "玄奘法器九环锡杖，战斗开始时获得2点敏捷。",
            RelicTier.BOSS, null, "📿",
            "BATTLE_START;DEXTERITY:2");
        // 御林军虎符：战斗开始+10格挡
        rle(GameConstants.RELIC_EMPEROR_TIGER_TALLY,
            "调遣御林军的兵符，战斗开始时获得10点格挡。",
            RelicTier.BOSS, null, "🐯",
            "BATTLE_START;BLOCK:10");
        // 御赐琉璃盏：每回合多抽1张
        rle(GameConstants.RELIC_EMPEROR_GLASS_CUP,
            "大唐贡品琉璃盏，每回合开始时多抽1张牌。",
            RelicTier.BOSS, null, "🍶",
            "TURN_START;DRAW:1");
        // 太宗玉玺：战斗金币奖励翻倍
        rle(GameConstants.RELIC_EMPEROR_JADE_SEAL,
            "皇权至高象征，每场战斗获得的金币奖励翻倍。",
            RelicTier.BOSS, null, "💎",
            "GOLD_DOUBLE");

        log.info("唐朝皇帝御赐宝物迁移完成，新增8件");
    }

    // ====== 第四批扩展卡牌（20张） ======

    /** 增量迁移：第四批扩展卡牌 */
    private void migrateExpansionCards() {
        if (!cr.findByName("万剑诀").isEmpty()) return;
        log.info("开始迁移：添加第四批扩展卡牌（20张）...");
        addExpansionCards();
        log.info("第四批扩展卡牌迁移完成，新增20张");
    }

    /** 第四批扩展卡牌定义（供 initCards 和 migrateExpansionCards 共用） */
    private void addExpansionCards() {
        // ====== 通用卡牌（6张） ======
        mkb("万剑诀","造成9点伤害。抽1张牌。",CardType.ATTACK,Rarity.UNCOMMON,null,1,9,0,4,0, 0,0,0,0,0,0,1);
        mkb("固本培元","回复6点生命值。获得1点敏捷。",CardType.SKILL,Rarity.UNCOMMON,null,1,0,0,0,0, 0,1,0,0,0,6,0);
        mkb("金刚降魔杵","造成12点伤害。施加1层易伤。",CardType.ATTACK,Rarity.UNCOMMON,null,2,12,0,4,0, 0,0,1,0,0,0,0);
        mkb("四象阵法","获得14点格挡。施加1层虚弱。",CardType.DEFENSE,Rarity.RARE,null,2,0,14,0,5, 0,0,0,1,0,0,0);
        mkb("以退为进","获得5点格挡。获得1点力量。抽1张牌。",CardType.SKILL,Rarity.UNCOMMON,null,1,0,5,0,3, 1,0,0,0,0,0,1);
        mkb("破绽百出","造成4点伤害。施加2层易伤。",CardType.ATTACK,Rarity.COMMON,null,1,4,0,3,0, 0,0,2,0,0,0,0);

        // ====== 孙悟空（3张） ======
        mkb("火眼金睛·真","造成6点伤害。施加2层易伤和1层虚弱。",CardType.ATTACK,Rarity.RARE,CharacterClass.SUN_WUKONG,2,6,0,3,0, 0,0,2,1,0,0,0);
        mkb("如意金箍棒","造成22点伤害。获得1点力量。",CardType.ATTACK,Rarity.RARE,CharacterClass.SUN_WUKONG,3,22,0,6,0, 1,0,0,0,0,0,0);
        mkb("铜头铁臂","获得8点格挡。获得1点力量。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,1,0,8,0,3, 1,0,0,0,0,0,0);

        // ====== 猪八戒（3张） ======
        mkb("三昧真火","造成12点伤害。施加2层中毒。",CardType.ATTACK,Rarity.RARE,CharacterClass.ZHU_BAJIE,2,12,0,4,0, 0,0,0,0,2,0,0);
        mkb("天蓬戍守","获得9点格挡。回复3点生命值。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.ZHU_BAJIE,1,0,9,0,3, 0,0,0,0,0,3,0);
        Card tstd = mkb("吞噬天地","获得2点力量。回复8点生命值。消耗。",CardType.SKILL,Rarity.RARE,CharacterClass.ZHU_BAJIE,2,0,0,0,0, 2,0,0,0,0,8,0);
        tstd.setExhaust(true);
        cr.save(tstd);

        // ====== 沙僧（2张） ======
        mkb("降魔大阵","获得12点格挡。获得1点敏捷。施加1层易伤。",CardType.DEFENSE,Rarity.RARE,CharacterClass.SHA_SENG,2,0,12,0,4, 0,1,1,0,0,0,0);
        mkb("罗汉伏魔","造成10点伤害。获得5点格挡。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SHA_SENG,2,10,5,4,2, 0,0,0,0,0,0,0);

        // ====== 白龙马（3张） ======
        mkb("龙腾九天","造成12点伤害。抽2张牌。",CardType.ATTACK,Rarity.RARE,CharacterClass.BAI_LONGMA,2,12,0,4,0, 0,0,0,0,0,0,2);
        mkb("水龙吟","获得6点格挡。抽2张牌。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,1,0,6,0,3, 0,0,0,0,0,0,2);
        Card lhxj = mkb("龙魂觉醒","获得2点力量。下回合多抽2张牌。",CardType.POWER,Rarity.RARE,CharacterClass.BAI_LONGMA,2,0,0,0,0, 2,0,0,0,0,0,0);
        lhxj.setDrawNextTurn(2);
        cr.save(lhxj);

        // ====== 唐三藏（3张） ======
        mkb("大慈大悲","回复12点生命值。抽1张牌。",CardType.SKILL,Rarity.RARE,CharacterClass.TANG_SANZANG,1,0,0,0,0, 0,0,0,0,0,12,1);
        Card fowb = mkb("佛法无边","获得2点力量。获得2点敏捷。消耗。",CardType.POWER,Rarity.RARE,CharacterClass.TANG_SANZANG,3,0,0,0,0, 2,2,0,0,0,0,0);
        fowb.setExhaust(true);
        cr.save(fowb);
        Card npcS = mkb("涅槃重生","回复20点生命值。获得10点格挡。消耗。",CardType.SKILL,Rarity.LEGENDARY,CharacterClass.TANG_SANZANG,3,0,10,0,3, 0,0,0,0,0,20,0);
        npcS.setExhaust(true);
        cr.save(npcS);
    }

    // ====== 第二批扩展宝物（12件） ======

    /** 增量迁移：第二批扩展宝物 */
    private void migrateExpansionRelics() {
        for (Relic r : rr.findAll()) {
            if ("护身符".equals(r.getName())) return;
        }
        log.info("开始迁移：添加第二批扩展宝物（12件）...");
        addExpansionRelics();
        log.info("第二批扩展宝物迁移完成，新增12件");
    }

    /** 第二批扩展宝物定义（供 initRelics 和 migrateExpansionRelics 共用） */
    private void addExpansionRelics() {
        // ====== COMMON 级宝物（3件） ======
        rle("护身符","战斗开始时获得5点格挡。",RelicTier.COMMON,null,"🛡️","BATTLE_START;BLOCK:5");
        rle("布袋","战斗开始时获得10金币。",RelicTier.COMMON,null,"👜","BATTLE_START;GOLD:10");
        rle("灵芝","最大生命值增加8点。",RelicTier.COMMON,null,"🍄","MAX_HP:8");

        // ====== UNCOMMON 级宝物（3件） ======
        rle("降魔杵","战斗开始时对敌人造成5点伤害。",RelicTier.UNCOMMON,null,"🔨","BATTLE_START;DAMAGE:5");
        rle("聚灵珠","每回合开始时回复1点生命值。",RelicTier.UNCOMMON,null,"🔮","TURN_START;HEAL:1");
        rle("天蚕甲","受到伤害时获得3点格挡。",RelicTier.UNCOMMON,null,"🦋","ON_DAMAGE;BLOCK:3");

        // ====== RARE 级宝物（3件） ======
        rle("混元珠","战斗开始时获得1点力量和1点敏捷。",RelicTier.RARE,null,"⚫","BATTLE_START;STRENGTH:1;DEXTERITY:1");
        rle("风袋","战斗开始时对敌人施加2层易伤。",RelicTier.RARE,null,"🌪️","BATTLE_START;ENEMY_DEBUFF;VULNERABLE:2");
        rle("乾坤镜","战斗开始时对敌人造成8点伤害并施加1层虚弱。",RelicTier.RARE,null,"🪞","BATTLE_START;DAMAGE:8;ENEMY_DEBUFF;WEAK:1");

        // ====== BOSS 级宝物（3件） ======
        rle("乾坤袋","战斗开始时获得2点力量和1点额外能量。",RelicTier.BOSS,null,"🎒","BATTLE_START;STRENGTH:2;ENERGY:1");
        rle("轮回珠","最大生命值+20。每回合开始时回复3点生命值。",RelicTier.BOSS,null,"💠","MAX_HP:20;TURN_START;HEAL:3");
        rle("天罡北斗阵","战斗开始时获得1点力量、1点敏捷和5点格挡。",RelicTier.BOSS,null,"⭐","BATTLE_START;STRENGTH:1;DEXTERITY:1;BLOCK:5");
    }

    // ====== 第五批扩展卡牌（20张） ======

    /** 增量迁移：第五批扩展卡牌 */
    private void migrateFifthBatchCards() {
        if (!cr.findByName("雷霆万钧").isEmpty()) return;
        log.info("开始迁移：添加第五批扩展卡牌（20张）...");
        addFifthBatchCards();
        log.info("第五批扩展卡牌迁移完成，新增20张");
    }

    /** 第五批扩展卡牌定义（供 initCards 和 migrateFifthBatchCards 共用） */
    private void addFifthBatchCards() {
        // ====== 通用卡牌（6张） ======
        Card ltwj = mk("雷霆万钧","造成16点伤害。消耗。",CardType.ATTACK,Rarity.RARE,null,2,16,0,5,0);
        ltwj.setExhaust(true);
        cr.save(ltwj);
        Card hgfx = mkb("回光返照","回复10点生命值。消耗。",CardType.SKILL,Rarity.RARE,null,1,0,0,0,0, 0,0,0,0,0,10,0);
        hgfx.setExhaust(true);
        cr.save(hgfx);
        mkb("破釜沉舟","造成14点伤害。失去2点生命值。",CardType.ATTACK,Rarity.UNCOMMON,null,2,14,0,5,0, 0,0,0,0,0,0,0);
        mkb("守心如一","获得7点格挡。获得1点敏捷。",CardType.DEFENSE,Rarity.COMMON,null,1,0,7,0,3, 0,1,0,0,0,0,0);
        mkb("毒蛇吐信","造成5点伤害。施加2层中毒。",CardType.ATTACK,Rarity.COMMON,null,1,5,0,3,0, 0,0,0,0,2,0,0);
        Card lywcz = mk("两仪微尘阵","获得16点格挡。消耗。",CardType.DEFENSE,Rarity.RARE,null,2,0,16,0,5);
        lywcz.setExhaust(true);
        cr.save(lywcz);

        // ====== 孙悟空（3张） ======
        Card qtds = mkb("齐天大圣","获得3点力量。获得2点敏捷。消耗。",CardType.POWER,Rarity.LEGENDARY,CharacterClass.SUN_WUKONG,3,0,0,0,0, 3,2,0,0,0,0,0);
        qtds.setExhaust(true);
        cr.save(qtds);
        Card jdys = mk("筋斗云·翔","获得6点格挡。下回合增加1点能量。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,1,0,6,0,3);
        jdys.setEnergyNextTurn(1);
        cr.save(jdys);
        mkb("猴王怒吼","造成8点伤害。施加1层虚弱。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,1,8,0,3,0, 0,0,0,1,0,0,0);

        // ====== 猪八戒（3张） ======
        Card tpjx = mkb("天蓬觉醒","获得3点力量。回复15点生命值。消耗。",CardType.POWER,Rarity.LEGENDARY,CharacterClass.ZHU_BAJIE,3,0,0,0,0, 3,0,0,0,0,15,0);
        tpjx.setExhaust(true);
        cr.save(tpjx);
        mkb("吞食天地","造成10点伤害。回复5点生命值。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.ZHU_BAJIE,2,10,0,4,0, 0,0,0,0,0,5,0);
        mkb("猪突猛进","造成7点伤害。获得3点格挡。",CardType.ATTACK,Rarity.COMMON,CharacterClass.ZHU_BAJIE,1,7,3,3,2, 0,0,0,0,0,0,0);

        // ====== 沙僧（2张） ======
        mkb("金刚罗汉阵","获得15点格挡。获得1点敏捷。",CardType.DEFENSE,Rarity.RARE,CharacterClass.SHA_SENG,2,0,15,0,5, 0,1,0,0,0,0,0);
        mkb("沙暴迷眼","施加2层虚弱和2层易伤。抽1张牌。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.SHA_SENG,1,0,0,0,0, 0,0,2,2,0,0,1);

        // ====== 白龙马（3张） ======
        Card lwdl = mkb("龙王降临","获得2点力量。获得2点敏捷。消耗。",CardType.POWER,Rarity.LEGENDARY,CharacterClass.BAI_LONGMA,3,0,0,0,0, 2,2,0,0,0,0,0);
        lwdl.setExhaust(true);
        cr.save(lwdl);
        mkb("乘风破浪","造成9点伤害。抽1张牌。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,1,9,0,4,0, 0,0,0,0,0,0,1);
        mkb("龙鳞护体","获得8点格挡。获得1点敏捷。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.BAI_LONGMA,1,0,8,0,3, 0,1,0,0,0,0,0);

        // ====== 唐三藏（3张） ======
        mkb("西天取经","获得1点力量。获得2点敏捷。",CardType.POWER,Rarity.LEGENDARY,CharacterClass.TANG_SANZANG,2,0,0,0,0, 1,2,0,0,0,0,0);
        Card jzht = mk("金钟护体","获得10点格挡。消耗。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,0,10,0,3);
        jzht.setExhaust(true);
        cr.save(jzht);
        mkb("超度亡魂","造成6点伤害。施加2层虚弱。回复3点生命值。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,6,0,3,0, 0,0,0,2,0,3,0);
    }

    // ====== 第六批扩展卡牌（74张，冲刺200张总量） ======

    /** 增量迁移：第六批扩展卡牌 */
    private void migrateSixthBatchCards() {
        if (!cr.findByName("雷劫").isEmpty()) return;
        log.info("开始迁移：添加第六批扩展卡牌（74张）...");
        addSixthBatchCards();
        log.info("第六批扩展卡牌迁移完成，新增74张");
    }

    /** 第六批扩展卡牌定义（供 initCards 和 migrateSixthBatchCards 共用） */
    private void addSixthBatchCards() {
        addSixthBatchGeneral();
        addSixthBatchSunWukong();
        addSixthBatchZhuBajie();
        addSixthBatchShaSeng();
        addSixthBatchBaiLongma();
        addSixthBatchTangSanzang();
    }

    private void addSixthBatchGeneral() {
        // ====== 通用卡牌（20张） ======
        Card lj = mk("雷劫","造成16点伤害。消耗。",CardType.ATTACK,Rarity.RARE,null,2,16,0,5,0);
        lj.setExhaust(true); cr.save(lj);
        Card np = mkb("涅槃","回复15点生命值。消耗。",CardType.SKILL,Rarity.RARE,null,1,0,0,0,0, 0,0,0,0,0,15,0);
        np.setExhaust(true); cr.save(np);
        mkb("天网恢恢","施加2层易伤和2层虚弱。",CardType.SKILL,Rarity.UNCOMMON,null,1,0,0,0,0, 0,0,2,2,0,0,0);
        mkb("金光咒","获得6点格挡。抽1张牌。",CardType.DEFENSE,Rarity.COMMON,null,1,0,6,0,3, 0,0,0,0,0,0,1);
        mkb("万象归一","获得2点力量。获得1点敏捷。",CardType.POWER,Rarity.RARE,null,2,0,0,0,0, 2,1,0,0,0,0,0);
        mkb("斩妖除魔","造成7点伤害。",CardType.ATTACK,Rarity.COMMON,null,1,7,0,3,0, 0,0,0,0,0,0,0);
        mkb("灵光护盾","获得8点格挡。",CardType.DEFENSE,Rarity.COMMON,null,1,0,8,0,3, 0,0,0,0,0,0,0);
        mkb("蚀骨毒雾","施加4层中毒。",CardType.SKILL,Rarity.UNCOMMON,null,1,0,0,0,0, 0,0,0,0,4,0,0);
        mkb("天道轮回","回复12点生命值。抽2张牌。",CardType.SKILL,Rarity.RARE,null,2,0,0,0,0, 0,0,0,0,0,12,2);
        mkb("神兵天降","造成14点伤害。",CardType.ATTACK,Rarity.UNCOMMON,null,2,14,0,5,0, 0,0,0,0,0,0,0);
        mk("金刚不坏·改","获得18点格挡。",CardType.DEFENSE,Rarity.RARE,null,2,0,18,0,6);
        mkb("破魔一击","造成6点伤害。施加1层易伤。",CardType.ATTACK,Rarity.UNCOMMON,null,1,6,0,3,0, 0,0,1,0,0,0,0);
        mkb("避邪符","施加1层虚弱。抽1张牌。",CardType.SKILL,Rarity.COMMON,null,0,0,0,0,0, 0,0,0,1,0,0,1);
        Card dzxy = mkb("斗转星移","获得5点格挡。获得1点力量。获得1点敏捷。消耗。",CardType.SKILL,Rarity.RARE,null,1,0,5,0,3, 1,1,0,0,0,0,0);
        dzxy.setExhaust(true); cr.save(dzxy);
        mkb("噬魂爪","造成5点伤害。施加3层中毒。",CardType.ATTACK,Rarity.UNCOMMON,null,1,5,0,3,0, 0,0,0,0,3,0,0);
        mkb("药石之言","回复5点生命值。抽1张牌。",CardType.SKILL,Rarity.COMMON,null,1,0,0,0,0, 0,0,0,0,0,5,1);
        mkb("铁血战意","获得1点力量。获得5点格挡。",CardType.POWER,Rarity.UNCOMMON,null,1,0,0,0,0, 1,0,0,0,0,0,0);
        Card hyfs = mkb("幻影分身","抽3张牌。消耗。",CardType.SKILL,Rarity.UNCOMMON,null,1,0,0,0,0, 0,0,0,0,0,0,3);
        hyfs.setExhaust(true); cr.save(hyfs);
        mkb("惊雷一击","造成9点伤害。",CardType.ATTACK,Rarity.COMMON,null,1,9,0,4,0, 0,0,0,0,0,0,0);
        mkb("玄铁盾","获得7点格挡。回复2点生命值。",CardType.DEFENSE,Rarity.COMMON,null,1,0,7,0,3, 0,0,0,0,0,2,0);
    }

    private void addSixthBatchSunWukong() {
        // ====== 孙悟空（12张） ======
        mkb("定心真言","获得1点力量。获得1点敏捷。",CardType.POWER,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,1,0,0,0,0, 1,1,0,0,0,0,0);
        mkb("猴毛化兵","抽2张牌。",CardType.SKILL,Rarity.COMMON,CharacterClass.SUN_WUKONG,1,0,0,0,0, 0,0,0,0,0,0,2);
        mkb("如意变化","施加3层虚弱。抽1张牌。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,1,0,0,0,0, 0,0,0,3,0,0,1);
        mkb("火眼金睛·极","造成8点伤害。施加3层易伤。",CardType.ATTACK,Rarity.RARE,CharacterClass.SUN_WUKONG,2,8,0,3,0, 0,0,3,0,0,0,0);
        Card jgbs = mk("金箍棒·碎","造成25点伤害。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.SUN_WUKONG,3,25,0,8,0);
        jgbs.setExhaust(true); cr.save(jgbs);
        mkb("筋斗云·瞬","获得5点格挡。抽2张牌。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,1,0,5,0,3, 0,0,0,0,0,0,2);
        mkb("大圣之威","获得2点力量。抽1张牌。",CardType.POWER,Rarity.RARE,CharacterClass.SUN_WUKONG,2,0,0,0,0, 2,0,0,0,0,0,1);
        Card qseb = mkb("七十二变·幻","施加2层虚弱和2层易伤。抽1张牌。消耗。",CardType.SKILL,Rarity.RARE,CharacterClass.SUN_WUKONG,1,0,0,0,0, 0,0,2,2,0,0,1);
        qseb.setExhaust(true); cr.save(qseb);
        mkb("齐天一击","造成16点伤害。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,2,16,0,5,0, 0,0,0,0,0,0,0);
        mkb("猴王护体","获得6点格挡。获得1点力量。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.SUN_WUKONG,1,0,6,0,3, 1,0,0,0,0,0,0);
        mkb("天宫叛逆","造成8点伤害。抽1张牌。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,1,8,0,3,0, 0,0,0,0,0,0,1);
        Card dzxz = mkb("斗战之心","获得1点力量。下回合多抽1张牌。",CardType.POWER,Rarity.UNCOMMON,CharacterClass.SUN_WUKONG,1,0,0,0,0, 1,0,0,0,0,0,0);
        dzxz.setDrawNextTurn(1); cr.save(dzxz);
    }

    private void addSixthBatchZhuBajie() {
        // ====== 猪八戒（12张） ======
        mkb("天蓬之力","造成6点伤害。获得4点格挡。",CardType.ATTACK,Rarity.COMMON,CharacterClass.ZHU_BAJIE,1,6,4,3,2, 0,0,0,0,0,0,0);
        mkb("猪八戒贪吃","回复6点生命值。",CardType.SKILL,Rarity.COMMON,CharacterClass.ZHU_BAJIE,1,0,0,0,0, 0,0,0,0,0,6,0);
        mkb("厚土之盾","获得7点格挡。回复2点生命值。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.ZHU_BAJIE,1,0,7,0,3, 0,0,0,0,0,2,0);
        mkb("天河怒涛","造成14点伤害。回复3点生命值。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.ZHU_BAJIE,2,14,0,5,0, 0,0,0,0,0,3,0);
        mkb("贪婪之咬","造成5点伤害。回复5点生命值。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.ZHU_BAJIE,1,5,0,3,0, 0,0,0,0,0,5,0);
        mkb("天蓬守护","获得8点格挡。获得1点力量。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.ZHU_BAJIE,1,0,8,0,3, 1,0,0,0,0,0,0);
        Card dkyi = mkb("大快朵颐","回复12点生命值。消耗。",CardType.SKILL,Rarity.RARE,CharacterClass.ZHU_BAJIE,1,0,0,0,0, 0,0,0,0,0,12,0);
        dkyi.setExhaust(true); cr.save(dkyi);
        mkb("铁齿铜牙","获得6点格挡。回复3点生命值。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.ZHU_BAJIE,1,0,6,0,3, 0,0,0,0,0,3,0);
        Card tpyl = mkb("天蓬元帅·临","获得3点力量。消耗。",CardType.POWER,Rarity.RARE,CharacterClass.ZHU_BAJIE,2,0,0,0,0, 3,0,0,0,0,0,0);
        tpyl.setExhaust(true); cr.save(tpyl);
        mkb("九齿狂舞","造成12点伤害。获得4点格挡。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.ZHU_BAJIE,2,12,4,4,2, 0,0,0,0,0,0,0);
        Card tsjq = mkb("吞噬精气","造成10点伤害。回复8点生命值。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.ZHU_BAJIE,2,10,0,4,0, 0,0,0,0,0,0,0);
        tsjq.setExhaust(true); cr.save(tsjq);
        mkb("天河水军·阵","获得10点格挡。回复4点生命值。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.ZHU_BAJIE,2,0,10,0,3, 0,0,0,0,0,4,0);
    }

    private void addSixthBatchShaSeng() {
        // ====== 沙僧（10张） ======
        mkb("降妖之力","造成5点伤害。获得5点格挡。",CardType.ATTACK,Rarity.COMMON,CharacterClass.SHA_SENG,1,5,5,3,2, 0,0,0,0,0,0,0);
        mkb("金刚护体","获得8点格挡。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.SHA_SENG,1,0,8,0,3, 0,0,0,0,0,0,0);
        mkb("罗汉怒目","造成8点伤害。施加1层虚弱。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SHA_SENG,1,8,0,3,0, 0,0,0,1,0,0,0);
        mkb("流沙之盾","获得10点格挡。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.SHA_SENG,1,0,10,0,3, 0,0,0,0,0,0,0);
        mkb("降魔真言","施加2层虚弱。获得5点格挡。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.SHA_SENG,1,0,5,0,3, 0,0,0,2,0,0,0);
        mkb("沙僧护法","获得14点格挡。施加1层虚弱。",CardType.DEFENSE,Rarity.RARE,CharacterClass.SHA_SENG,2,0,14,0,5, 0,0,0,1,0,0,0);
        mkb("罗汉降魔","造成12点伤害。获得5点格挡。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.SHA_SENG,2,12,5,4,2, 0,0,0,0,0,0,0);
        Card jsbm = mk("金身不灭","获得16点格挡。消耗。",CardType.DEFENSE,Rarity.RARE,CharacterClass.SHA_SENG,2,0,16,0,5);
        jsbm.setExhaust(true); cr.save(jsbm);
        mkb("流沙漩涡","施加3层虚弱。抽1张牌。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.SHA_SENG,1,0,0,0,0, 0,0,0,3,0,0,1);
        Card xybz = mkb("降妖宝杖·烈","造成16点伤害。施加1层易伤。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.SHA_SENG,2,16,0,5,0, 0,0,1,0,0,0,0);
        xybz.setExhaust(true); cr.save(xybz);
    }

    private void addSixthBatchBaiLongma() {
        // ====== 白龙马（10张） ======
        mkb("龙之吐息","造成7点伤害。施加1层虚弱。",CardType.ATTACK,Rarity.COMMON,CharacterClass.BAI_LONGMA,1,7,0,3,0, 0,0,0,1,0,0,0);
        mkb("云龙飞天","获得7点格挡。抽1张牌。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.BAI_LONGMA,1,0,7,0,3, 0,0,0,0,0,0,1);
        mkb("龙威震天","造成14点伤害。施加1层易伤。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,2,14,0,5,0, 0,0,1,0,0,0,0);
        Card fcdc = mkb("风驰电掣","抽2张牌。消耗。",CardType.SKILL,Rarity.COMMON,CharacterClass.BAI_LONGMA,0,0,0,0,0, 0,0,0,0,0,0,2);
        fcdc.setExhaust(true); cr.save(fcdc);
        mkb("龙魂附体","获得1点力量。获得1点敏捷。",CardType.POWER,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,1,0,0,0,0, 1,1,0,0,0,0,0);
        mkb("水龙破阵","造成10点伤害。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,1,10,0,4,0, 0,0,0,0,0,0,0);
        mkb("龙鳞壁垒","获得12点格挡。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.BAI_LONGMA,2,0,12,0,4, 0,0,0,0,0,0,0);
        Card xlzn = mk("逆鳞之怒","造成18点伤害。消耗。",CardType.ATTACK,Rarity.RARE,CharacterClass.BAI_LONGMA,2,18,0,6,0);
        xlzn.setExhaust(true); cr.save(xlzn);
        Card yhft = mkb("云海翻腾","抽3张牌。消耗。",CardType.SKILL,Rarity.RARE,CharacterClass.BAI_LONGMA,1,0,0,0,0, 0,0,0,0,0,0,3);
        yhft.setExhaust(true); cr.save(yhft);
        mkb("龙吟九天","造成12点伤害。抽2张牌。",CardType.ATTACK,Rarity.RARE,CharacterClass.BAI_LONGMA,2,12,0,4,0, 0,0,0,0,0,0,2);
    }

    private void addSixthBatchTangSanzang() {
        // ====== 唐三藏（10张） ======
        mkb("佛光普照·真","回复8点生命值。施加1层虚弱。",CardType.SKILL,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,0,0,0,0, 0,0,0,1,0,8,0);
        mkb("袈裟金光","获得7点格挡。回复2点生命值。",CardType.DEFENSE,Rarity.COMMON,CharacterClass.TANG_SANZANG,1,0,7,0,3, 0,0,0,0,0,2,0);
        mkb("诵经祈福","回复7点生命值。",CardType.SKILL,Rarity.COMMON,CharacterClass.TANG_SANZANG,1,0,0,0,0, 0,0,0,0,0,7,0);
        Card cxdy = mkb("禅心定意","获得1点敏捷。下回合多抽1张牌。",CardType.POWER,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,0,0,0,0, 0,1,0,0,0,0,0);
        cxdy.setDrawNextTurn(1); cr.save(cxdy);
        mkb("佛门圣光","造成6点伤害。施加2层虚弱。",CardType.ATTACK,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,6,0,3,0, 0,0,0,2,0,0,0);
        mkb("大乘佛法·圆","回复10点生命值。获得3点格挡。",CardType.SKILL,Rarity.RARE,CharacterClass.TANG_SANZANG,1,0,3,0,2, 0,0,0,0,0,10,0);
        mkb("金蝉护体","获得9点格挡。回复3点生命值。",CardType.DEFENSE,Rarity.UNCOMMON,CharacterClass.TANG_SANZANG,1,0,9,0,3, 0,0,0,0,0,3,0);
        Card cdzs = mkb("超度众生","回复15点生命值。抽1张牌。消耗。",CardType.SKILL,Rarity.RARE,CharacterClass.TANG_SANZANG,2,0,0,0,0, 0,0,0,0,0,15,1);
        cdzs.setExhaust(true); cr.save(cdzs);
        mkb("佛光普照·极","回复6点生命值。获得5点格挡。施加1层虚弱。",CardType.SKILL,Rarity.RARE,CharacterClass.TANG_SANZANG,1,0,5,0,3, 0,0,0,1,0,6,0);
        mkb("紧箍咒·灭","造成10点伤害。施加2层虚弱和2层易伤。",CardType.ATTACK,Rarity.RARE,CharacterClass.TANG_SANZANG,2,10,0,4,0, 0,0,2,2,0,0,0);
    }

    // ====== 第三批扩展宝物（14件，冲刺60件总量） ======

    /** 增量迁移：第三批扩展宝物 */
    private void migrateExpansionRelics2() {
        for (Relic r : rr.findAll()) {
            if ("安神符".equals(r.getName())) return;
        }
        log.info("开始迁移：添加第三批扩展宝物（14件）...");
        addExpansionRelics2();
        log.info("第三批扩展宝物迁移完成，新增14件");
    }

    /** 第三批扩展宝物定义（供 initRelics 和 migrateExpansionRelics2 共用） */
    private void addExpansionRelics2() {
        // ====== COMMON 级宝物（4件） ======
        rle("安神符","战斗开始时获得1点敏捷。",RelicTier.COMMON,null,"🌿","BATTLE_START;DEXTERITY:1");
        rle("驱邪符","战斗开始时对敌人施加1层虚弱。",RelicTier.COMMON,null,"📜","BATTLE_START;ENEMY_DEBUFF;WEAK:1");
        rle("玉佩","最大生命值增加5点。",RelicTier.COMMON,null,"📿","MAX_HP:5");
        rle("布僧鞋","战斗开始时获得5金币。",RelicTier.COMMON,null,"👟","BATTLE_START;GOLD:5");

        // ====== UNCOMMON 级宝物（4件） ======
        rle("降妖符","战斗开始时对敌人造成3点伤害。",RelicTier.UNCOMMON,null,"🔱","BATTLE_START;DAMAGE:3");
        rle("聚气珠","战斗开始时获得1点力量。",RelicTier.UNCOMMON,null,"🔵","BATTLE_START;STRENGTH:1");
        rle("天机镜","战斗开始时对敌人施加1层易伤。",RelicTier.UNCOMMON,null,"🔮","BATTLE_START;ENEMY_DEBUFF;VULNERABLE:1");
        rle("金丝甲","受到伤害时获得1点格挡。",RelicTier.UNCOMMON,null,"✨","ON_DAMAGE;BLOCK:1");

        // ====== RARE 级宝物（3件） ======
        rle("混沌珠","战斗开始时获得2点力量。",RelicTier.RARE,null,"🌑","BATTLE_START;STRENGTH:2");
        rle("九龙杯","每回合开始时回复2点生命值。",RelicTier.RARE,null,"🍶","TURN_START;HEAL:2");
        rle("太极图","战斗开始时获得3点格挡和1点敏捷。",RelicTier.RARE,null,"☯️","BATTLE_START;BLOCK:3;DEXTERITY:1");

        // ====== BOSS 级宝物（3件） ======
        rle("混元鼎","战斗开始时获得2点力量和2点敏捷。",RelicTier.BOSS,null,"🏺","BATTLE_START;STRENGTH:2;DEXTERITY:2");
        rle("天命珠","最大生命值+15。战斗开始时获得1点额外能量。",RelicTier.BOSS,null,"🌟","MAX_HP:15;BATTLE_START;ENERGY:1");
        rle("造化玉碟","战斗开始时获得1点力量、1点敏捷、1点额外能量和5点格挡。",RelicTier.BOSS,null,"💠","BATTLE_START;STRENGTH:1;DEXTERITY:1;ENERGY:1;BLOCK:5");
    }
}
