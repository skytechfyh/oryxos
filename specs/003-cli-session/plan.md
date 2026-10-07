# Implementation Plan: CLI 命令行入口与会话层(第18节)

**Branch**: `003-lesson18-cli` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-cli-session/spec.md`

## Summary

交付两样东西:① **会话层**——`SessionManager` 在第17节最小契约(仅 `save`)上**新增** `getOrCreate` / `get`,`Session` JPA 实体 + `SessionRepository` + 实现类落在 `oryxos-storage`,`sessions` 表追加到手工建表脚本;`session_id` 只在实现类内部按"渠道+用户+Agent 名"生成。② **CLI 入口**——`OryxOsCli`(Picocli)注册 12 个子命令,`CliChannel` 承载 chat 的"读—转交—打印"循环;命令分轻重两类,轻命令不启动 Spring,重命令(`chat`/`serve`/`gateway`)才启动,启动类显式声明 JPA 扫描包。

技术路线见 [research.md](./research.md)。

## Technical Context

**Language/Version**: Java 21(虚拟线程),Spring Boot 3.5.16

**Primary Dependencies**: Picocli 4.7.7(根 pom 已管理)、Spring Data JPA、sqlite-jdbc 3.53.4.0 + hibernate-community-dialects(storage 已有);Spring AI 仅作 `Message` 数据类型(core 已依赖 `spring-ai-model`),不引入任何 Agent 抽象

**新增依赖(均为 BOM 已管理、版本不变,无 CVE 门禁变化)**:
- `oryxos-cli` → `spring-boot`(仅为 `ConfigurableApplicationContext` / 重命令拿 Bean)、`oryxos-channel-cli`、`oryxos-storage` **不依赖**(见下"依赖方向")
- `oryxos-cli`、`oryxos-channel-cli` → `spring-boot-starter-test`(test scope,与 core 一致)
- `oryxos-storage` → Jackson(若 `dependency:tree` 显示 `spring-ai-model` 已传递带入则不加);`spring-boot-starter-test`(test scope)

**Storage**: SQLite + Spring Data JPA,`ddl-auto=none`,`schema.sql` 手工建表

**Testing**: JUnit 5 + AssertJ + Mockito;`@DataJpaTest`(沿用 `ToolInvocationRepositoryTest` 口径);集成冒烟打 `@Tag("integration")`

**Target Platform**: 单 fat JAR,`java -jar`

**Project Type**: Maven 多模块单体(CLI + 内核)

**Performance Goals**: 轻命令 < 1s(不起 Spring);重命令承担 Spring 启动耗时(2~4s)

**Constraints**: 同步阻塞,无 Reactor/CompletableFuture/自建线程池;所有 Java 方法含中文 Javadoc;避开 Java 18+ switch `default ->` 语法

**Scale/Scope**: 单机、单 SQLite 连接(`maximum-pool-size: 1`)

### 依赖方向(避免循环)

```text
oryxos-boot ──► oryxos-cli ──► oryxos-channel-cli ──► oryxos-core
     │                                                   ▲
     └──────────► oryxos-storage ────────────────────────┘
```

`oryxos-cli` 不能依赖 `oryxos-boot`(boot 已依赖 cli)。因此重命令启动 Spring 的"启动动作"由 boot 在调用 `OryxOsCli` 时以 `Function<String[], ConfigurableApplicationContext>` 注入(JDK 标准函数类型,不新增 public 类型),`OryxOsApplication.main` 一行委托给 `OryxOsCli`。

## Constitution Check

| 原则 | 结论 |
|---|---|
| I 自实现 ReAct | 不触碰 ReActLoop;CLI 只调 `AgentService.process` ✅ |
| II Spring AI 仅协议/Schema | 本节不调用模型,不引入自动 tool 执行;`Message` 仅作数据类型 ✅ |
| III Provider 显式映射 | 不涉及 ✅ |
| IV SKILL.md 不是 Tool | 不涉及 ✅ |
| V 审计 Day One | 本节不新增审计表;重命令启动必须保证 JPA 扫描到 storage(FR-004),否则审计写不进 ✅ |
| VI 沙箱/凭证 | `init`/`profile create|delete` 的文件写入**限定在 `.oryxos/` 工作区内**并校验名字不含路径分隔(防路径穿越),`SandboxChecker` 属第24节,**留调用位注明24节接线**;凭证不落明文 ✅ |
| VII 同步 + 虚拟线程 | Picocli 同步执行;`serve`/`gateway` 用 `ctx` 阻塞等待,不自建线程池 ✅ |
| VIII 配置即 Agent/状态外置/不依赖自动迁移 | 会话入库;`sessions` 走手工脚本 ✅ |

「技术栈约束」中"日志禁用 `System.out`"针对**日志**。CLI 的交互输出是产品功能而非日志:`CliChannel` 通过构造器注入 `InputStream`/`PrintStream`(便于测试),`System.in`/`System.out` **只在命令层装配处出现一次**,其余一律走 SLF4J。

**Gate 结果**:无违反,无需 Complexity Tracking。设计后复核(Phase 1 之后)同样通过。

## Project Structure

### Documentation (this feature)

```text
specs/003-cli-session/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── cli-commands.md
│   └── session-manager.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks 产出
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/io/oryxos/core/session/
└── SessionManager.java              # 仅新增 getOrCreate / get,save 签名不变

oryxos-storage/src/main/java/io/oryxos/storage/
├── Session.java                     # JPA 实体,implements core 的 Session 接口
├── SessionRepository.java           # JpaRepository<Session, String>
├── JpaSessionManager.java           # SessionManager 实现,session_id 唯一拼接处
└── SessionMessageCodec.java         # 包私有:List<Message> <-> messages_json
oryxos-storage/src/main/resources/schema.sql   # 追加 sessions 表
oryxos-storage/src/test/java/io/oryxos/storage/
├── SessionManagerTest.java
└── SessionRepositoryTest.java

oryxos-channel-cli/src/main/java/io/oryxos/channel/cli/
└── CliChannel.java
oryxos-channel-cli/src/test/java/io/oryxos/channel/cli/
└── CliChannelTest.java

oryxos-cli/src/main/java/io/oryxos/cli/
├── OryxOsCli.java                   # 根命令 + run(args, 启动函数)
├── InitCommand.java  StatusCommand.java  ChatCommand.java
├── ServeCommand.java GatewayCommand.java
├── ProfileCommand.java              # 父命令,子命令为下面四个嵌套类/独立类
├── ProfileListCommand.java  ProfileCreateCommand.java
├── ProfileShowCommand.java  ProfileDeleteCommand.java
├── ProviderListCommand.java  ToolListCommand.java  SessionListCommand.java
└── (package-private 工具:WorkspacePaths 等)

oryxos-boot/src/main/java/io/oryxos/boot/OryxOsApplication.java   # main 改为委托 OryxOsCli
```

**Structure Decision**: 严格按 TechnicalSolution §10 与课件落位表:命令→cli,`CliChannel`→channel-cli,`SessionManager` 接口→core,实体/仓库/实现→storage。`SessionManager` 的实现类不在课件交付物清单中点名,按"`SessionManager` 本身"归入 storage(命名 `JpaSessionManager`),**需用户在 tasks 停点确认**。

## 关键设计决策

1. **会话标识**:`session_id = enc(channel) + ":" + enc(user) + ":" + enc(profile)`,`enc` 对 `%` 与 `:` 做百分号转义,不同三元组不可能碰撞;任一项空白 → 抛 `BizException`(沿用既有错误码,不新增)。拼接代码只在 `JpaSessionManager` 的一个私有方法里。
2. **幂等**:`getOrCreate` 在事务内"先查后建";并发重复建以主键冲突兜底并回读,保证同身份至多一条。
3. **历史序列化**:Spring AI 的 `Message` 层级不适合直接 Jackson 序列化,用包私有 `SessionMessageCodec` 把 User / Assistant(含 toolCalls)/ ToolResponse 映射为简单 JSON 数组;`messages_json` 一列存整段。实体持有 `messagesJson` 列并按需解码出 `List<Message>`;`save` 时编码回写并刷新 `last_active_at`。
4. **轻重分流**:轻命令 = `init`、`status`、`profile *`、`provider list`、`tool list`、`session list`,全部纯文件/JDBC,不起 Spring;重命令 = `chat`、`serve`、`gateway`。`session list` 直接用 JDBC 只读查 `sessions` 表(不新增 `SessionManager` 公共方法)。
5. **重命令启动**:`chat` 以 `--spring.main.web-application-type=none` 启动(不占 8080/8081);`serve`/`gateway` 正常启动并阻塞。`OryxOsApplication` 已声明 `@EntityScan("io.oryxos.storage")` 与 `@EnableJpaRepositories("io.oryxos.storage")`,本节**保留并在测试里断言**,满足"显式声明扫描包"约定。
6. **`serve` 参数透传**:`serve` 用 Picocli `@Unmatched` 接收 `--spring.*` 参数并透传给 Spring,以兼容 `--spring.profiles.active=prod` 用法。

## 待用户确认的偏离/风险(2026-10-07 已确认:A、C~E 接受;B 选①推迟到第20节)

| # | 事项 | 说明 | 建议 |
|---|---|---|---|
| A | **`java -jar` 无参行为变化** | 之前无参即起服务;改为 CLI 后无参打印帮助,起服务需 `serve`。README.md:63-64、CLAUDE.md:11 的命令需同步改 | 接受,文档同步 |
| B | **`chat` 依赖尚未交付的 `ToolRegistry` Bean** | `AgentConfiguration` 注释已说明 `ToolRegistry` 实现属第20节;此前容器里没有该 Bean,`chat` 真跑会在取 `AgentService` 时失败,课件"人工验收:多轮对话"无法完成 | 二选一:① 接受人工项推迟到第20节后;② 本节在 boot 加一个**包私有、`@ConditionalOnMissingBean`** 的空 `ToolRegistry` 兜底(属课件交付物清单外的新类型) |
| C | `JpaSessionManager` 命名 | 课件未点名实现类 | 接受 |
| D | `tool list` 内容 | `ToolRegistry` 契约无"列全部"方法且禁止改签名,第20节才有完整实现 | 本节输出明确的"尚无已注册工具(第20节交付)"占位 |
| E | `serve`/`gateway` 仅为骨架 | 不含 Web Service 细节与 IM 通道 | 照边界执行 |

## Complexity Tracking

无违反项,无需填写。
