# Research: CLI 命令行入口与会话层(第18节)

所有技术背景项均已定论,无遗留 NEEDS CLARIFICATION。

## R1 重命令如何在不依赖 boot 的前提下启动 Spring

- **Decision**: `OryxOsCli.run(String[] args, Function<String[], ConfigurableApplicationContext> contextStarter)`;boot 的 `OryxOsApplication.main` 一行委托,把 `a -> SpringApplication.run(OryxOsApplication.class, a)` 注入。
- **Rationale**: `oryxos-boot → oryxos-cli`,反向引用会形成循环;用 JDK 函数类型注入,不新增 public 类型,也不用反射/ServiceLoader。
- **Alternatives**: ① `Class.forName("io.oryxos.boot.OryxOsApplication")` 反射——脆、绕过编译期检查;② 把 `OryxOsCli` 放进 boot——违反课件落位(→cli);③ ServiceLoader——需新增 public SPI 接口,超出交付物清单。

## R2 `chat` 不应占用 HTTP 端口

- **Decision**: `chat` 启动 Spring 时追加 `--spring.main.web-application-type=none`;`serve`/`gateway` 不加。
- **Rationale**: 终端对话不需要 Tomcat,否则与已运行的 `serve` 抢 8080/8081;参数透传是 Spring Boot 标准做法,无需新增配置键。
- **Alternatives**: 新增 `oryxos.mode` 配置键——属对外概念,课件未列,拒绝。

## R3 会话标识生成与防碰撞

- **Decision**: `enc(channel):enc(user):enc(profile)`,`enc` 仅转义 `%`→`%25`、`:`→`%3A`。
- **Rationale**: 可读(便于审计 `session_id` 关联),且转义后分隔符唯一,不同三元组不会碰撞(spec 边缘情况)。拼接只在 `JpaSessionManager` 一个私有方法。
- **Alternatives**: SHA-256 哈希——不可读、审计不友好;不转义直接拼——`a:b`+`c` 与 `a`+`b:c` 碰撞。
- **校验**: 任一项为 null/空白 → `BizException(BAD_REQUEST)`(沿用既有错误码,不新增)。

## R4 `getOrCreate` 幂等与并发

- **Decision**: `@Transactional` 内 `findById` → 不存在则 `save`;若遇 `DataIntegrityViolationException`(并发竞态)回读返回已存在行。
- **Rationale**: 主键即 `session_id`,天然唯一;SQLite 单连接池已串行化写入,兜底分支处理多进程场景。
- **Alternatives**: 应用层加锁——违背"实例无状态",不引入。

## R5 对话历史序列化

- **Decision**: 包私有 `SessionMessageCodec` 把 `List<Message>` 映射为简单 JSON 数组:`{type: user|assistant|tool, text, toolCalls:[{id,name,arguments}], toolResponses:[{id,name,data}]}`;用 Jackson `ObjectMapper`(经 core → `spring-ai-model` 传递可用,实现时以 `-am` 构建核实)。
- **Rationale**: Spring AI `Message` 是多态层级且部分类无无参构造,直接 Jackson 绑定不可靠;自有 DTO 稳定、可读、版本可控。核心阶段不按条拆表(课件明确)。
- **Alternatives**: 直接序列化 `Message`——反序列化失败风险;按条拆表——课件明确不做。

## R6 实体与接口同名

- **Decision**: JPA 实体取名 `io.oryxos.storage.Session`(课件字面量),`implements io.oryxos.core.session.Session`(全限定名引用接口)。
- **Rationale**: 课件与 §9.2 称其为"Session 实体";不同包同名可共存,保持字面量。
- **Alternatives**: 改名 `SessionEntity`——修改已定字面量,触发软门禁,不采用。

## R7 时间与状态列

- **Decision**: 时间用 ISO-8601 文本(与 `tool_invocations.created_at` 一致,SQLite 无原生时间类型);`status` 取 `active` / `archived` 文本;`archived_at` 可空。
- **Rationale**: 与既有表风格一致,无需新类型转换器。

## R8 `session list` 与 `status` 的数据来源

- **Decision**: 轻命令用 JDBC 只读查 `sessions`(路径取 `ORYXOS_DB_PATH`,默认 `./oryxos.db`);库文件或表不存在时输出"暂无会话"。
- **Rationale**: 不起 Spring 才能秒回;不新增 `SessionManager` 公共方法(超出课件"对外三个方法")。
- **Alternatives**: 重命令走 `SessionRepository`——慢,且需新增查询方法。

## R9 `profile create|show|delete` 的文件操作与安全

- **Decision**: 直接读写 `.oryxos/profiles/<name>.yaml`;`name` 仅允许 `[A-Za-z0-9_-]+`,拒绝路径分隔与 `..`;`create` 写入含课件已有字段(`name`/`description`/`identity`/`provider`/`tools`/`settings`)的最小模板,不引入新 Profile 字段;`delete` 不存在时报错。
- **Rationale**: 防路径穿越(宪法 VI 精神);`SandboxChecker` 属第24节,**留调用位注明24节接线**。
- **Alternatives**: 通过 Spring 的 `ProfileRegistry` 操作——会让轻命令变重,且运行时注册属第29节。

## R10 测试策略

- `SessionManagerTest`(`@DataJpaTest` + 内存 SQLite,沿用 `ToolInvocationRepositoryTest` 口径):课件原样用例 `同一三元组_历次getOrCreate都是同一个Session`;channel/user/profile 任一不同则不同;空白身份被拒;"id 生成只此一处"用**源码级断言**——扫描 `src/main` 确认仅 `JpaSessionManager` 含拼接标识的逻辑(grep 口径,避免空头断言,实现时落具体实现)。
- `SessionRepositoryTest`:脚本建表列存在;`messages_json` 往返(含 toolCalls、空串、特殊字符与换行);"重启"=用**文件型临时 SQLite**,关闭第一个 `ApplicationContext` 后新建第二个再查。
- `CliChannelTest`:脚本化 `InputStream` + 捕获 `PrintStream` + Mockito `AgentService`/`SessionManager`;覆盖回复打印、`/quit`(含首尾空白)不转交、EOF 正常结束、引擎抛错打印错误后继续。
- 不做:命令分流与 `--help` 的进程级测试(课件明确留人工)。
- `OryxOsApplicationTest`(boot,已有)扩充一条断言:`@EnableJpaRepositories`/`@EntityScan` 覆盖 `io.oryxos.storage`(`SessionRepository` Bean 存在)——对应 FR-004 的自动化部分。

## Note: T002 本地 API 核实结论(Spring AI 1.1.8 / Picocli 4.7.7)

- `UserMessage(String)`、`getText()`(继承自 `AbstractMessage`)存在。
- `AssistantMessage.builder().content(..).toolCalls(..).build()` 构造;`getToolCalls()`、`hasToolCalls()` 存在;`AssistantMessage.ToolCall` 为 record `(id, type, name, arguments)`。
- `ToolResponseMessage.builder().responses(List<ToolResponse>).build()`;`ToolResponse` 为 record `(id, name, responseData)`,`getResponses()` 取值。
- Picocli:`@Command(subcommands, mixinStandardHelpOptions)`、`@Unmatched`、`IFactory.create(Class)`、`CommandLine.execute(String...)` 与 `setExecutionExceptionHandler` 均存在。
