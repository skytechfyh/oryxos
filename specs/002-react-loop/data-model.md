# Data Model: ReAct 循环(第17节)

## 持久化:`tool_invocations`(SQLite,手工建表)

口径与 `llm_calls` 一致:成败都写,失败 `success=0` 并带 `error_message`。

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK AUTOINCREMENT | |
| session_id | TEXT | NOT NULL,有索引 | 关联 `llm_calls.session_id` |
| tool_name | TEXT | NOT NULL | |
| input | TEXT | | 模型给的入参 JSON 原文 |
| success | INTEGER | NOT NULL | 1/0 |
| error_message | TEXT | | 失败原因 |
| duration_ms | INTEGER | NOT NULL | |
| created_at | TEXT | NOT NULL | ISO-8601 文本 |

实体 `ToolInvocation`(storage,`@Table(name="tool_invocations")`)、`ToolInvocationRepository extends JpaRepository<ToolInvocation, Long>`(含 `findBySessionId`)。`ddl-auto: none`。

## 内存模型(core)

- **ToolResult**(record):`boolean success`、`String content`、`String errorMessage`、`boolean retryable`;静态工厂 `ok(content)` / `fail(errorMessage, retryable)`。
- **Session**(接口,最小契约):见 research R3。
- **ProfileContext**(静态工具类,`ThreadLocal<Profile>`):`set`/`current`/`clear`;`current()` 无值返回 null。
- **关系**:ToolInvocation.session_id ↔ LlmCall.session_id ↔ Session.id();状态转换无(审计只增不改)。
