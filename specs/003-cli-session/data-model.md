# Data Model: 会话(第18节)

## sessions 表(手工建表,追加到 `oryxos-storage/src/main/resources/schema.sql`)

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `session_id` | TEXT | PRIMARY KEY | `enc(channel):enc(user):enc(profile)`,只由 `JpaSessionManager` 生成 |
| `profile_name` | TEXT | NOT NULL | 关联 Profile |
| `channel` | TEXT | NOT NULL | 接入渠道(`cli` / `web` / `scheduler`) |
| `user_id` | TEXT | NOT NULL | 用户标识 |
| `messages_json` | TEXT | NOT NULL | 整段对话历史的 JSON 数组,新建时为 `[]` |
| `status` | TEXT | NOT NULL | `active` / `archived` |
| `created_at` | TEXT | NOT NULL | ISO-8601 |
| `last_active_at` | TEXT | NOT NULL | ISO-8601,每次 `save` 刷新 |
| `archived_at` | TEXT | 可空 | 本节只存不写(自动归档不在范围) |

DDL 使用 `CREATE TABLE IF NOT EXISTS`,与已有两表同风格;无额外索引(按主键查,`session list` 全表小量扫描)。

## 实体 `io.oryxos.storage.Session`

- 字段与上表一一对应(`@Id session_id`,其余 `@Column`)。
- 实现 `io.oryxos.core.session.Session`:`id()`、`profileName()`、`messages()`、三个 `append`/`appendToolResult`。
- `messages()` 惰性由 `messages_json` 解码为 `List<Message>` 并缓存在 `@Transient` 字段;`append*` 改内存列表;`JpaSessionManager.save` 时编码回写 `messages_json`、刷新 `last_active_at`。
- 状态转换:`active`(新建) →(后续节)→ `archived`;本节不实现转换。

## 校验规则

- `channel` / `user` / `profileName` 任一为空白 → `BizException(BAD_REQUEST)`。
- `get(sessionId)` 找不到 → `Optional.empty()`,不抛异常。
- `save` 传入非本实现产生的 `Session`(非实体)→ `BizException(BAD_REQUEST)`,不静默忽略。

## messages_json 元素格式

```json
{"type":"user","text":"..."}
{"type":"assistant","text":"...","toolCalls":[{"id":"c1","name":"http","arguments":"{...}"}]}
{"type":"tool","responses":[{"id":"c1","name":"http","data":"..."}]}
```

字段以 `type` 区分;缺失的可选字段回读为空列表/空串。
