# Research: ReAct 循环(第17节)

## R1 core 如何使用 Prompt/ChatResponse 而不循环依赖

- **Decision**: core 新增 `spring-ai-model` 依赖(版本由已锁定的 `spring-ai-bom` 1.1.8 管理),只用数据类型;调用经 core 接口 `ChatGateway`,`ProviderService implements ChatGateway`。
- **Rationale**: 依赖方向 provider→core、storage→core 已定,core 不能反向依赖;第16节 `ProviderService.chat` 入参/返回已是 Spring AI 类型,改签名属改前序公共接口。用户已确认方案 A。
- **Alternatives**: B(core 自有 LlmRequest/LlmResponse + 适配器)——公共类型更多、偏离课件写法,被否决。
- **CVE 说明**: 版本沿用已锁定 1.1.8,未新增依赖线;第16节已评估并抑制的 CVE 不变。实现时用 `./mvnw dependency:tree` 核实 `spring-ai-model` 在 core 的解析结果。

## R2 ToolCall 与 Response 类型

- **Decision**: ToolCall = `AssistantMessage.ToolCall`(id/type/name/arguments);Response = `ChatResponse`,取 `getResult().getOutput()` 的文本与 `hasToolCalls()`/`getToolCalls()`。
- **Rationale**: 避免重复造类型;ProviderService 回的就是这些。写前(H3)须在本地依赖核实 `ChatResponse.hasToolCalls()` 等方法存在,核实不到停下。

## R3 Session 最小契约

- **Decision**: `Session` 为接口:`id()`、`profileName()`、`messages()`(`List<Message>`)、`append(String userMessage)`、`append(ChatResponse)`、`appendToolResult(ToolCall, ToolResult)`。测试用 test 源集内的 `FakeSession`。
- **Rationale**: 课件骨架写 `appendToolResult(result)`,但 tool 消息必须带 toolCall id/name 才能被模型识别,故多带 `ToolCall` 入参;这是对"骨架"的最小必要补充,签名在第18节保持。
- **Note**: 第18节实体放 storage,实现此接口;`session_id` 只在 `SessionManager` 内拼接(H4④),本节不拼接。

## R4 历史截断口径

- **Decision**: "一轮"= 从一条 `UserMessage` 起到下一条 `UserMessage` 前的全部消息;保留最近 N 个轮次(N=`settings.maxHistoryTurns`,缺省 20)。按轮切不会把 assistant 的 tool_calls 与对应 tool 消息拆开。
- **Rationale**: 按消息条数切会破坏 tool_calls/tool 配对,导致模型 API 报错。

## R5 默认值与装配

- **Decision**: `MAX_ITERATIONS` 缺省 10、`maxHistoryTurns` 缺省 20 作为 core 常量,`Profile.settings` 为 null 字段时回落。boot 装配:`AgentService` 依赖的 `SessionManager`、`ToolRegistry` 在第18/20节前无实现,boot 中该装配用 `@ConditionalOnBean`/`ObjectProvider` 保证应用仍能启动(不创建占位实现类);`ContextLoader` 根目录取 `.oryxos`(沿用现有工作区路径配置,实现时看 `ProfileConfiguration`)。
- **Rationale**: 不为占位写假实现(避免多出公共类型),又不阻断启动。

## R6 工具失败语义

- **Decision**: `ToolExecutor.execute` 对工具抛出的异常/未知工具:写 `success=false` 审计(带 `error_message`)、打 ERROR 日志、返回 `ToolResult.fail(原因, retryable=false)` 供循环回填给模型;不吞(审计+日志+结果三处可见),不让循环崩溃(spec US2-4)。模型调用(`ChatGateway`)的异常照常上抛由 `AgentService` finally 清理。
- **Rationale**: 与 spec"失败原因被记录且循环不崩溃"一致;课件 harness"异常不吞"解释为"必须可查"。

## R7 ContextLoader 缺失语义

- **Decision**: Profile `skills` 中引用的 `SKILL.md` 缺失 → 抛业务异常(沿用 `BizException`/`ErrorCode`,实现时核对是否需新增错误码;若需新增按软门禁处理);`bootstrap` 中文件缺失 → `WARN` 日志并跳过。每次 `load(profile)` 现读文件,类内无缓存字段。
- **Rationale**: 课件铁律;静默跳过会造成"人格悄悄丢了"。

## R8 日期时间来源

- **Decision**: `PromptBuilder` 注入 `java.time.Clock`(默认系统时钟),便于 `PromptBuilderTest` 固定时间断言;system prompt 末尾追加一行 ISO 本地日期时间。

## R2 Note(T002 核实结论,Spring AI 1.1.8 本地 jar)

- `ChatResponse.hasToolCalls()`、`getResult().getOutput()`(`AssistantMessage`)存在;`AssistantMessage.getToolCalls()/hasToolCalls()`、`getText()` 存在。
- `AssistantMessage.ToolCall` 为 record `(id, type, name, arguments)`;`ToolResponseMessage.builder().responses(List<ToolResponse>)`,`ToolResponse(id, name, responseData)`。
- `Prompt(List<Message>)`、`new Generation(AssistantMessage)`、`new ChatResponse(List<Generation>)` 存在。
- T003:Skill 缺失沿用 `ErrorCode.NOT_FOUND`,无需新增错误码。
