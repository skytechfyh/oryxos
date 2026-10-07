---

description: "第18节 CLI 命令行入口与会话层任务清单"
---

# Tasks: CLI 命令行入口与会话层(会话持久化、chat 交互、12 个子命令)

**Input**: `specs/003-cli-session/` 下的 plan.md、spec.md、research.md、data-model.md、contracts/(session-manager.md、cli-commands.md)、quickstart.md

**Prerequisites**: plan「待确认事项」A~E 已由用户确认(B 选推迟到第20节,不加兜底类型)

**Tests**: 课件"验收 harness"两个测试类 `SessionManagerTest`、`SessionRepositoryTest` **原样落地且先于对应实现**;课件关键用例 `同一三元组_历次getOrCreate都是同一个Session` 方法名与断言原样保留;另加 `CliChannelTest`(plan 测试策略)与 `OryxOsApplicationTest` 的 JPA 扫描断言。命令分流与 `--help` 不写自动化测试(留人工)。

**Organization**: 按 spec 三个用户故事分组:US1 会话层(P1,地基)、US2 终端对话(P1)、US3 12 个子命令与分流(P2)。

## Format: `[ID] [P?] [Story] Description`

- 代码风格:Google 格式 + 阿里 P3C,**每个方法(含 private、构造器、测试方法)带中文 Javadoc,类带中文类注释**;避开 Java 18+ 增强 switch `default ->`;每个任务完成后跑该模块测试,红了当场修。
- 构建一律 `./mvnw`;全程不 commit / push;不新增 plan 未列明的公共类型、配置键、表;`session_id` 拼接只许出现在 `JpaSessionManager`。
- 反作弊:不删断言、不 `@Disabled`、不放宽阈值。

---

## Phase 1: Setup(依赖与前置核实)

- [X] T001 在 `oryxos-channel-cli/pom.xml` 增加测试依赖 `spring-boot-starter-test`(test scope,无版本,与 core 一致);在 `oryxos-cli/pom.xml` 增加依赖 `org.springframework.boot:spring-boot`(无版本,BOM 已管理)、`oryxos-channel-cli`、`spring-boot-starter-test`(test scope);**不依赖 `oryxos-boot` 与 `oryxos-storage`**。执行 `./mvnw -pl oryxos-cli,oryxos-channel-cli,oryxos-storage -am dependency:tree`,确认版本不变、无 Tomcat/log4j 等已固定组件回退、storage 经 core 能解析到 Jackson(`jackson-databind`)与 `spring-ai-model`;若 storage 解析不到 Jackson,按软门禁停下报告
- [X] T002 核实本地 API(H3,核实不到按软门禁停下):Spring AI 1.1.8 的 `UserMessage`/`AssistantMessage`(含 `getToolCalls()`、`AssistantMessage.ToolCall(id,type,name,arguments)` 构造)/`ToolResponseMessage` 及其 `ToolResponse(id,name,responseData)` 的构造与取值方法;Picocli 4.7.7 的 `@Command(subcommands)`、`mixinStandardHelpOptions`、`@Unmatched`、`CommandLine.execute`;结论追加到 `specs/003-cli-session/research.md` 末尾 Note
- [X] T003 [P] 核对 `oryxos-core` 现有 `BizException`/`ErrorCode`(已有 `BAD_REQUEST`/`NOT_FOUND`)满足空白身份、`save` 传入非法 Session、Profile 不存在三类报错;若需新增错误码按软门禁停下
- [X] T004 [P] 核对 `oryxos-boot` 的 `OryxOsApplication` 已声明 `@EntityScan("io.oryxos.storage")` 与 `@EnableJpaRepositories("io.oryxos.storage")`(课件约定),并读 `OryxOsApplicationTest` 的现有写法,为 T027 的扩充断言做准备

---

## Phase 2: Foundational(阻塞所有用户故事)

**Purpose**: 会话契约扩展与建表,US1/US2 共用。**`save` 签名与 `Session` 接口不动**。

- [X] T005 在 `oryxos-core/src/main/java/io/oryxos/core/session/SessionManager.java` 按 `contracts/session-manager.md` **只新增** `Session getOrCreate(String channel, String user, String profileName)` 与 `Optional<Session> get(String sessionId)`;更新类注释(去掉"第18节补全"措辞,改为描述三方法契约及"session_id 只在实现内部拼接");同时 grep 全仓所有 `SessionManager` 实现/匿名类/Mockito 之外的手写 fake(尤其 `AgentServiceTest`、`ReActLoopTest`),为其补上新方法的桩实现,**仅改夹具、不放宽任何断言**
- [X] T006 在 `oryxos-storage/src/main/resources/schema.sql` 追加 `sessions` 表(`CREATE TABLE IF NOT EXISTS`,列严格按 data-model.md:`session_id TEXT PRIMARY KEY`、`profile_name TEXT NOT NULL`、`channel TEXT NOT NULL`、`user_id TEXT NOT NULL`、`messages_json TEXT NOT NULL`、`status TEXT NOT NULL`、`created_at TEXT NOT NULL`、`last_active_at TEXT NOT NULL`、`archived_at TEXT` 可空),头部注释写明用途;不碰 `db/audit-tables.sql`(核对其是否被引用,若是重复遗留文件只在报告中提示,不改)

**Checkpoint**: `./mvnw -pl oryxos-core,oryxos-storage -am test` 仍全绿(前序节回归)。

---

## Phase 3: User Story 1 - 同一身份始终落到同一个会话,且历史不丢 (Priority: P1) 🎯 MVP

**Goal**: 会话管理按三元组幂等获取/创建,历史整段落库、回读完整、重启不丢。

**Independent Test**: 只用 `SessionManager`:同身份连取两次同一 id;换一项得不同 id;保存带历史会话后新建 context 再读,历史完整。

### Tests(先于实现)

- [X] T007 [P] [US1] 在 `oryxos-storage/src/test/java/io/oryxos/storage/SessionManagerTest.java` 写测试(`@DataJpaTest`,口径同 `ToolInvocationRepositoryTest`:内存 SQLite、`ddl-auto=none`、`schema.sql`,并 `@Import(JpaSessionManager.class)`):课件原样 `同一三元组_历次getOrCreate都是同一个Session`(`first.id()==second.id()`;`web` 渠道得不同 id);另含 user 不同/profile 不同得不同会话;空白或 null 的任一项抛 `BizException`;`get` 不存在返回 empty;**身份含 `:` 与 `%` 时不碰撞**(`("a:b","c")` 与 `("a","b:c")` id 不同);同身份重复获取后表内只有一行;新建会话 `status=active`;`save` 传入非实体 Session 抛 `BizException`;**"id 生成只此一处"**:扫描 `oryxos-*/src/main/java` 源码,断言除 `JpaSessionManager` 外无文件含拼接 session_id 的 `":"` 拼接逻辑(以具体可判定的 grep 口径实现,避免空头断言)
- [X] T008 [P] [US1] 在 `oryxos-storage/src/test/java/io/oryxos/storage/SessionRepositoryTest.java` 写测试:脚本建出的 `sessions` 表含 data-model 全部 9 列(`PRAGMA table_info`);存一条读一条字段一致;`messages_json` 往返——含用户消息、带 toolCalls 的 assistant 响应、tool 结果,**消息条数、顺序、内容完全一致**,另含空串、特殊字符与换行;**模拟重启**:用文件型临时 SQLite(`@TempDir`)建第一个 `ApplicationContext` 存会话后关闭,再新建第二个 context 查,历史仍在

### Implementation

- [X] T009 [US1] 在 `oryxos-storage/src/main/java/io/oryxos/storage/SessionMessageCodec.java` 建**包私有**编解码器(`final class`,静态方法,私有构造):`String encode(List<Message>)`、`List<Message> decode(String json)`,格式按 data-model.md「messages_json 元素格式」(`type: user|assistant|tool`,字段缺失回读为空串/空列表);未知 `type` 或 JSON 非法抛 `BizException`(不吞);用 Jackson `ObjectMapper`
- [X] T010 [US1] 在 `oryxos-storage/src/main/java/io/oryxos/storage/Session.java` 建 JPA 实体(`@Entity @Table(name="sessions")`,`implements io.oryxos.core.session.Session` 用全限定名引用),字段与列一一对应(`session_id` 为 `@Id`;时间为 ISO-8601 文本;`status` 取 `active`/`archived`;`archived_at` 可空);`messages()` 惰性解码 `messages_json` 并缓存在 `@Transient`;`append(String)`/`append(ChatResponse)`/`appendToolResult(...)` 改内存列表并语义与 `Session` 接口一致(ToolCall 与结果的对应靠调用 id);提供包内可见的"把内存历史编码回 `messages_json`"方法供 `save` 使用;`protected` 无参构造
- [X] T011 [P] [US1] 在 `oryxos-storage/src/main/java/io/oryxos/storage/SessionRepository.java` 建 `interface SessionRepository extends JpaRepository<Session, String>`(不加自定义查询)
- [X] T012 [US1] 在 `oryxos-storage/src/main/java/io/oryxos/storage/JpaSessionManager.java` 建 `@Component`/`@Service` 实现 `SessionManager`:**私有方法 `buildSessionId(channel, user, profileName)` 是全仓唯一拼接处**——`enc(channel)+":"+enc(user)+":"+enc(profile)`,`enc` 只转义 `%`→`%25`、`:`→`%3A`;任一项 null/空白抛 `BizException(BAD_REQUEST)`;`getOrCreate` 在 `@Transactional` 内先查后建,新建 `status=active`、`messages_json="[]"`、时间戳当前时刻,遇 `DataIntegrityViolationException` 回读已存在行;`get` 返回 `Optional`;`save` 校验入参为实体、编码历史回写、刷新 `last_active_at`,失败上抛不吞
- [X] T013 [US1] 跑 `./mvnw -pl oryxos-storage -am test`,`SessionManagerTest`/`SessionRepositoryTest` 及既有 `LlmCallRepositoryTest`/`ToolInvocationRepositoryTest` 全绿

**Checkpoint**: 会话层独立可演示(US1 完成即为 MVP)。

---

## Phase 4: User Story 2 - 在终端里和 Agent 多轮对话 (Priority: P1)

**Goal**: `CliChannel` 实现读—转交—打印循环,`ChatCommand` 把它接到重命令启动流程上。

**Independent Test**: 用脚本化输入与 mock 的 `AgentService`:每行转交、回复被打印、`/quit` 不转交且结束、EOF 正常结束、引擎抛错打印提示后继续。

### Tests(先于实现)

- [X] T014 [P] [US2] 在 `oryxos-channel-cli/src/test/java/io/oryxos/channel/cli/CliChannelTest.java` 写测试(Mockito mock `AgentService`/`SessionManager`/`ProfileRegistry`,`ByteArrayInputStream` 作 stdin,`ByteArrayOutputStream` 捕获 stdout/stderr):多行输入逐行转交且回复逐条打印;`/quit` 与 `  /quit  `(首尾空白)结束且**不**调用 `process`;EOF 无 `/quit` 正常返回;`process` 抛 `BizException` 时向用户打印错误提示并继续下一行(且不抛出);Profile 不存在时 `run` 立即报错返回非零结果且不进入循环、不调用 `process`;`SessionManager.getOrCreate` 以 `("cli", 用户, profile)` 调用一次

### Implementation

- [X] T015 [US2] 在 `oryxos-channel-cli/src/main/java/io/oryxos/channel/cli/CliChannel.java` 建类:构造器注入 `AgentService`、`SessionManager`、`ProfileRegistry`、`InputStream`、`PrintStream`(输出)、`PrintStream`(错误);`int run(String profileName, String user)`:先查 Profile(不存在打印中文错误返回 1),再 `sessionManager.getOrCreate("cli", user, profileName)`,循环 `BufferedReader.readLine()`:null(EOF)或 `/quit`(trim 后)即退出返回 0;空行跳过;其余交 `agentService.process(session, line)` 并打印回复;`process` 抛异常时打印错误到错误流并记 SLF4J,继续下一行。**CLI 唯一自有逻辑是退出判断**;不做任何 Agent 逻辑;用户输出只走注入的 PrintStream
- [X] T016 [US2] 在 `oryxos-cli/src/main/java/io/oryxos/cli/ChatCommand.java` 建 `@Command(name="chat")`(`mixinStandardHelpOptions`),`--profile` 默认 `default`;**重命令**:通过 `OryxOsCli` 注入的启动函数以 `--spring.main.web-application-type=none` 启动 Spring,取 `AgentService`/`SessionManager`/`ProfileRegistry` Bean 组装 `CliChannel`(`System.in`/`System.out`/`System.err` 只在此装配处出现一次),当前用户取 `System.getProperty("user.name")`,结束时关闭 context 并以 `run` 的返回值作退出码
- [X] T017 [US2] 跑 `./mvnw -pl oryxos-channel-cli,oryxos-cli -am test`,`CliChannelTest` 全绿

---

## Phase 5: User Story 3 - 12 个子命令与"轻重分流" (Priority: P2)

**Goal**: `OryxOsCli` 注册 12 个子命令,轻命令不起 Spring,重命令才起;`OryxOsApplication.main` 委托 CLI。

**Independent Test**: 进程级行为留人工验收(`quickstart.md` 人工清单);自动化仅保证编译、静态检查与 `OryxOsApplicationTest` 的 JPA 扫描断言。

- [X] T018 [US3] 在 `oryxos-cli/src/main/java/io/oryxos/cli/OryxOsCli.java` 建根命令 `@Command(name="oryxos", mixinStandardHelpOptions=true, subcommands=…)`,按 `contracts/cli-commands.md` 注册 12 个子命令(`profile` 为父命令含 list/create/show/delete 四个子命令);`public static int run(String[] args, Function<String[], ConfigurableApplicationContext> contextStarter)`:用 Picocli `IFactory` 把启动函数注入重命令,`new CommandLine(...).execute(args)` 返回退出码;无子命令时打印帮助;**不手写参数解析**
- [X] T019 [P] [US3] 在 `oryxos-cli/src/main/java/io/oryxos/cli/InitCommand.java` 建轻命令:创建 `.oryxos/` 全结构(`profiles/`、`memory/MEMORY.md`、`skills/`、`mcp_servers.yaml`、`sessions/`、`logs/`、`AGENTS.md`、`SOUL.md`、`USER.md`)并写默认模板与默认 Profile(`default.yaml`,仅用既有 Profile 字段,不引入新字段);已存在的文件不覆盖;写文件处注释"第24节接 SandboxChecker";抽出包私有的工作区路径/名称校验小工具类供 profile 命令复用(名称仅允许 `[A-Za-z0-9_-]+`,拒绝路径分隔与 `..`)
- [X] T020 [P] [US3] 在 `oryxos-cli/src/main/java/io/oryxos/cli/StatusCommand.java` 建轻命令:打印工作区是否存在、Profile 文件数、数据库文件(`ORYXOS_DB_PATH`,默认 `./oryxos.db`)是否存在
- [X] T021 [P] [US3] 在 `oryxos-cli/src/main/java/io/oryxos/cli/ProfileCommand.java` 建父命令与 `ProfileListCommand`、`ProfileCreateCommand`、`ProfileShowCommand`、`ProfileDeleteCommand`(轻,直接读写 `.oryxos/profiles/`):list 列文件名;create 写最小模板(已存在报错);show 打印原文(不存在报错);delete 删除(不存在报错);名称经 T019 的校验;错误输出到 stderr、退出码按 contract
- [X] T022 [P] [US3] 在 `oryxos-cli/src/main/java/io/oryxos/cli/ProviderListCommand.java` 建轻命令:用 snakeyaml 读 classpath `application.yaml` 的 `oryxos.providers`,只打印 `name` 与 `base-url`,**不打印任何 key**;读不到时输出明确提示
- [X] T023 [P] [US3] 在 `oryxos-cli/src/main/java/io/oryxos/cli/ToolListCommand.java` 建轻命令:输出"尚无已注册工具(完整注册表由第20节交付)"占位
- [X] T024 [P] [US3] 在 `oryxos-cli/src/main/java/io/oryxos/cli/SessionListCommand.java` 建轻命令:JDBC 只读(`DriverManager`,参数化/无拼接 SQL)查询 `sessions`,列 `session_id`、`profile_name`、`channel`、`user_id`、`status`、`last_active_at`;库文件或表不存在输出"暂无会话";其余 `SQLException` 打印错误并以非零退出,不吞
- [X] T025 [P] [US3] 在 `oryxos-cli/src/main/java/io/oryxos/cli/ServeCommand.java` 与 `GatewayCommand.java` 建重命令骨架:`serve` 用 `@Unmatched` 接收 `--spring.*` 参数透传给启动函数,启动后阻塞等待(不自建线程池,用 context 关闭/`Thread` 阻塞等待的同步方式);`gateway` 启动并阻塞,不含 IM 通道与 Web 细节
- [X] T026 [US3] 修改 `oryxos-boot/src/main/java/io/oryxos/boot/OryxOsApplication.java`:`main` 改为 `System.exit(OryxOsCli.run(args, a -> SpringApplication.run(OryxOsApplication.class, a)))`(保留类名与两个扫描注解及其注释,`OryxOsApplication` 的 Javadoc 补"`main` 委托 `OryxOsCli`");**勿因委托而破坏 `OryxOsApplicationTest` 的 Spring 启动路径**
- [X] T027 [US3] 在 `oryxos-boot/src/test/java/io/oryxos/boot/OryxOsApplicationTest.java` 扩充一条断言:`SessionRepository` 与 `JpaSessionManager` Bean 存在(证明 `@EnableJpaRepositories`/`@EntityScan` 覆盖到 storage,对应 FR-004);既有断言不动
- [X] T028 [US3] 同步文档(plan 待确认事项 A):`README.md` 第 63-64 行与 `CLAUDE.md` 第 11 行的启动命令改为 `java -jar … serve [--spring.profiles.active=prod]`,并在 CLAUDE.md「模块」一句保持与实际一致(不改架构原则条款)
- [X] T029 [US3] 跑 `./mvnw -pl oryxos-cli,oryxos-boot -am test`,全绿

---

## Phase 6: Polish & Cross-Cutting

- [X] T030 [P] `ToolRegistry` Bean 处置(用户已确认选①):**不加兜底类型**;在验收报告人工项里写明 `chat` 真跑待第20节交付 `ToolRegistry` 后再验,自动化测试用 mock 不受影响
- [X] T031 [P] 更新 `AgentConfiguration` 类注释中"`SessionManager` 实现由第18节交付"的过时措辞(现已由 `JpaSessionManager` 提供),不改任何 Bean 定义
- [X] T032 `./mvnw clean verify` 全绿(Spotless → Checkstyle → PMD+P3C → SpotBugs/FSB);SpotBugs 如需排除必须在 `config/spotbugs-exclude.xml` 写明理由
- [X] T033 抽查本节新增/修改的全部 Java 文件:**每个方法(含 private、构造器、测试方法)有中文 Javadoc,类有中文类注释**,缺失当场补齐
- [X] T034 H4 自查:grep 无明文 key;无 Reactor/`CompletableFuture`/自建线程池;无 Spring AI 自动工具执行路径;`session_id` 拼接只在 `JpaSessionManager`;涉外 IO 留 24 节 Sandbox 接线位注释
- [X] T035 产出验收报告 `specs/003-cli-session/lesson-report.md`(六项证据 DoD + 课件"做完怎么验"剩余人工项清单)

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → US1(Phase 3)→ US2(Phase 4)→ US3(Phase 5)→ Polish。
- US2 依赖 Phase 2 的 `SessionManager` 新契约,但 `CliChannelTest` 用 mock,**可与 US1 并行**;US3 的 `ChatCommand` 接线依赖 US2 的 `CliChannel`。
- 每个故事内:测试(T007/T008、T014)先于实现;`SessionMessageCodec`(T009)→ 实体(T010)→ `JpaSessionManager`(T012)。

## Parallel Opportunities

- Phase 1:T003、T004 并行。
- US1:T007 与 T008 并行(不同文件);T011 与 T009 并行。
- US2:T014 可与 US1 实现并行。
- US3:T019~T025 彼此不同文件,基本可并行(T021 依赖 T019 的名称校验工具,T019 先行)。

## Implementation Strategy

- **MVP = US1**:会话层独立可演示、可判卷(`mvn test`)。
- 增量:US2 打通"终端说上话"→ US3 补全 12 命令与分流。
- 每个故事结束跑该模块及上游模块测试,最后全量 `clean verify` 做节级门禁。

## 课件交付物 ↔ 任务对账

| 交付物 | 任务 |
|---|---|
| `OryxOsCli` 主入口 | T018、T026 |
| 12 个 `@Command` 子命令类 | T016、T019~T025 |
| `CliChannel` | T015 |
| `Session` 实体 + `SessionRepository` | T010、T011 |
| `SessionManager`(接口新增 + 实现) | T005、T012 |
| `SessionManagerTest`、`SessionRepositoryTest` | T007、T008 |
| `sessions` 表手工脚本 | T006 |
| 约定:轻命令不起 Spring;重命令显式 `@EnableJpaRepositories`/`@EntityScan` | T016/T018/T019~T025;T004、T026、T027 |
