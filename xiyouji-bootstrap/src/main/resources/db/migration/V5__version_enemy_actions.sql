-- Keep enemy primary keys and all original stats/artwork. Existing rows are upgraded
-- by stable content_key/version under DataInitializer's distributed seed lock.
ALTER TABLE enemies
    ADD COLUMN content_key VARCHAR(120) NULL,
    ADD COLUMN content_version INT NOT NULL DEFAULT 0,
    ADD COLUMN rules_version VARCHAR(32) NULL,
    ADD COLUMN action_definitions TEXT NULL;
CREATE INDEX idx_enemies_content_key ON enemies(content_key);
