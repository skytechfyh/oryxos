# 第17节验收报告:ReAct 循环

**分支**:`002-lesson17-react-loop`  **日期**:2026-10-04  **结论**:harness 全绿,剩余人工项等人工确认。

## 六项证据

1. **`./mvnw clean verify` 全绿**:10 个模块 `BUILD SUCCESS`;Spotless → Checkstyle(0 violations)→ PMD + P3C → SpotBugs(BugInstance 0)全过。测试:core 38、storage 7、provider 19、boot 4 等,0 失败。
   - 依赖:仅在 `oryxos-core` 新增 `spring-ai-model`(版本由已锁定的 BOM 管理,为 1.1.8,与 provider 已解析的是同一版本,未新增版本线)与 `spring-boot-starter-test`(test scope)。**未新增 CVE 评估项,但 CI 的 `-Psecurity` dependency-check 才是最终判定,请看 PR 的 CI。**
2. **harness 测试类映射**:`ReActLoopTest`(9)、`PromptBuilderTest`(6)、`ToolExecutorTest`(5)、`AgentServiceTest`(4)、`ContextLoaderTest`(5)均存在且非空;另有 `ToolInvocationRepositoryTest`(4)。
   - 关键回归对号:`stopsAtMaxIterationsWhenModelNeverConverges`(DisplayName=课件原名 `模型一直要调工具_转满最大轮数强制停`,`times(10)` + 含"达到最大轮数");`profileContextClearedWhenProcessingThrows`(DisplayName=`处理中抛异常_ProfileContext也必须被清掉`,`assertNull(ProfileContext.current())`);截断(`truncatesHistoryBeyondMaxTurns`)、日期时间(`systemPromptEndsWithCurrentDateTime`)、成败审计(`ToolExecutorTest`)、无缓存与缺失语义(`ContextLoaderTest`)。
   - **偏离说明**:课件测试方法用中文名,但 Checkstyle `MethodName` 拒绝中文与下划线(第16节同样处理),故方法名用英文驼峰,课件原名放入 `@DisplayName`。断言一条未改。
3. **交付物存在性**:`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`ProfileContext`、`ContextLoader`(core/agent);`ToolInvocation`、`ToolInvocationRepository`(storage);`tool_invocations` 表与索引在 `schema.sql`(手工建表,`ddl-auto: none`,已由 `ToolInvocationRepositoryTest` 验证列)。方法注释抽查:本节新增/修改的 29 个 Java 文件,每个方法、构造器均有中文 Javadoc,缺口(第16节两个测试类里原有的 15 个方法)已补齐。
4. **前序回归**:第16节全部测试(`ProfileLoaderTest`、`ProviderServiceTest`、`ToolSchemaAdapterTest`、`ProviderConfigurationTest`、`LlmCallAuditorTest`、`LlmCallRepositoryTest`)在全量 verify 中全绿。
5. **H4 不变量**:①`ToolExecutor` 执行前留 `Sandbox.enforce` 注释位,注明第24节接线;②`tool_invocations` 成败都写(`ToolExecutorTest` 5 例),`llm_calls` 沿用第16节;③grep 无明文 key;④本节未拼接 `session_id`;⑤grep 无 Reactor/`CompletableFuture`/自建线程池,仅 `ThreadLocal`(`ProfileContext`,finally 清理);⑥无 `ChatClient`/Advisor;自动工具执行仍由第16节的 `internalToolExecutionEnabled(false)` 关闭,本节未新增调用路径。
6. 见下方人工项。

## 补充:真模型集成冒烟 `ReActSmokeIT`

- 位置 `oryxos-provider/src/test/java/io/oryxos/provider/ReActSmokeIT.java`,`@Tag("integration")`,CI 默认跳过;未设置 `DEEPSEEK_API_KEY` 自动跳过。key 只经 `${DEEPSEEK_API_KEY}` 占位符从环境变量读取,仓库内无明文(全仓库 grep 已核)。
- 用真 DeepSeek + 离线假天气工具 + 内存会话,跑通一次多轮 ReAct:模型先调 `get_weather`,看到结果后给出穿搭建议;断言 `tool_invocations` 有成功记录、`llm_calls` 至少 2 条成功(手动真调通过,约 4.6 秒)。
- 局限:假工具与内存会话是第 18/20 节交付前的顶替,**不等于**课件的 Demo 一(调真实 `http_get`),该人工项仍待第 18/20 节后完成。
- 手动跑法见 `quickstart.md`「集成冒烟」。

## 与计划相比的设计取舍(均已在 plan/research 记录)

- core 不能依赖 provider/storage:经用户确认,core 加 `spring-ai-model` 并定义 `ChatGateway`(`ProviderService` 实现,签名未动)与 `ToolInvocationRecorder`(`ToolInvocationAuditor` 实现)。
- `ToolCall` 直接用 `AssistantMessage.ToolCall`;`Session.appendToolResult` 比课件骨架多带 `ToolCall` 入参(tool 消息需要 call id)。
- 占位接口(第18/20/22节补实现,签名不得改):`Session`、`SessionManager`、`ToolRegistry`、`ToolResult`、`OryxTool.execute`、`MemoryContextProvider`。
- boot 的 `AgentConfiguration` 整体 `@Lazy`:`ToolRegistry`/`SessionManager` 实现尚未交付,应用照常启动,取用 `AgentService` 时才解析依赖。
- `ToolExecutor` 额外按 `ProfileContext.current().tools()` 拒绝越权工具(spec 边界项)。
- `ContextLoader` 加路径越界校验。

## 剩余人工项(harness 已判卷,这几项等你人工过)

- [ ] Demo 一对话版用真模型跑通一次(问天气→调 `http_get`→给穿搭建议)。(已用 `ReActSmokeIT` 以离线假工具验证了循环本身。)**当前 `ToolRegistry`/`SessionManager` 没有实现(第18/20节),此项要等这两节落地后才能做**。
- [ ] Code review 确认循环自实现,未使用框架 Agent 封装(`ReActLoop` 仅 93 行,无 `ChatClient`)。
- [ ] PR 的 CI `-Psecurity` dependency-check 通过。

全程未 commit / push / 运行 package.sh。
