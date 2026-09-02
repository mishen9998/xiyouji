-- Align the cards table with the persisted Card entity fields.
-- These columns were introduced after the original V1 schema and must be
-- applied before Hibernate's production `ddl-auto=validate` check runs.
ALTER TABLE cards
    ADD COLUMN draw_next_turn INT NOT NULL DEFAULT 0,
    ADD COLUMN energy_next_turn INT NOT NULL DEFAULT 0,
    ADD COLUMN upgraded BOOLEAN NOT NULL DEFAULT FALSE;
