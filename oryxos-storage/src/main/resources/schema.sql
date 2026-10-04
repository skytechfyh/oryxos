-- 手工维护的建表脚本。SQLite 的 ALTER TABLE 能力很弱,不依赖 hibernate.ddl-auto 做迁移;
-- 表结构演进时在此追加脚本,后续各节的表也追加到本文件。

-- 每次 LLM 调用的审计记录:成功与失败都写入,失败时 success=0 并记录 error_message。
CREATE TABLE IF NOT EXISTS llm_calls (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id        TEXT    NOT NULL,
  provider          TEXT    NOT NULL,
  model             TEXT    NOT NULL,
  prompt_tokens     INTEGER,
  completion_tokens INTEGER,
  total_tokens      INTEGER,
  duration_ms       INTEGER NOT NULL,
  success           INTEGER NOT NULL,
  error_message     TEXT,
  created_at        TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_llm_calls_session_id ON llm_calls (session_id);

-- 每次工具调用的审计记录:成功与失败都写入,失败时 success=0 并记录 error_message。
-- session_id 与 llm_calls.session_id 同值,用于把一次对话里的模型调用与工具调用关联起来。
CREATE TABLE IF NOT EXISTS tool_invocations (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id    TEXT    NOT NULL,
  tool_name     TEXT    NOT NULL,
  input         TEXT,
  success       INTEGER NOT NULL,
  error_message TEXT,
  duration_ms   INTEGER NOT NULL,
  created_at    TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tool_invocations_session_id ON tool_invocations (session_id);
