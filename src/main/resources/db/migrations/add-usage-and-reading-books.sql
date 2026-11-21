BEGIN;
CREATE TABLE IF NOT EXISTS daily_usage (
    user_id bigint NOT NULL REFERENCES users(id),
    usage_date date NOT NULL,
    viewed_count integer NOT NULL DEFAULT 0 CHECK (viewed_count >= 0),
    estimated_seconds integer NOT NULL DEFAULT 0 CHECK (estimated_seconds >= 0),
    stop_count integer NOT NULL DEFAULT 0 CHECK (stop_count >= 0),
    pledged_count integer CHECK (pledged_count BETWEEN 1 AND 100),
    pledged_at timestamptz,
    tip_shown boolean NOT NULL DEFAULT false,
    PRIMARY KEY (user_id, usage_date),
    CHECK ((pledged_count IS NULL) = (pledged_at IS NULL))
);
CREATE TABLE IF NOT EXISTS reading_books (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id bigint REFERENCES personas(id),
    title varchar(200) NOT NULL CHECK (length(trim(title)) > 0),
    author varchar(100) NOT NULL CHECK (length(trim(author)) > 0),
    reading_minutes integer NOT NULL CHECK (reading_minutes > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_reading_books_persona ON reading_books(persona_id) WHERE deleted_at IS NULL;
COMMIT;
