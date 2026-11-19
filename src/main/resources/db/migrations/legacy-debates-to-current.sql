-- One-time migration from the original six-turn debate schema to the current schema.
-- Run with psql -v ON_ERROR_STOP=1 against the intended database after taking a backup.
-- Original tables and all their columns remain in legacy_debates_20261117.
BEGIN;

DO $$
BEGIN
  IF to_regnamespace('legacy_debates_20261117') IS NOT NULL THEN
    RAISE EXCEPTION 'legacy_debates_20261117 already exists; inspect before retrying';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
                 AND table_name = 'debate_turns' AND column_name = 'stage')
     OR EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
                AND table_name = 'debate_turns' AND column_name = 'id') THEN
    RAISE EXCEPTION 'Expected the original debate schema; no changes made';
  END IF;
END $$;

CREATE SCHEMA legacy_debates_20261117;
ALTER TABLE debate_turns SET SCHEMA legacy_debates_20261117;
ALTER TABLE debates SET SCHEMA legacy_debates_20261117;

CREATE TABLE debates (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES users(id),
  persona_a_id bigint NOT NULL REFERENCES personas(id),
  persona_b_id bigint NOT NULL REFERENCES personas(id),
  topic varchar(200) NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ENDED')),
  turn_count smallint NOT NULL DEFAULT 0 CHECK (turn_count BETWEEN 0 AND 12),
  summary text,
  ai_model varchar(100),
  created_at timestamptz DEFAULT now(),
  ended_at timestamptz,
  updated_at timestamptz,
  CHECK (persona_a_id <> persona_b_id),
  CHECK ((status = 'ENDED') = (ended_at IS NOT NULL))
);
CREATE INDEX ix_debates_user ON debates(user_id, created_at DESC, id DESC);
CREATE TABLE debate_turns (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  debate_id bigint NOT NULL REFERENCES debates(id),
  turn_order smallint NOT NULL CHECK (turn_order BETWEEN 1 AND 12),
  speaker_type text NOT NULL CHECK (speaker_type IN ('PERSONA','USER')),
  speaker_persona_id bigint REFERENCES personas(id),
  body varchar(300) NOT NULL CHECK (length(body) BETWEEN 1 AND 300),
  ai_model varchar(100),
  created_at timestamptz DEFAULT now(),
  UNIQUE (debate_id, turn_order),
  CHECK ((speaker_type = 'PERSONA') = (speaker_persona_id IS NOT NULL))
);

-- Old records have no creation timestamps or per-turn model. Preserve what is known;
-- use completed_at as the best available timestamp, or migration time if unavailable.
INSERT INTO debates (id, user_id, persona_a_id, persona_b_id, topic, status, turn_count,
                     summary, ai_model, created_at, ended_at, updated_at)
OVERRIDING SYSTEM VALUE
SELECT d.id, d.user_id, d.persona_low_id, d.persona_high_id, d.topic_text,
       CASE WHEN d.completed_at IS NULL THEN 'ACTIVE' ELSE 'ENDED' END,
       (SELECT count(*) FROM legacy_debates_20261117.debate_turns t WHERE t.debate_id = d.id),
       d.issue_summary, d.ai_model, COALESCE(d.completed_at, now()), d.completed_at,
       COALESCE(d.completed_at, now())
FROM legacy_debates_20261117.debates d;
SELECT setval(pg_get_serial_sequence('debates', 'id'), COALESCE((SELECT max(id) FROM debates), 1),
              EXISTS (SELECT 1 FROM debates));
INSERT INTO debate_turns (debate_id, turn_order, speaker_type, speaker_persona_id, body, ai_model, created_at)
SELECT t.debate_id, t.turn_order, 'PERSONA', t.speaker_id, t.body, d.ai_model,
       COALESCE(d.completed_at, now())
FROM legacy_debates_20261117.debate_turns t
JOIN legacy_debates_20261117.debates d ON d.id = t.debate_id;
COMMIT;
