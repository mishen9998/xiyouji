-- ========================================
-- 西游记Roguelike卡牌游戏 - Flyway 初始化迁移脚本
-- 适用 MySQL 8.0+
-- 说明: 使用 CREATE TABLE IF NOT EXISTS, 不会删除已有表
-- ========================================

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

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'PLAYER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
