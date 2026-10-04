# Data Model

## Profile(oryxos-core,不可变 record,YAML 蛇形键 → 驼峰字段)

| 字段 | 类型 | 说明 |
|---|---|---|
| name | String | 唯一标识,必填 |
| description | String | |
| identity | Identity(agentName, prompt) | |
| provider | ProviderRef(name, model, temperature) | 本节唯一校验:`name` 必须在全局层存在 |
| tools | List<String> | |
| skills | List<String> | |
| mcpServers | List<String> | YAML 键 `mcp_servers` |
| channels | List<String> | |
| notifyChannels | List<String> | YAML 键 `notify_channels` |
| schedules | List<Map<String,Object>> | 结构由第 25 节定义,本节原样承载 |
| bootstrap | List<String> | |
| settings | Settings(maxIterations, maxHistoryTurns) | `max_iterations`、`max_history_turns` |

缺省:列表字段缺省为空列表,`settings`/`identity` 缺省为空对象;`name`、`provider` 缺失视为非法 Profile。

## ProfileRegistry

`Map<String, Profile>`(线程安全发布),`register`(包可见,仅启动扫描用)/`find(name)`/`all()`。同名拒绝覆盖。

## 全局层 ProviderProperties(`oryxos.providers[]`)

| 键 | 说明 |
|---|---|
| name | 唯一 provider 名 |
| base-url | OpenAI 兼容端点(TechnicalSolution §3.1) |
| api-key | `${XXX_API_KEY:}` 环境变量占位,空视为缺失 |

## LlmCall(oryxos-storage)——表 `llm_calls`(**列待用户确认**)

| 列 | 类型 | 约束 |
|---|---|---|
| id | INTEGER | PK AUTOINCREMENT |
| session_id | TEXT | NOT NULL,索引 |
| provider | TEXT | NOT NULL |
| model | TEXT | NOT NULL |
| prompt_tokens | INTEGER | 可空 |
| completion_tokens | INTEGER | 可空 |
| total_tokens | INTEGER | 可空 |
| duration_ms | INTEGER | NOT NULL |
| success | INTEGER(0/1) | NOT NULL |
| error_message | TEXT | 失败时填 |
| created_at | TEXT(ISO-8601) | NOT NULL |

## OryxTool(oryxos-core,最小契约,决策 1)

`String name()`、`String description()`、`String getInputSchema()`(JSON Schema 字符串)。不含 `execute`——本节只翻译;执行契约与 `ToolResult` 归第 20 节。
