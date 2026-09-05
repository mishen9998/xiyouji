-- 将“登录账号”和“显示用户名”拆分。
-- 旧版本只有 username；升级时用原 username 回填 account，保持旧账号仍可登录。
ALTER TABLE users ADD COLUMN account VARCHAR(50) NULL AFTER id;

UPDATE users
SET account = username
WHERE account IS NULL OR account = '';

ALTER TABLE users MODIFY COLUMN account VARCHAR(50) NOT NULL;
CREATE UNIQUE INDEX uk_users_account ON users (account);
