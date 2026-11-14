-- 디쇼츠 학교 시연용 목표 스키마 / PostgreSQL 16+
-- 빈 데이터베이스에서 한 번 실행한다. 기존 DB 변경/삭제/시드 주입은 하지 않는다.
-- 모든 설정 변경 시 updated_at은 애플리케이션이 갱신한다.
BEGIN;

CREATE TABLE users (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username varchar(30) NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9._]{3,30}$'),
  password_hash text NOT NULL,
  display_name varchar(50) NOT NULL,
  bio varchar(500) NOT NULL DEFAULT '',
  avatar_path text,
  role text NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE topics (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name varchar(50) NOT NULL UNIQUE,
  description varchar(300) NOT NULL DEFAULT '',
  sort_order integer NOT NULL DEFAULT 0,
  deleted_at timestamptz
);
CREATE TABLE user_interests (
  user_id bigint NOT NULL REFERENCES users(id),
  topic_id bigint NOT NULL REFERENCES topics(id),
  PRIMARY KEY (user_id, topic_id)
);
CREATE INDEX ix_user_interests_topic ON user_interests(topic_id);
CREATE TABLE personas (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username varchar(30) NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9._]{3,30}$'),
  display_name varchar(50) NOT NULL,
  era varchar(100),
  bio varchar(1000) NOT NULL DEFAULT '',
  avatar_path text,
  ai_disclosure text NOT NULL DEFAULT '실제 인물이 아닌 AI 재현입니다.',
  perspective text NOT NULL DEFAULT '',
  voice text NOT NULL DEFAULT '',
  knowledge_scope text NOT NULL DEFAULT '',
  prohibitions text NOT NULL DEFAULT '',
  conversation_rules text NOT NULL DEFAULT '',
  post_style text NOT NULL DEFAULT '',
  active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz,
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE persona_topics (
  persona_id bigint NOT NULL REFERENCES personas(id),
  topic_id bigint NOT NULL REFERENCES topics(id),
  PRIMARY KEY (persona_id, topic_id)
);
CREATE INDEX ix_persona_topics_topic ON persona_topics(topic_id, persona_id);
CREATE TABLE posts (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  persona_id bigint NOT NULL REFERENCES personas(id),
  title varchar(200) NOT NULL,
  summary text NOT NULL DEFAULT '',
  caption text NOT NULL DEFAULT '',
  grounding_mode text NOT NULL DEFAULT 'MODEL_KNOWLEDGE' CHECK (grounding_mode = 'MODEL_KNOWLEDGE'),
  status text NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('PUBLISHED','HIDDEN')),
  deleted_at timestamptz,
  ai_model varchar(100),
  published_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (id, persona_id)
);
CREATE INDEX ix_posts_feed ON posts(published_at DESC, id DESC) WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
CREATE INDEX ix_posts_persona ON posts(persona_id, published_at DESC, id DESC);
CREATE TABLE post_topics (
  post_id bigint NOT NULL REFERENCES posts(id),
  topic_id bigint NOT NULL REFERENCES topics(id),
  PRIMARY KEY (post_id, topic_id)
);
CREATE INDEX ix_post_topics_topic ON post_topics(topic_id, post_id);
CREATE TABLE post_sections (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  post_id bigint NOT NULL REFERENCES posts(id),
  section_order smallint NOT NULL CHECK (section_order BETWEEN 1 AND 10),
  title varchar(200),
  body text NOT NULL,
  citation_type text NOT NULL DEFAULT 'RECONSTRUCTION' CHECK (citation_type IN ('DIRECT_QUOTE','RECONSTRUCTION')),
  quote_attribution text,
  image_path text,
  image_alt varchar(300),
  image_mime varchar(50),
  image_width integer CHECK (image_width > 0),
  image_height integer CHECK (image_height > 0),
  image_ai_generated boolean NOT NULL DEFAULT false,
  image_credit text,
  UNIQUE (post_id, section_order),
  UNIQUE (post_id, id),
  CHECK (image_path IS NULL OR (image_alt IS NOT NULL AND length(image_alt) > 0)),
  CHECK (citation_type <> 'DIRECT_QUOTE' OR quote_attribution IS NOT NULL)
);
CREATE TABLE key_points (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  post_id bigint NOT NULL REFERENCES posts(id),
  point_order smallint NOT NULL CHECK (point_order BETWEEN 1 AND 4),
  text varchar(500) NOT NULL,
  section_id bigint,
  UNIQUE (post_id, point_order),
  UNIQUE (post_id, id),
  FOREIGN KEY (post_id, section_id) REFERENCES post_sections(post_id, id)
);
CREATE INDEX ix_key_points_section ON key_points(post_id, section_id);
CREATE TABLE post_sources (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  post_id bigint NOT NULL REFERENCES posts(id),
  source_order smallint NOT NULL CHECK (source_order > 0),
  title varchar(300) NOT NULL,
  author varchar(200),
  publisher varchar(200),
  url text,
  publication_label varchar(50),
  verified boolean NOT NULL DEFAULT false,
  checked_at date,
  note text,
  UNIQUE (post_id, source_order),
  CHECK (NOT verified OR checked_at IS NOT NULL)
);
CREATE TABLE generation_jobs (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_key varchar(100) NOT NULL UNIQUE,
  persona_id bigint NOT NULL REFERENCES personas(id),
  topic_id bigint NOT NULL REFERENCES topics(id),
  subject varchar(200) NOT NULL,
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
  attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  ai_model varchar(100),
  error_message text,
  result_post_id bigint UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  finished_at timestamptz,
  FOREIGN KEY (result_post_id, persona_id) REFERENCES posts(id, persona_id),
  CHECK ((status = 'SUCCEEDED') = (result_post_id IS NOT NULL)),
  CHECK (status NOT IN ('SUCCEEDED','FAILED') OR finished_at IS NOT NULL)
);
CREATE INDEX ix_jobs_status ON generation_jobs(status, id);
CREATE INDEX ix_jobs_persona ON generation_jobs(persona_id);
CREATE INDEX ix_jobs_topic ON generation_jobs(topic_id);
CREATE TABLE questions (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  post_id bigint NOT NULL REFERENCES posts(id),
  key_point_id bigint NOT NULL UNIQUE,
  prompt text NOT NULL,
  explanation text NOT NULL,
  correct_option_order smallint NOT NULL CHECK (correct_option_order BETWEEN 1 AND 4),
  UNIQUE (post_id, id),
  FOREIGN KEY (post_id, key_point_id) REFERENCES key_points(post_id, id)
);
CREATE TABLE answer_options (
  question_id bigint NOT NULL REFERENCES questions(id),
  option_order smallint NOT NULL CHECK (option_order BETWEEN 1 AND 4),
  text varchar(500) NOT NULL,
  PRIMARY KEY (question_id, option_order)
);
-- 순환 FK: 질문과 선택지는 같은 트랜잭션에서 등록한다.
ALTER TABLE questions ADD CONSTRAINT fk_question_correct_option
  FOREIGN KEY (id, correct_option_order) REFERENCES answer_options(question_id, option_order)
  DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE quiz_attempts (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES users(id),
  post_id bigint NOT NULL REFERENCES posts(id),
  correct_count smallint NOT NULL CHECK (correct_count >= 0),
  total_count smallint NOT NULL CHECK (total_count BETWEEN 2 AND 4),
  submitted_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (id, post_id),
  CHECK (correct_count <= total_count)
);
CREATE INDEX ix_attempts_user ON quiz_attempts(user_id, submitted_at DESC, id DESC);
CREATE INDEX ix_attempts_post ON quiz_attempts(post_id);
CREATE TABLE quiz_answers (
  attempt_id bigint NOT NULL,
  post_id bigint NOT NULL,
  question_id bigint NOT NULL,
  selected_option_order smallint NOT NULL,
  is_correct boolean NOT NULL,
  PRIMARY KEY (attempt_id, question_id),
  FOREIGN KEY (attempt_id, post_id) REFERENCES quiz_attempts(id, post_id),
  FOREIGN KEY (post_id, question_id) REFERENCES questions(post_id, id),
  FOREIGN KEY (question_id, selected_option_order) REFERENCES answer_options(question_id, option_order)
);
CREATE INDEX ix_answers_question ON quiz_answers(question_id, selected_option_order);
CREATE INDEX ix_answers_post ON quiz_answers(post_id, question_id);
CREATE TABLE study_conversations (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES users(id),
  post_id bigint NOT NULL REFERENCES posts(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  last_message_at timestamptz,
  UNIQUE (user_id, post_id),
  UNIQUE (id, post_id)
);
CREATE INDEX ix_conversations_post ON study_conversations(post_id);
CREATE TABLE conversation_messages (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  conversation_id bigint NOT NULL REFERENCES study_conversations(id),
  role text NOT NULL CHECK (role IN ('USER','PERSONA')),
  content text NOT NULL,
  ai_model varchar(100),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (conversation_id, id)
);
CREATE TABLE learning_assessments (
  conversation_id bigint NOT NULL,
  post_id bigint NOT NULL,
  key_point_id bigint NOT NULL,
  evidence_message_id bigint NOT NULL,
  level text NOT NULL CHECK (level IN ('UNDERSTOOD','PARTIAL','MISSED')),
  feedback text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (conversation_id, key_point_id),
  FOREIGN KEY (conversation_id, post_id) REFERENCES study_conversations(id, post_id),
  FOREIGN KEY (post_id, key_point_id) REFERENCES key_points(post_id, id),
  FOREIGN KEY (conversation_id, evidence_message_id) REFERENCES conversation_messages(conversation_id, id)
);
CREATE INDEX ix_assessments_point ON learning_assessments(post_id, key_point_id);
CREATE INDEX ix_assessments_message ON learning_assessments(conversation_id, evidence_message_id);
CREATE TABLE review_items (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES users(id),
  post_id bigint NOT NULL,
  key_point_id bigint NOT NULL,
  reason text NOT NULL CHECK (reason IN ('QUIZ_WRONG','CHAT_MISSED')),
  status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','DONE')),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  UNIQUE (user_id, key_point_id),
  FOREIGN KEY (post_id, key_point_id) REFERENCES key_points(post_id, id),
  CHECK ((status = 'DONE') = (completed_at IS NOT NULL))
);
CREATE INDEX ix_review_user ON review_items(user_id, status, updated_at DESC, id DESC);
CREATE INDEX ix_review_point ON review_items(post_id, key_point_id);
CREATE TABLE follows (
  user_id bigint NOT NULL REFERENCES users(id),
  persona_id bigint NOT NULL REFERENCES personas(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, persona_id)
);
CREATE INDEX ix_follows_persona ON follows(persona_id);
CREATE TABLE post_likes (
  user_id bigint NOT NULL REFERENCES users(id),
  post_id bigint NOT NULL REFERENCES posts(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, post_id)
);
CREATE INDEX ix_likes_post ON post_likes(post_id);
CREATE TABLE saved_posts (
  user_id bigint NOT NULL REFERENCES users(id),
  post_id bigint NOT NULL REFERENCES posts(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, post_id)
);
CREATE INDEX ix_saved_user ON saved_posts(user_id, created_at DESC, post_id DESC);
CREATE INDEX ix_saved_post ON saved_posts(post_id);
CREATE TABLE debates (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES users(id),
  persona_low_id bigint NOT NULL REFERENCES personas(id),
  persona_high_id bigint NOT NULL REFERENCES personas(id),
  topic_text varchar(200) NOT NULL,
  topic_key varchar(200) NOT NULL,
  issue_summary text NOT NULL,
  ai_model varchar(100),
  completed_at timestamptz NOT NULL DEFAULT now(),
  CHECK (persona_low_id < persona_high_id),
  UNIQUE (user_id, persona_low_id, persona_high_id, topic_key),
  UNIQUE (id, persona_low_id, persona_high_id)
);
CREATE INDEX ix_debates_daily ON debates(user_id, completed_at DESC, id DESC);
CREATE INDEX ix_debates_low ON debates(persona_low_id);
CREATE INDEX ix_debates_high ON debates(persona_high_id);
CREATE TABLE debate_turns (
  debate_id bigint NOT NULL,
  turn_order smallint NOT NULL CHECK (turn_order BETWEEN 1 AND 6),
  persona_low_id bigint NOT NULL,
  persona_high_id bigint NOT NULL,
  speaker_id bigint NOT NULL,
  stage text NOT NULL CHECK (stage IN ('OPENING','REBUTTAL','CLOSING')),
  body varchar(300) NOT NULL,
  PRIMARY KEY (debate_id, turn_order),
  UNIQUE (debate_id, speaker_id, stage),
  FOREIGN KEY (debate_id, persona_low_id, persona_high_id) REFERENCES debates(id, persona_low_id, persona_high_id),
  CHECK (speaker_id IN (persona_low_id, persona_high_id))
);
CREATE TABLE post_reports (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES users(id),
  post_id bigint NOT NULL REFERENCES posts(id),
  reason_code text NOT NULL CHECK (reason_code IN ('INACCURATE','FAKE_QUOTE','HARMFUL','INAPPROPRIATE','OTHER')),
  detail varchar(500),
  status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED','DISMISSED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  resolved_at timestamptz,
  UNIQUE (user_id, post_id),
  CHECK ((status = 'OPEN') = (resolved_at IS NULL))
);
CREATE INDEX ix_reports_status ON post_reports(status, created_at DESC, id DESC);
CREATE INDEX ix_reports_post ON post_reports(post_id);

COMMIT;
