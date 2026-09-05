-- 9.5 规则文案统一：玩家可见的“易伤”改为“脆弱”。
-- 技术效果字段 VULNERABLE 保持不变，以兼容已有存档和遗物配置。
UPDATE cards
SET description = REPLACE(description, '易伤', '脆弱')
WHERE description LIKE '%易伤%';

UPDATE relics
SET description = REPLACE(description, '易伤', '脆弱')
WHERE description LIKE '%易伤%';
