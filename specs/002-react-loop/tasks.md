---

description: "第17节 ReAct 循环任务清单"
---

# Tasks: ReAct 循环(自实现调度、提示组装、工具执行、统一入口、上下文加载)

**Input**: `specs/002-react-loop/` 下的 plan.md、spec.md、research.md、data-model.md、contracts/core-interfaces.md

**Prerequisites**: plan 的软门禁结论已确认(core 加 `spring-ai-model` + `ChatGateway` / `ToolInvocationRecorder` 两个接口;Session/SessionManager/ToolRegistry/ToolResult/OryxTool.execute/可选记忆接口为最小占位;ToolCall 直接用 `AssistantMessage.ToolCall`)

**Tests**: 课件"验收 harness"要求五个测试类,**全部原样落地且先于或伴随对应实现**;课件两个回归测试(`模型一直要调工具_转满最大轮数强制停`、`处理中抛异常_ProfileContext也必须被清掉`)方法名与断言原样保留。

**Organization**: 按 spec 的五个用户故事分组。US1~US3 共用 `ReActLoop`/`ToolExecutor`,其共同依赖(占位接口、依赖、表)放 Foundational。

## Format: `[ID] [P?] [Story] Description`

- 代码风格:Google 格式 + 阿里 P3C,**每个方法(含 private、构造器、测试方法)带中文 Javadoc,类带中文类注释**;避开 Java 18+ 增强 switch `default ->`;每个任务完成后跑该模块测试,红了当场修。
- 构建一律 `./mvnw`;全程不 commit / push;不新增 plan 未列明的公共类型、配置键、表。

---

## Phase 1: Setup(依赖核实)

- [X] T001 在 `oryxos-core/pom.xml` 增加依赖 `org.springframework.ai:spring-ai-model`(版本由根 pom 已导入的 `spring-ai-bom` 管理,不写版本)与测试依赖 `spring-boot-starter-test`(test scope);执行 `./mvnw -pl oryxos-core -am dependency:tree`,确认解析版本为 1.1.8、无版本冲突、未引入 Tomcat/log4j 等已固定组件的回退
- [X] T002 核实 Spring AI 1.1.8 本地 API(H3,核实不到按软门禁停下):`ChatResponse.hasToolCalls()`/`getResult().getOutput().getToolCalls()`/`getText()`、`AssistantMessage.ToolCall(id,type,name,arguments)` 构造、`ToolResponseMessage` 及其 `ToolResponse`、`Prompt(List<Message>)`;结论追加到 `specs/002-react-loop/research.md` R2 的 Note
- [X] T003 [P] 核对 `oryxos-core` 现有 `BizException`/`ErrorCode`,确定 Skill 缺失报错使用的错误码;若需新增错误码按软门禁停下报告(research R7)

---

## Phase 2: Foundational(阻塞所有用户故事)

**Purpose**: 全部用户故事共用的最小占位与接口;签名以 `contracts/core-interfaces.md` 为准逐字保真。

- [X] T004 [P] 在 `oryxos-core/src/main/java/io/oryxos/core/tool/ToolResult.java` 建 record `ToolResult(boolean success, String content, String errorMessage, boolean retryable)`,静态工厂 `ok(String content)`、`fail(String errorMessage, boolean retryable)`
- [X] T005 在 `oryxos-core/src/main/java/io/oryxos/core/tool/OryxTool.java` 增加 `ToolResult execute(String inputJson)`;grep 全仓所有 `OryxTool` 实现/匿名类/lambda(尤其 `oryxos-provider` 的 `ToolSchemaAdapterTest`、`ProviderServiceTest`),补上 `execute`,仅改夹具,不放宽任何断言;类注释里更新"execute 由第 20 节补全实现"的说明
- [X] T006 [P] 在 `oryxos-core/src/main/java/io/oryxos/core/tool/ToolRegistry.java` 建接口:`Optional<OryxTool> find(String name)`、`List<OryxTool> forProfile(Profile profile)`
- [X] T007 [P] 在 `oryxos-core/src/main/java/io/oryxos/core/session/Session.java` 建接口(id/profileName/messages/`append(String)`/`append(ChatResponse)`/`appendToolResult(AssistantMessage.ToolCall, ToolResult)`)与 `SessionManager.java`(`void save(Session)`);类注释注明"最小契约,第 18 节补实现"
- [X] T008 [P] 在 `oryxos-core/src/main/java/io/oryxos/core/agent/` 建三个接口:`ChatGateway`(`ChatResponse chat(String sessionId, Profile profile, Prompt prompt, List<OryxTool> availableTools)`)、`ToolInvocationRecorder`(`void record(String sessionId, String toolName, String input, boolean success, String errorMessage, long durationMs)`)、`MemoryContextProvider`(`Optional<String> longTermMemory(Profile profile)`,可选,第 22 节实现)
- [X] T009 在 `oryxos-provider/src/main/java/io/oryxos/provider/ProviderService.java` 增加 `implements ChatGateway`,仅在 4 参 `chat` 上加 `@Override`,**签名与行为不动**;跑 `./mvnw -pl oryxos-provider -am test` 确认第16节测试仍绿
- [X] T010 [P] 在 `oryxos-core/src/test/java/io/oryxos/core/agent/` 建测试夹具 `FakeSession`(实现 Session 的内存版)与构造 `Profile`/`ChatResponse`(带/不带 tool call)的工具方法类 `TestFixtures`;仅 test 源集,不进 main
- [X] T010a [P] 【前移自 T027】`oryxos-core/src/main/java/io/oryxos/core/agent/ProfileContext.java`:`ThreadLocal<Profile>`,`set`/`current`/`clear`,私有构造
- [X] T010b 【前移自 T030】`oryxos-core/src/test/java/io/oryxos/core/agent/ContextLoaderTest.java`(`@TempDir`):`改文件后下一次load立即读到新内容`、`Skill引用缺失_报错`、`Bootstrap缺失_WARN且不抛`(Logback `ListAppender` 断言 WARN)、`同时含Bootstrap与Skill_顺序拼接`
- [X] T010c 【前移自 T031,须先于 T013,避免 PromptBuilder 依赖未建类】`oryxos-core/src/main/java/io/oryxos/core/agent/ContextLoader.java`:构造入参根目录 `Path`;`load(Profile)` 按 `bootstrap` 读 `.oryxos/` 下文件(缺失 WARN 跳过)、按 `skills` 读 `.oryxos/skills/<name>/SKILL.md`(缺失抛业务异常,错误码按 T003 结论);类内无缓存字段;路径需防穿越(不得读 `.oryxos/` 之外,拼接后 `normalize` 校验 startsWith)

**Checkpoint**: `./mvnw -pl oryxos-core,oryxos-provider -am test` 全绿。

---

## Phase 3: User Story 1 - 多步任务:先取数据再给建议 (P1) 🎯 MVP

**Goal**: 循环能"想→做→看"并在无工具调用时收尾,提示按四部分顺序组装。

**Independent Test**: mock 的 `ChatGateway` 与 `ToolRegistry`:首轮要工具、次轮出文本,断言工具执行一次、结果出现在第二轮提示、返回次轮文本;无工具调用一轮收尾;多工具调用按序执行。

### Tests for User Story 1(先写,须先红)

- [X] T011 [P] [US1] `oryxos-core/src/test/java/io/oryxos/core/agent/ReActLoopTest.java`:`无工具调用_一轮收尾`、`有工具调用_执行并回填进下一轮`、`一次响应多个工具调用_按顺序执行`、`空响应_视为无工具调用并收尾`、`每轮响应和工具结果都累积进Session`(US3 累积点同类放此)
- [X] T012 [P] [US1] `oryxos-core/src/test/java/io/oryxos/core/agent/PromptBuilderTest.java`:`四部分顺序正确`(system → 长期记忆 → 历史 → 工具列表经 `availableTools`)、`未启用长期记忆则跳过`、`systemPrompt末尾含当前日期时间`(固定 `Clock` 断言)

### Implementation for User Story 1

- [X] T013 [US1] `oryxos-core/src/main/java/io/oryxos/core/agent/PromptBuilder.java`:构造入参 `ContextLoader`、`ToolRegistry`、`Optional<MemoryContextProvider>`(或可空)、`Clock`;`build(Session, Profile)` 返回 `Prompt`,顺序:system(identity.prompt + `contextLoader.load(profile)` + 末尾日期时间行)→ 长期记忆(有则单独一条 system 消息,标明"长期记忆")→ 会话历史 → ;`availableTools(Profile)` 返回 `toolRegistry.forProfile`;历史截断见 US2 的 T018,此处先接入调用点
- [X] T014 [US1] `oryxos-core/src/main/java/io/oryxos/core/agent/ToolExecutor.java`(US1 最小可用版:`find` 工具→执行→返回 `ToolResult`;在执行前留注释位"24 节接线:Sandbox.enforce";审计与失败语义在 US3 的 T021 补全)
- [X] T015 [US1] `oryxos-core/src/main/java/io/oryxos/core/agent/ReActLoop.java`:`run(Session, String userMessage, Profile)`,骨架照课件:`session.append(user)`→`for i < maxIterations`→`promptBuilder.build`→`gateway.chat(session.id(), profile, prompt, promptBuilder.availableTools(profile))`→`session.append(resp)`→无工具调用返回文本→否则逐个 `toolExecutor.execute(session.id(), call)` 并 `session.appendToolResult(call, result)`;循环内不含 prompt 拼装/模型细节/工具执行细节;不用 Reactor/CompletableFuture/线程池

**Checkpoint**: ReActLoopTest(US1 部分)、PromptBuilderTest 绿。

---

## Phase 4: User Story 2 - 边界兜底:死循环、上下文、失败 (P1)

**Goal**: 最大轮数兜底、历史按 N 轮截断、工具失败不崩循环。

**Independent Test**: 课件回归测试 + 截断测试。

### Tests for User Story 2

- [X] T016 [P] [US2] 在 `ReActLoopTest` 原样落地课件回归测试 `模型一直要调工具_转满最大轮数强制停`(`verify(times(10))`、`assertTrue(reply.contains("达到最大轮数"))`),另加 `Profile未指定轮数_默认10`、`Profile指定轮数_以Profile为准`、`工具执行失败_失败原因回填且循环不抛出`
- [X] T017 [P] [US2] 在 `PromptBuilderTest` 加 `历史超N轮被截断`(R4:按 UserMessage 切轮,不拆开 tool_calls 与 tool 消息)、`未指定maxHistoryTurns_默认20`

### Implementation for User Story 2

- [X] T018 [US2] `PromptBuilder` 实现历史截断:保留最近 N(`settings.maxHistoryTurns` 缺省常量 20)个轮次;常量 `MAX_HISTORY_TURNS=20` 定义在 `PromptBuilder`
- [X] T019 [US2] `ReActLoop` 补常量 `MAX_ITERATIONS=10`(`settings.maxIterations` 为 null 回落),循环耗尽返回含"达到最大轮数"的文本(课件原文"达到最大轮数,已停止"),恰好调用 `gateway` 最大轮数次

**Checkpoint**: 课件回归测试 1 绿;截断测试绿。

---

## Phase 5: User Story 3 - 全程可审计 (P1)

**Goal**: 每次工具调用成败都有且仅有一条 `tool_invocations`,失败不吞。

**Independent Test**: `ToolExecutorTest` + `ToolInvocationRepositoryTest`。

### Tests for User Story 3

- [X] T020 [P] [US3] `oryxos-core/src/test/java/io/oryxos/core/agent/ToolExecutorTest.java`:`成功写审计success为true`、`失败也写审计success为false且带原因`(mock `ToolInvocationRecorder`,断言每次恰好一条)、`未知工具_写失败审计并返回失败结果`、`工具不在当前Profile可用范围_按失败处理`、`工具抛异常_不吞_结果与审计都带原因`
- [X] T021 [P] [US3] `oryxos-storage/src/test/java/io/oryxos/storage/ToolInvocationRepositoryTest.java`:仿 `LlmCallRepositoryTest`,存取成功与失败各一条,`findBySessionId` 返回正确;`success`/`error_message` 列映射正确

### Implementation for User Story 3

- [X] T022 [US3] 在 `oryxos-storage/src/main/resources/schema.sql` 追加 `tool_invocations` 建表(列严格按 data-model.md:id INTEGER PK AUTOINCREMENT、session_id TEXT NOT NULL、tool_name TEXT NOT NULL、input TEXT、success INTEGER NOT NULL、error_message TEXT、duration_ms INTEGER NOT NULL、created_at TEXT NOT NULL,`CREATE TABLE IF NOT EXISTS`)及 `idx_tool_invocations_session_id` 索引;同步清理 `db/audit-tables.sql` 的预留注释
- [X] T023 [P] [US3] `oryxos-storage` 建 `ToolInvocation` 实体(`@Table(name="tool_invocations")`,布尔列与 `LlmCall` 同写法,`created_at` 用 ISO-8601 文本)与 `ToolInvocationRepository`(含 `findBySessionId`)
- [X] T024 [US3] `oryxos-storage` 建 `ToolInvocationAuditor`(`@Component implements ToolInvocationRecorder`):`save` 异常只记日志不外抛(同 `LlmCallAuditor` 口径)
- [X] T025 [US3] 补全 `ToolExecutor`:`execute(sessionId, call)` 计时;成功→`recorder.record(..., true, null, ms)`;未知工具或工具抛异常→ERROR 日志 + `recorder.record(..., false, 原因, ms)` + 返回 `ToolResult.fail(原因, false)`;`ProfileContext.current()` 非空且 `tools()` 不含该工具时同样按失败处理(spec 边界);确保一次调用恰好一条审计

**Checkpoint**: `ToolExecutorTest`、`ToolInvocationRepositoryTest` 绿。

---

## Phase 6: User Story 4 - 统一入口与 Agent 身份传递 (P2)

**Goal**: `AgentService.process` + `ProfileContext`,异常也清理,结束持久化。

### Tests for User Story 4

- [X] T026 [P] [US4] `oryxos-core/src/test/java/io/oryxos/core/agent/AgentServiceTest.java`:`处理期间ProfileContext可取到当前Profile`、课件回归测试 `处理中抛异常_ProfileContext也必须被清掉`(`assertThrows` + `assertNull(ProfileContext.current())`)、`正常结束_Session被持久化且ProfileContext已清除`

### Implementation for User Story 4

- [X] T028 [US4] `oryxos-core/src/main/java/io/oryxos/core/agent/AgentService.java`:`process(Session, String)` 骨架照课件(`profileRegistry.get(session.profileName())`→`ProfileContext.set`→`try { reActLoop.run; sessionManager.save } finally { ProfileContext.clear }`);异常不吞
- [X] T029 [US4] `oryxos-boot` 装配:新增/扩展配置类装配 `ContextLoader`、`PromptBuilder`、`ToolExecutor`、`ReActLoop`、`AgentService`;`SessionManager`/`ToolRegistry` 在 18/20 节前无实现,用 `ObjectProvider`(不用 `@ConditionalOnBean`,其对注册顺序敏感)使应用仍能启动(research R5);`ChatGateway` 取 `ProviderService`;`ToolInvocationRecorder` 取 `ToolInvocationAuditor`;`ContextLoader` 根目录沿用现有工作区配置(先读 `ProfileConfiguration`)

**Checkpoint**: AgentServiceTest 绿;`./mvnw -pl oryxos-boot -am test` 应用上下文能起。

---

## Phase 7: User Story 5 - 上下文加载:改完立即生效 (P2)

> `ContextLoader` 的测试与实现(T010b/T010c)已前移到 Foundational,因 `PromptBuilder`(T013)编译依赖它;本故事的验收点即 `ContextLoaderTest` 全绿。

**Checkpoint**: ContextLoaderTest 绿;PromptBuilderTest 用真实 ContextLoader 的集成点不破。

---

## Phase 8: Polish & 节级验收

- [X] T032 `./mvnw clean verify` 全绿(Spotless → Checkstyle → PMD/P3C → SpotBugs);如需 SpotBugs 排除项,写明理由;`./mvnw spotless:apply` 修格式
- [X] T033 抽查本节新增/修改的 Java(含测试)每个方法、构造器均有中文 Javadoc,类有中文类注释,缺失当场补
- [X] T034 H4 六条不变量自查(Sandbox 留位、llm_calls/tool_invocations 成败都写、无明文 key、session_id 只在 SessionManager 内拼、无 Reactor/CompletableFuture/线程池、无 Spring AI 自动工具执行路径);跑第 16 节全部测试回归
- [X] T035 写 `specs/002-react-loop/lesson-report.md`(六项证据 + 剩余人工项)

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → 用户故事阶段;US1 是 MVP,US2/US3 依赖 US1 的 `ReActLoop`/`ToolExecutor`/`PromptBuilder` 骨架;US4 依赖 US1;`ContextLoader`(T010b/T010c)与 `ProfileContext`(T010a)已前移到 Foundational,先于 T013、T025。
- 同一文件内的任务串行(`ReActLoopTest`:T011→T016;`PromptBuilderTest`:T012→T017;`ToolExecutor`:T014→T025;`PromptBuilder`:T013→T018;`ReActLoop`:T015→T019)。

## Parallel Opportunities

- Phase 2:T004/T006/T007/T008/T010/T010a 可并行(不同文件);T005 须在 T004 后。
- Phase 3 测试 T011/T012 并行;Phase 5 T020/T021/T023 并行;US4 的 T026 与 T028 之前的测试编写并行。

## Implementation Strategy

- **MVP**:Phase 1~3(US1)→ 循环可跑通多步任务。
- 增量:US2 兜底 → US3 审计(落库)→ US4 统一入口 → US5 上下文;每个阶段 Checkpoint 绿再进下一个。
