-- migrations/0001_init.sql
--   npx wrangler d1 migrations apply slippy --local    (and --remote)

-- A user: made at the first call of a registered user, or by POST /guest for a guest
CREATE TABLE IF NOT EXISTS users (
  id                  INTEGER PRIMARY KEY,
  firebase_id         TEXT NOT NULL UNIQUE,      -- the token's "sub"
  email               TEXT,                      -- NULL for a guest
  email_verified      INTEGER NOT NULL DEFAULT 0,
  is_guest            INTEGER NOT NULL DEFAULT 0,
  name                TEXT,                      -- a guest's name
  weekly_budget_ticks INTEGER,                   -- NULL: WEEKLY_BUDGET_TICKS
  total_budget_ticks  INTEGER,                   -- NULL: TOTAL_BUDGET_TICKS (or the guest's)
  created_at          TEXT NOT NULL,
  synced_at           TEXT NOT NULL              -- updated at every login and refresh
);

-- The models of the AI and other providers, and what they cost
CREATE TABLE IF NOT EXISTS models (
  id       INTEGER PRIMARY KEY,
  name     TEXT NOT NULL UNIQUE,                 -- as the provider names it: 'claude-haiku-5-5'
  provider TEXT NOT NULL                         -- 'claude', 'openai', 'gemini', ...
);

-- A price change is a new row with a later valid_from, so old calls keep their price
CREATE TABLE IF NOT EXISTS model_rates (
  model_id                 INTEGER NOT NULL REFERENCES models(id),
  valid_from               TEXT NOT NULL,
  ticks_per_million_input  INTEGER NOT NULL,
  ticks_per_million_output INTEGER NOT NULL,
  PRIMARY KEY (model_id, valid_from)
);

-- One row per paid call: the budgets are sums of these
CREATE TABLE IF NOT EXISTS ai_calls (
  id            INTEGER PRIMARY KEY,
  user_id       INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at    TEXT NOT NULL,
  operation     TEXT NOT NULL,                   -- the endpoint: 'geocode'
  model_id      INTEGER NOT NULL REFERENCES models(id),
  input_tokens  INTEGER NOT NULL,
  output_tokens INTEGER NOT NULL,
  ticks         INTEGER NOT NULL                 -- worked out at the time of the call
);
CREATE INDEX IF NOT EXISTS idx_ai_calls_user_time ON ai_calls (user_id, created_at);

-- The models in use, with their prices. These numbers are an example: look up the
-- current ones. 1 tick = $0.0001, so $1 per million tokens is 10000 ticks per million.
INSERT INTO models (name, provider) VALUES ('claude-haiku-5-5', 'claude')
  ON CONFLICT (name) DO NOTHING;
INSERT INTO model_rates (model_id, valid_from, ticks_per_million_input, ticks_per_million_output)
  SELECT id, '2000-01-01T00:00:00.000Z', 10000, 50000 FROM models WHERE name = 'claude-haiku-5-5'
  ON CONFLICT DO NOTHING;
