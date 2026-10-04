---

description: "第16节 Agent Provider 与 Profile 加载任务清单"
---

# Tasks: Agent Provider(大模型统一接入层)与 Profile 加载

**Input**: `specs/001-agent-provider/` 下的 plan.md、spec.md、research.md、data-model.md、contracts/provider-api.md

**Prerequisites**: plan.md、spec.md 已就绪;软门禁结论见 plan(OryxTool 最小接口、直接用 Spring AI 类型、缺 key 跳过、依赖方案 spring-ai-bom + openai starter)

**Tests**: 课件"验收 harness"明确要求,**全部测试任务必须原样落地且先于或伴随对应实现**。

**Organization**: 按 spec 的三个用户故事分组。US1(路由)与 US2(审计)共用 `ProviderService`,其共同依赖(审计组件、表、OryxTool)放 Foundational。

## Format: `[ID] [P?] [Story] Description`

- 代码风格:Google 格式 + 阿里 P3C,中文注释(只写"为什么");避开 Java 18+ 增强 switch `default ->`;每个任务完成后跑该模块测试,红了当场修。
- 构建一律 `./mvnw`;全程不 commit / push。

---

## Phase 1: Setup(依赖与模块接线)

**Purpose**: 引入第三方依赖并核实(软门禁 H3:核实不到即停下报告)

- [X] T001 在根 `pom.xml` 的 properties 新增 `spring-ai.version`(1.1.2),`dependencyManagement` 内 import `org.springframework.ai:spring-ai-bom`,须放在 `spring-ai-alibaba-bom` 之前或之后均可但不得覆盖已固定的安全版本(Tomcat/log4j/swagger-ui)
- [X] T002 在 `oryxos-provider/pom.xml` 添加 `spring-ai-starter-model-openai`、`oryxos-storage` 依赖及测试依赖 `spring-boot-starter-test`(核对 `oryxos-boot` 已聚合 provider);随后执行 `./mvnw -pl oryxos-provider -am dependency:tree`,确认 spring-ai 1.1.2 与 Alibaba 1.1.2.4-security-fix 无冲突、`OpenAiChatModel`/`ToolCallingChatOptions`/`ToolDefinition` 类在本地依赖中存在;核实不到按软门禁停下
- [X] T003 在 `oryxos-core/pom.xml` 确认 snakeyaml 与测试依赖可用(snakeyaml 版本已由根 pom 管理,无则添加依赖声明,不新增版本)
- [X] T004 [P] 在 `oryxos-storage/pom.xml` 确认 `spring-boot-starter-test`(含 DataJpaTest 所需)与 sqlite 测试依赖齐全
- [X] T005 核对 Spring AI 1.1.2 真实 API:查明关闭自动工具执行的选项方法名(`internalToolExecutionEnabled` / `getInternalToolExecutionEnabled`)、`Usage` 取 token 的方法、`OpenAiApi.builder()` 构建方式,把结论追加到 `specs/001-agent-provider/research.md` R2/R3 的 Note 中

---

## Phase 2: Foundational(阻塞所有用户故事)

**Purpose**: US1/US2 共用的最小承载。**测试先行**。

- [X] T006 [P] 在 `oryxos-core/src/main/java/io/oryxos/core/tool/OryxTool.java` 建最小接口:`String name()`、`String description()`、`String getInputSchema()`(JSON Schema 字符串);不含 execute,不建 ToolResult(决策1)
- [X] T007 在 `oryxos-core/src/main/java/io/oryxos/core/profile/Profile.java` 建全字段不可变 record(字段/嵌套类型与 data-model.md 一致:name、description、identity(agentName,prompt)、provider(name,model,temperature)、tools、skills、mcpServers、channels、notifyChannels、schedules、bootstrap、settings(maxIterations,maxHistoryTurns)),列表缺省为空列表(Profile 被 ProviderService 签名依赖,故置于 Foundational)
- [X] T008 [P] 在 `oryxos-storage/src/main/resources/schema.sql` 写 `llm_calls` 手工建表脚本(`CREATE TABLE IF NOT EXISTS`):id INTEGER PK AUTOINCREMENT、session_id TEXT NOT NULL(建索引)、provider TEXT NOT NULL、model TEXT NOT NULL、prompt_tokens/completion_tokens/total_tokens INTEGER 可空、duration_ms INTEGER NOT NULL、success INTEGER NOT NULL(0/1)、error_message TEXT、created_at TEXT NOT NULL(ISO-8601)
- [X] T009 [P] 编写 `oryxos-storage/src/test/java/io/oryxos/storage/LlmCallRepositoryTest.java`:用 `spring.sql.init.schema-locations=classpath:schema.sql` + `spring.jpa.hibernate.ddl-auto=none` 建表(**不得让 Hibernate 自动建表**);测试存/读一条成功记录与一条失败记录;用 `PRAGMA table_info(llm_calls)` 断言 `success`、`error_message` 两列真实存在
- [X] T010 在 `oryxos-storage/src/main/java/io/oryxos/storage/LlmCall.java` 与 `LlmCallRepository.java` 实现 JPA 实体(`@Table(name="llm_calls")`,列映射同 T008,`ddl-auto` 不参与)与 `JpaRepository<LlmCall, Long>`;跑 T009 转绿
- [X] T011 在 `oryxos-provider/src/main/java/io/oryxos/provider/LlmCallAuditor.java` 实现 `record(String sessionId, String provider, String model, Usage usage, boolean success, String errorMessage, long durationMs)`:`usage` 可为 null 时 token 列为空;写库自身异常捕获后记 ERROR 日志,不得掩盖调用方的原始异常
- [X] T012 在 `oryxos-core` 或 `oryxos-provider` 新增 `ProviderNotFoundException`(位置以既有 `BizException`/`ErrorCode` 风格为准,先读 `oryxos-core/.../ErrorCode.java` 与 `BizException.java` 再定;消息含 provider 名称);若需新增 ErrorCode 枚举值属已定字面量扩展,先停下确认

**Checkpoint**: Foundation 就绪,`./mvnw -pl oryxos-storage,oryxos-core test` 绿

---

## Phase 3: User Story 1 - 按 Agent 配置路由到指定的大模型 (Priority: P1) 🎯 MVP

**Goal**: `chat(sessionId, Profile, Prompt)` 按名显式路由,关闭自动工具执行,工具只翻译不执行

**Independent Test**: mock 两个 `ChatModel`,指定 kimi 的 Profile 调用后 kimi 1 次、deepseek 0 次;未知名抛 `ProviderNotFoundException`;带工具调用时自动执行关闭且 schema 已带上

### Tests for User Story 1(先写,先红)

- [X] T013 [P] [US1] 编写 `oryxos-provider/src/test/java/io/oryxos/provider/ToolSchemaAdapterTest.java`:OryxTool 的 name/description/inputSchema 翻译为 Spring AI `ToolDefinition` 后字段一一对齐;产物是纯定义,不含任何可执行回调
- [X] T014 [P] [US1] 编写 `oryxos-provider/src/test/java/io/oryxos/provider/ProviderServiceTest.java` 中的 `按名路由_两个provider不串台()`(课件原样:`new ProviderService(Map.of("deepseek", deepseek, "kimi", kimi), adapter, audit)`,kimi `times(1)`,deepseek `never()`)与 `未知provider名_抛ProviderNotFoundException()`
- [X] T015 [P] [US1] 在同一测试类编写 `带工具schema调用_请求里关闭了自动执行()`(课件原样,ArgumentCaptor 捕获传给 `ChatModel.call` 的 Prompt,断言 internalToolExecutionEnabled 为 false 且工具定义非空;方法名按 T005 核实的真实 API 调整断言写法,不改守点)

### Implementation for User Story 1

- [X] T016 [US1] 在 `oryxos-provider/src/main/java/io/oryxos/provider/ToolSchemaAdapter.java` 实现 `List<ToolDefinition> toSpringAiTools(List<OryxTool>)`,只翻译,跑 T013 转绿
- [X] T017 [US1] 在 `oryxos-provider/src/main/java/io/oryxos/provider/ProviderService.java` 实现 `chat`:构造入参 `(Map<String, ChatModel>, ToolSchemaAdapter, LlmCallAuditor)`;先按名取模型,取不到抛 `ProviderNotFoundException`;请求选项关闭 `internalToolExecutionEnabled`;`chatModel.call(prompt)`;模型的工具调用请求原样随 `ChatResponse` 返回;方法首行留注释位"24 节接线:HTTP 域名白名单";跑 T014、T015 转绿
- [X] T018 [P] [US1] 在 `oryxos-provider/src/main/java/io/oryxos/provider/ProviderProperties.java` 实现 `oryxos.providers[]`(name、base-url、api-key)的 `@ConfigurationProperties` 绑定
- [X] T019 [US1] 在 `oryxos-provider/src/main/java/io/oryxos/provider/ProviderConfiguration.java` 手工构建 `Map<String, ChatModel>`:name 为空/重复、api-key 为空(`${XXX_API_KEY:}` 空默认)时记 ERROR 日志(含 provider 名与缺失变量)并跳过,不阻断启动;不注入 `List<ChatModel>`,不靠类型扫描;并在 `ProviderServiceTest` 补 `缺key的provider_被跳过且不阻断()` 用例(针对配置构建方法的单测)
- [X] T020 [US1] 在 `oryxos-boot/src/main/resources/application.yaml` 增加 `spring.autoconfigure.exclude`(排除 Spring AI OpenAI 等模型自动装配类,类名以 T005 核实为准)与 `oryxos.providers` 全局层示例(deepseek/kimi,key 使用 `${DEEPSEEK_API_KEY:}`/`${KIMI_API_KEY:}`,不落明文);并确认 `spring.sql.init` 在生产执行 `schema.sql`(`mode: always`,`schema-locations: classpath:schema.sql`),跑 `OryxOsApplicationTest` 仍绿

**Checkpoint**: US1 可独立演示:`./mvnw -pl oryxos-provider -am test` 路由/未知名/自动执行关闭 三项绿

---

## Phase 4: User Story 2 - 每次模型调用留审计,失败同样留痕 (Priority: P1)

**Goal**: 成败都落 `llm_calls`,失败后异常原样上抛

**Independent Test**: 模拟成功与失败各一次,检查审计参数与异常

### Tests for User Story 2

- [X] T021 [P] [US2] 在 `ProviderServiceTest` 编写 `调用失败_审计必须留下success为false的记录()`(课件原样:`thenThrow(new RuntimeException("connect timeout"))`,`assertThrows`,`verify(audit).record(eq("s-1"), eq("deepseek"), any(), isNull(), eq(false), contains("timeout"), anyLong())`)
- [X] T022 [P] [US2] 在 `ProviderServiceTest` 编写 `调用成功_审计记录success为true且含token与耗时()`、`审计写入自身失败_不掩盖原始调用异常()`(后者对应 spec 边缘情况)
- [X] T023 [P] [US2] 编写 `LlmCallAuditorTest`(`oryxos-provider/src/test/java/io/oryxos/provider/LlmCallAuditorTest.java`):usage 为 null 时 token 列为空不报错;repository 抛异常时 record 不向外抛

### Implementation for User Story 2

- [X] T024 [US2] 在 `ProviderService.chat` 补审计:成功分支 `record(..., true, null, ...)`;`catch (RuntimeException e)` 先 `record(..., false, e.getMessage(), ...)` 再 `throw e`(不吞异常);耗时用 `System.currentTimeMillis()` 差值;跑 T021~T023 转绿
- [X] T025 [P] [US2] 编写 `oryxos-provider/src/test/java/io/oryxos/provider/ProviderSmokeIT.java`,打 `@Tag("integration")`:读环境变量真 key(缺失则 `Assumptions.assumeTrue` 跳过),真调一次,断言响应非空且 `llm_calls` 多一条 `success=true`;确认 surefire 默认排除 `integration` 标签、`-Dgroups=integration` 可手动触发(必要时在 `oryxos-provider/pom.xml` 配 surefire,不改根 pom 已固定版本)

**Checkpoint**: US1+US2 绿;审计"成败对称"证据齐

---

## Phase 5: User Story 3 - 启动加载 Agent 配置,坏配置不拖垮整体 (Priority: P2)

**Goal**: 扫描 `.oryxos/profiles/`,合法的入索引,坏的记日志不阻断

**Independent Test**: 临时目录放合法、引用不存在 provider、格式损坏三类文件,执行 `load()`

### Tests for User Story 3

- [X] T026 [US3] 编写 `oryxos-core/src/test/java/io/oryxos/core/profile/ProfileLoaderTest.java`,覆盖(中文方法名):`合法YAML_全字段解析()`、`引用不存在的provider_报错清晰且不入索引()`(断言日志/错误信息含文件名与 provider 名)、`坏文件不阻断其余加载()`、`ENV占位从环境变量解析()`(注入 env 函数)、`ENV变量缺失_该Profile被跳过并记错误()`、`同名Profile_后者不覆盖前者()`、`目录不存在_索引为空不报错()`

### Implementation for User Story 3

- [X] T027 [US3] 在 `oryxos-core/src/main/java/io/oryxos/core/profile/ProfileRegistry.java` 实现按 name 查找的内存索引(`find(name): Optional<Profile>`、`all()`;同名拒绝覆盖;仅启动扫描一条注册路径,**不加运行时 register() 公共方法**,第 29 节再补)
- [X] T028 [US3] 在 `oryxos-core/src/main/java/io/oryxos/core/profile/ProfileLoader.java` 实现:`SafeConstructor` 读 YAML 为 Map → 手工映射为 `Profile` record(蛇形键 `mcp_servers`/`notify_channels`/`max_iterations`/`max_history_turns`);`${NAME}`/`${NAME:default}` 经注入的 `Function<String,String> env` 解析,缺失且无默认→该 Profile 失败;校验 `provider.name ∈ knownProviders`;每个文件独立 try/catch,ERROR 日志含文件名与原因,不上抛、不阻断;跑 T026 转绿
- [X] T029 [US3] 在 `oryxos-boot` 内以最小 `@Configuration`/`ApplicationRunner` 完成启动接线(取 `ProviderService`/`ProviderConfiguration` 的 provider 名集合作为 `knownProviders`,目录取 `.oryxos/profiles/`);该类为 Spring 接线,不新增对外概念;`OryxOsApplicationTest` 保持绿
- [X] T030 [P] [US3] 新增示例 Profile `.oryxos/profiles/ops-agent.yaml`(课件示例:provider.name=deepseek、model=deepseek-chat、temperature=0.7,**无任何密钥**)作为演示与冒烟素材

**Checkpoint**: 三个故事均可独立验证

---

## Phase 6: Polish & 节级收尾

- [X] T031 [P] 跑 `./mvnw spotless:apply`,再 `./mvnw clean verify`,逐项处理 Checkstyle/PMD+P3C/SpotBugs+FindSecBugs 报告(SpotBugs 排除项须写明理由,不得靠放宽阈值过关)
- [X] T032 H4 六条自查并记录证据:①涉外 IO 首行过 Sandbox(本节留 24 节接线注释位)②成败都落 `llm_calls`③`grep -rn "sk-"` 无明文 key④`session_id` 本节只透传、无拼接⑤无 Reactor/`CompletableFuture`/自建线程池⑥无 Spring AI 自动工具执行路径(回归测试在 T015)
- [X] T033 按 `quickstart.md` 复核依赖解析、单测、冒烟命令;生成 `specs/001-agent-provider/lesson-report.md`(六项证据 + 剩余人工项:真模型冒烟、BOM 依赖在 CI 联网解析、`./mvnw verify -Psecurity` 新依赖 CVE 检查、key 无明文 grep)

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → {US1, US2} → US3 → Polish。
- US1 与 US2 同改 `ProviderService`/`ProviderServiceTest`:先 US1 后 US2(T017 → T024)。
- US3 仅依赖 T007(Profile)与 T012 之后的 `knownProviders` 来源(T019),可在 US1 完成后与 US2 并行。
- 测试先行:T009→T010、T013/T014/T015→T016/T017、T021~T023→T024、T026→T027/T028。

### Parallel Opportunities

- Phase 1:T004 与 T002/T003 并行。
- Phase 2:T006、T008、T009 并行;
- US1:T013、T014、T015 并行(不同方法但同文件的 T014/T015 需合并后提交,编辑串行);T018 并行于 T016/T017。
- US2:T021、T022、T023 并行(T021/T022 同文件)。
- US3:T030 与 T026~T028 并行。

## Implementation Strategy

- **MVP = Phase 1 + 2 + US1**(能稳定调通一次、路由不串台、自动执行关闭),随后 US2(审计失败路径),最后 US3。
- 每个 checkpoint 跑对应模块测试,红了当场修;全部完成后以 `./mvnw clean verify` 全绿为"实现完成"。

## Notes

- 已知软门禁待确认项:`llm_calls` 列(T008/T010)、`provider→storage` 模块依赖(T002)、新增 Spring 接线类 `ProviderProperties`/`ProviderConfiguration`/`LlmCallAuditor`/`ProviderNotFoundException`、boot 接线类(T029)。

## 实现偏差记录(逐项,供复核)

| 任务 | 偏差 | 原因 / 授权 |
|---|---|---|
| T005 / T016 | `ToolSchemaAdapter.toSpringAiTools` 返回 `List<ToolCallback>`(仅含 schema,`call()` 拒绝),而非 `List<ToolDefinition>` | Spring AI 1.1.2 请求选项只接受 `ToolCallback`(javap 实测),见 research「T005 核实结论」 |
| T017 | 新增 4 参重载 `chat(sessionId, Profile, Prompt, List<OryxTool>)`;3 参保留且等价于无工具;新增 `providerNames()` 供启动校验 | 课件 `Prompt.getAvailableTools()` 是自建类型的伪代码,Spring AI `Prompt` 没有;用户已选"加 4 参重载,3 参保留" |
| T012 | `ProviderNotFoundException` 放 `oryxos-provider`,继承 `BizException`,复用 `ErrorCode.NOT_FOUND`,**未新增枚举值** | analyze 报告 I1 |
| T003 | `oryxos-core/pom.xml` 增加 `snakeyaml`、`slf4j-api`(版本均由现有 BOM 管理) | analyze 报告 I2 |
| T019 | "缺 key/重复名/空 base-url/构建失败"单测放在独立的 `ProviderConfigurationTest`(6 个),而非 `ProviderServiceTest` | 被测对象是 `ProviderConfiguration`,放一起会跨类职责 |
| T022 | "审计写入自身失败不掩盖原异常"用真实 `LlmCallAuditor` + 抛错的 mock Repository 验证,与 `LlmCallAuditorTest` 的单点断言互补 | 端到端更能证明异常不被掩盖 |
| T020 / C1 | `OryxOsApplication` 增加 `@EntityScan`/`@EnableJpaRepositories("io.oryxos.storage")` | analyze 报告 C1:实体不在启动类包下,否则 `LlmCallRepository` 装配失败;用户在 tasks 确认时已被告知 |
| 全部测试类 | 测试方法名改 ASCII 驼峰,课件中文方法名原文保留在 `@DisplayName` | Checkstyle `MethodName`/`AbbreviationAsWordInName` 禁止中文方法名;用户选择"改 ASCII + 中文 @DisplayName",未改动任何门禁配置 |
| T025 | `ProviderSmokeIT` 加类级 `@SuppressWarnings("checkstyle:AbbreviationAsWordInName")` | 类名 `ProviderSmokeIT` 是课件固定字面量,不能改名;就地豁免并写明理由,不动全局配置 |
| T025 | 手动冒烟命令为 `-Dexcluded.test.groups= -Dgroups=integration`(provider pom 默认排除 `integration`) | surefire 默认不拾取 `*IT`,在 provider pom 显式配置 includes 与 excludedGroups |
| 额外 | `ProfileLoader` 的 `${ENV}` 解析用手写解析替代正则 | FindSecBugs `REDOS` 告警,按"改代码不加排除项"处理 |
| 额外 | `ProviderService` 用 `catch (Exception e) { ...; throw e; }` 精确重抛 | 避开 SpotBugs `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION`;`model.call` 只抛非受检异常,语义与 `catch RuntimeException` 一致 |
