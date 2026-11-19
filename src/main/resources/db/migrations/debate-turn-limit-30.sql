-- Raises the debate turn limit from 12 to 30 (AI + user turns combined).
-- Apply once to an existing database. Fresh databases get the same CHECKs from schema.sql.
-- Postgres names column-level CHECKs as <table>_<column>_check.
BEGIN;
ALTER TABLE debates DROP CONSTRAINT IF EXISTS debates_turn_count_check;
ALTER TABLE debates ADD CONSTRAINT debates_turn_count_check CHECK (turn_count BETWEEN 0 AND 30);
ALTER TABLE debate_turns DROP CONSTRAINT IF EXISTS debate_turns_turn_order_check;
ALTER TABLE debate_turns ADD CONSTRAINT debate_turns_turn_order_check CHECK (turn_order BETWEEN 1 AND 30);
COMMIT;
