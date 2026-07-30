-- ========================================
-- 西游记Roguelike卡牌游戏 - 数据库初始化脚本
-- 适用MySQL 8.0+
-- ========================================

CREATE DATABASE IF NOT EXISTS xiyouji
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE xiyouji;

-- 角色表
CREATE TABLE IF NOT EXISTS characters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    character_class VARCHAR(50) NOT NULL UNIQUE,
    max_hp INT NOT NULL DEFAULT 70,
    starting_gold INT NOT NULL DEFAULT 100,
    starting_deck VARCHAR(1000),
    starting_relic VARCHAR(200),
    description VARCHAR(500),
    emoji VARCHAR(100),
    INDEX idx_character_class (character_class)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 卡牌表
CREATE TABLE IF NOT EXISTS cards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    type VARCHAR(20) NOT NULL,
    rarity VARCHAR(20) NOT NULL,
    character_class VARCHAR(50),
    cost INT DEFAULT 1,
    damage INT DEFAULT 0,
    block INT DEFAULT 0,
    draw_cards INT DEFAULT 0,
    heal_amount INT DEFAULT 0,
    strength_bonus INT DEFAULT 0,
    dexterity_bonus INT DEFAULT 0,
    vulnerable_turns INT DEFAULT 0,
    weak_turns INT DEFAULT 0,
    poison_amount INT DEFAULT 0,
    exhaust BOOLEAN DEFAULT FALSE,
    upgradeable BOOLEAN DEFAULT TRUE,
    upgrade_name VARCHAR(100),
    damage_upgrade INT DEFAULT 0,
    block_upgrade INT DEFAULT 0,
    cost_reduction INT DEFAULT 0,
    flavor_text VARCHAR(100),
    emoji VARCHAR(100),
    INDEX idx_character_class (character_class),
    INDEX idx_type (type),
    INDEX idx_rarity (rarity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 敌人表
CREATE TABLE IF NOT EXISTS enemies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    max_hp INT NOT NULL,
    hp INT NOT NULL,
    attack INT DEFAULT 5,
    defense INT DEFAULT 0,
    is_boss BOOLEAN DEFAULT FALSE,
    level INT DEFAULT 1,
    emoji VARCHAR(100),
    INDEX idx_level (level),
    INDEX idx_is_boss (is_boss)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 遗物表
CREATE TABLE IF NOT EXISTS relics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    tier VARCHAR(20) NOT NULL,
    character_class VARCHAR(50),
    emoji VARCHAR(100),
    effect VARCHAR(500),
    INDEX idx_tier (tier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========================================
-- 种子数据
-- ========================================

-- 角色数据
INSERT INTO characters (character_class, max_hp, starting_gold, starting_deck, starting_relic, description, emoji) VALUES
('SUN_WUKONG', 75, 100, '1,2,3,4,5,6,7,8,9,10', '1', '齐天大圣，擅长攻击与变化之道。金箍棒所向披靡！', '🐵'),
('ZHU_BAJIE', 85, 100, '1,2,3,4,5,6,11,12,13,14', '2', '天蓬元帅，虽贪吃懒惰但生命力顽强，九齿钉耙威力十足。', '🐷'),
('SHA_SENG', 90, 100, '1,2,3,4,5,6,15,16,17,18', '3', '卷帘大将，沉默寡言却最为可靠。金刚之躯坚不可摧！', '🟤'),
('BAI_LONGMA', 70, 100, '1,2,3,4,5,6,19,20,21,22', '4', '西海龙太子，腾云驾雾快如闪电。龙威浩荡！', '🐴');

-- 基础通用卡牌
INSERT INTO cards (name, description, type, rarity, character_class, cost, damage, block, draw_cards, damage_upgrade, block_upgrade, flavor_text, emoji) VALUES
('挥棒', '造成6点伤害。', 'ATTACK', 'BASIC', NULL, 1, 6, 0, 0, 3, 0, '最简单的一击', '⚔️'),
('格挡', '获得5点格挡。', 'DEFENSE', 'BASIC', NULL, 1, 0, 5, 0, 0, 3, '架起防御姿势', '🛡️'),
('蓄力', '获得2点力量。', 'SKILL', 'BASIC', NULL, 1, 0, 0, 0, 0, 0, NULL, '💪'),
-- 后续卡牌会在DataInitializer中动态加载
('闪避', '获得6点格挡。', 'DEFENSE', 'BASIC', NULL, 1, 0, 6, 0, 0, 3, '灵活闪避', '💨'),
('精准打击', '造成4点伤害。施加1层易伤。', 'ATTACK', 'BASIC', NULL, 1, 4, 0, 0, 2, 0, NULL, '🎯'),
('旋风斩', '对所有敌人造成4点伤害。', 'ATTACK', 'BASIC', NULL, 2, 4, 0, 0, 2, 0, NULL, '🌪️');

-- 孙悟空专属卡牌
INSERT INTO cards (name, description, type, rarity, character_class, cost, damage, block, draw_cards, damage_upgrade, flavor_text, emoji) VALUES
('金箍棒法', '造成8点伤害。如果目标有易伤，造成12点伤害。', 'ATTACK', 'COMMON', 'SUN_WUKONG', 1, 8, 0, 0, 4, '如意金箍棒，一万三千五百斤！', '🔱'),
('七十二变', '使敌人虚弱2回合。抽1张牌。', 'SKILL', 'COMMON', 'SUN_WUKONG', 1, 0, 0, 1, 0, '变成苍蝇、变成大树、变成……', '🔄'),
('筋斗云', '获得10点格挡。下回合多抽1张牌。', 'DEFENSE', 'COMMON', 'SUN_WUKONG', 2, 0, 10, 0, 3, '一个跟头就是十万八千里。', '☁️'),
('火眼金睛', '造成3点伤害3次。施加1层易伤。', 'ATTACK', 'UNCOMMON', 'SUN_WUKONG', 2, 3, 0, 0, 2, '在老君炉里炼出来的！', '👁️');

-- 猪八戒专属卡牌
INSERT INTO cards (name, description, type, rarity, character_class, cost, damage, block, draw_cards, damage_upgrade, flavor_text, emoji) VALUES
('九齿钉耙', '造成5点伤害。获得3点格挡。', 'ATTACK', 'COMMON', 'ZHU_BAJIE', 1, 5, 3, 0, 2, '太上老君亲手锻造的神兵！', '🔨'),
('狼吞虎咽', '回复5点生命值。消耗。', 'SKILL', 'COMMON', 'ZHU_BAJIE', 1, 0, 0, 0, 0, '有吃的？哪里？！', '🍗'),
('厚皮', '获得8点格挡。回复3点生命值。', 'DEFENSE', 'COMMON', 'ZHU_BAJIE', 2, 0, 8, 0, 2, '皮糙肉厚，刀枪不入。', '🐽'),
('天河水军', '造成15点伤害。如果生命低于50%，获得5点格挡。', 'ATTACK', 'UNCOMMON', 'ZHU_BAJIE', 2, 15, 0, 0, 5, '俺老猪当年也是天蓬元帅！', '🌊');

-- 沙僧专属卡牌
INSERT INTO cards (name, description, type, rarity, character_class, cost, damage, block, draw_cards, damage_upgrade, block_upgrade, flavor_text, emoji) VALUES
('降妖宝杖', '造成4点伤害。获得4点格挡。', 'ATTACK', 'COMMON', 'SHA_SENG', 1, 4, 4, 0, 2, 2, '月牙铲，降妖除魔。', '🏏'),
('金刚不坏', '获得12点格挡。', 'DEFENSE', 'COMMON', 'SHA_SENG', 2, 0, 12, 0, 0, 4, '我自岿然不动。', '💎'),
('流沙河', '获得3点敏捷。抽1张牌。', 'SKILL', 'COMMON', 'SHA_SENG', 1, 0, 0, 1, 0, 0, '八百流沙界，三千弱水深。', '🏞️'),
('负重前行', '造成10点伤害。获得6点格挡。消耗。', 'ATTACK', 'UNCOMMON', 'SHA_SENG', 2, 10, 6, 0, 3, 2, '大师兄，行李我来背！', '🎒');

-- 白龙马专属卡牌
INSERT INTO cards (name, description, type, rarity, character_class, cost, damage, block, draw_cards, damage_upgrade, flavor_text, emoji) VALUES
('龙吟', '造成3点伤害。抽2张牌。', 'ATTACK', 'COMMON', 'BAI_LONGMA', 1, 3, 0, 2, 2, '龙吟九天，万兽臣服！', '🐉'),
('腾云驾雾', '获得7点格挡。下回合获得额外1点能量。', 'DEFENSE', 'COMMON', 'BAI_LONGMA', 1, 0, 7, 0, 3, '一日千里，不在话下。', '☁️'),
('疾风步', '抽3张牌。弃掉1张。消耗。', 'SKILL', 'COMMON', 'BAI_LONGMA', 0, 0, 0, 3, 0, '快如闪电，疾如旋风', '💨'),
('龙爪', '造成7点伤害。如果本回合使用过技能，造成12点伤害。', 'ATTACK', 'UNCOMMON', 'BAI_LONGMA', 1, 7, 0, 0, 4, '西海龙族，爪裂金石！', '🦞');

-- 通用卡牌（所有角色可用）
INSERT INTO cards (name, description, type, rarity, character_class, cost, damage, block, damage_upgrade, block_upgrade, flavor_text, emoji) VALUES
('重击', '造成10点伤害。', 'ATTACK', 'COMMON', NULL, 2, 10, 0, 4, 0, NULL, '🔨'),
('铁壁', '获得8点格挡。', 'DEFENSE', 'COMMON', NULL, 2, 0, 8, 0, 4, NULL, '🧱'),
('突刺', '造成7点伤害。抽1张牌。', 'ATTACK', 'UNCOMMON', NULL, 1, 7, 0, 3, 0, NULL, '🗡️'),
('冥想', '获得2点敏捷。回复3点生命值。', 'SKILL', 'UNCOMMON', NULL, 1, 0, 0, 0, 0, NULL, '🧘'),
('金钟罩', '获得15点格挡。消耗。', 'DEFENSE', 'RARE', NULL, 2, 0, 15, 0, 5, NULL, '🔔'),
('天雷破', '造成20点伤害。施加1层易伤。', 'ATTACK', 'RARE', NULL, 3, 20, 0, 5, 0, NULL, '⚡'),
('致命一击', '造成5点伤害。如果目标有易伤，造成三倍伤害。', 'ATTACK', 'RARE', NULL, 2, 5, 0, 5, 0, NULL, '💀'),
('仙丹', '回复10点生命值。获得2点力量。消耗。', 'SKILL', 'RARE', NULL, 1, 0, 0, 0, 0, NULL, '✨');

-- 敌人数据
INSERT INTO enemies (name, description, max_hp, hp, attack, defense, is_boss, level, emoji) VALUES
('小妖', '山间出没的小妖怪', 30, 30, 5, 0, FALSE, 1, '👺'),
('妖兵', '有妖王手下的精兵', 40, 40, 7, 2, FALSE, 1, '👹'),
('白骨精', '善于变化的女妖精', 55, 55, 8, 5, FALSE, 2, '💀'),
('蜘蛛精', '盘丝洞里的蜘蛛精', 45, 45, 6, 3, FALSE, 2, '🕷️'),
('红孩儿', '牛魔王之子，善使三昧真火', 50, 50, 10, 2, FALSE, 2, '🔥'),
('牛魔王', '平天大圣，力大无穷', 80, 80, 12, 8, TRUE, 1, '🐂'),
('黄风怪', '黄风岭上的妖怪', 60, 60, 9, 6, FALSE, 2, '🌪️'),
('铁扇公主', '牛魔王之妻，有芭蕉扇', 65, 65, 7, 10, TRUE, 1, '👸'),
('金角大王', '莲花洞的妖王', 70, 70, 11, 5, TRUE, 1, '👑'),
('银角大王', '莲花洞的二大王', 60, 60, 10, 5, FALSE, 2, '👲'),
('黄袍怪', '宝象国附近的妖王', 75, 75, 12, 6, TRUE, 1, '👘'),
('灵感大王', '通天河里的妖怪', 55, 55, 8, 7, FALSE, 2, '🐟');

-- 遗物数据
INSERT INTO relics (name, description, tier, character_class, emoji, effect) VALUES
('紧箍咒', '战斗开始时获得2点力量。(孙悟空专属)', 'SPECIAL', 'SUN_WUKONG', '⭕', 'BATTLE_START;STRENGTH:2'),
('九齿钉耙', '每当你使用攻击牌时，回复1点生命。(猪八戒专属)', 'SPECIAL', 'ZHU_BAJIE', '🔨', 'ON_ATTACK;HEAL:1'),
('降魔宝杖', '每当你获得格挡时，额外获得2点格挡。(沙僧专属)', 'SPECIAL', 'SHA_SENG', '🏏', 'ON_BLOCK;BONUS:2'),
('龙鳞甲', '每回合开始时额外抽1张牌。(白龙马专属)', 'SPECIAL', 'BAI_LONGMA', '🐉', 'TURN_START;DRAW:1'),
('定海神针', '战斗开始时获得1点能量。', 'BOSS', NULL, '📏', 'BATTLE_START;ENERGY:1'),
('蟠桃', '最大生命值+10。恢复全部生命。', 'RARE', NULL, '🍑', 'MAX_HP:10;HEAL_FULL'),
('八卦炉', '每打出3张攻击牌获得1点力量。', 'UNCOMMON', NULL, '♨️', 'COMBO_ATTACK:3;STRENGTH:1'),
('紫金铃', '每打出5张牌，对随机敌人造成8点伤害。', 'UNCOMMON', NULL, '🔔', 'COMBO_CARDS:5;DAMAGE:8'),
('袈裟', '在休息点可以额外回复10点生命值。', 'COMMON', NULL, '👘', 'REST_HEAL_BONUS:10'),
('通关文牒', '在商店购物享受8折优惠。', 'COMMON', NULL, '📜', 'SHOP_DISCOUNT:20'),
('人参果', '每场战斗开始时回复5点生命值。', 'RARE', NULL, '🍒', 'BATTLE_START;HEAL:5'),
('避水珠', '受到伤害时，获得1点格挡。', 'COMMON', NULL, '💧', 'ON_DAMAGE;BLOCK:1'),
('风火轮', '每回合获得额外1点能量。', 'BOSS', NULL, '🔥', 'TURN_START;ENERGY:1'),
('照妖镜', '战斗开始时，对敌人施加1层易伤。', 'COMMON', NULL, '🪞', 'BATTLE_START;ENEMY_DEBUFF;VULNERABLE:1'),
('甘露瓶', '在休息点可以升级一张卡牌，不需要篝火。', 'RARE', NULL, '🧴', 'REST_UPGRADE');
