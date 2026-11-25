-- migrations/0002_usage_days.sql: what a guest uses per day (BLUE_PRINT_GUEST.md, 4)
--   npx wrangler d1 migrations apply slippy --local    (and --remote)

CREATE TABLE IF NOT EXISTS usage_days (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  day     TEXT NOT NULL,                       -- '2026-11-25', UTC
  count   INTEGER NOT NULL DEFAULT 0,          -- the map tiles of that day
  PRIMARY KEY (user_id, day)
);

-- GUEST_DAILY_MAX counts the guests made today
CREATE INDEX IF NOT EXISTS idx_users_guest_created ON users (is_guest, created_at);
