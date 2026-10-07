# 第18节验收报告:CLI 命令行入口与会话层

**分支**:`003-lesson18-cli` | **日期**:2026-10-07 | **状态**:harness 全绿,等人工项

## 一、六项证据 DoD

### 1. `./mvnw clean verify` 全绿

```
Spotless → Checkstyle(0 violations ×10)→ PMD+P3C(0 failure)→ SpotBugs+FindSecBugs(BugInstance size is 0 ×9)
Reactor Summary: 全部 10 个模块 SUCCESS    BUILD SUCCESS
```

| 模块 | 测试数 | 结果 |
|---|---|---|
| oryxos-core | 38 | 全绿(前序回归) |
| oryxos-storage | 20(本节新增 13) | 全绿 |
| oryxos-provider | 19 | 全绿(前序回归) |
| oryxos-web | 4 | 全绿(前序回归) |
| oryxos-channel-cli | 7 | 全绿(本节新增) |
| oryxos-cli | 3 | 全绿(本节新增) |
| oryxos-boot | 6(本节新增 1) | 全绿 |

**依赖变更与 CVE 核对**:本节只在 `oryxos-cli` 新增 `spring-boot`、`oryxos-channel-cli`(BOM 已管理,版本不变),`oryxos-cli`/`oryxos-channel-cli` 新增 `spring-boot-starter-test`(test scope)。`dependency:tree` 确认 Spring Boot 3.5.16、Spring AI 1.1.8、Jackson 2.21.4 不变,无 Tomcat/log4j 等已固定组件回退。**未新增、未升级任何第三方版本**,故无新的 CVE 评估项。⚠️ 提醒:PR 的 CI `-Psecurity` dependency-check 才是最终判定(本地不含)。

**SpotBugs 排除项(`config/spotbugs-exclude.xml`,均已写理由)**:
- `NM_SAME_SIMPLE_NAME_AS_INTERFACE`(`storage.Session`):实体名是课件与 §9.2 的既定字面量,改名违反"不改已定字面量"。
- `EI_EXPOSE_REP`(`Session.messages()`):第17节接口契约要求返回可变序列,ReAct 循环靠它累积历史。
- `EI_EXPOSE_REP2`(`CliChannel`):输入输出流由构造器注入,持有调用方给的流正是设计本意。

### 2. 课件 harness 映射

| 课件测试类 | 文件 | 用例数 | 关键回归 |
|---|---|---|---|
| `SessionManagerTest` | `oryxos-storage/src/test/.../SessionManagerTest.java` | 8 | ✅ `同一三元组_历次getOrCreate都是同一个Session`(`@DisplayName` 原样,方法名按 Checkstyle 取 ASCII `sameTripleGetOrCreateReturnsSameSession`,与前序节同一做法);channel/user/profile 任一不同;分隔符不碰撞;空白身份被拒;`save` 拒绝非本实现会话;**id 只此一处**(扫全仓 main 源码,`%3A` 只出现在 `JpaSessionManager`,找不到仓库根则失败) |
| `SessionRepositoryTest` | `oryxos-storage/src/test/.../SessionRepositoryTest.java` | 5 | ✅ 脚本建出的 9 列;存取;`messages_json` 往返(用户消息 / 带 toolCalls 的响应 / 工具结果 / 空串 / 引号 / 换行 / 反斜杠);`save` 刷新 `last_active_at`;**模拟重启**:文件型 SQLite,关闭第一个容器后新建第二个再查,历史仍在 |

另有本节追加的测试:`CliChannelTest`(7)、`OryxOsCliTest`(3)、`OryxOsApplicationTest.storageModuleIsScannedByJpa`(1)。

### 3. 交付物存在性核对

| 交付物 | 位置 | 状态 |
|---|---|---|
| `OryxOsCli` 主入口 | `oryxos-cli/.../OryxOsCli.java`(`OryxOsApplication.main` 一行委托) | ✅ |
| 12 个子命令 | `InitCommand` `StatusCommand` `ChatCommand` `ServeCommand` `GatewayCommand` `Profile{List,Create,Show,Delete}Command` `ProviderListCommand` `ToolListCommand` `SessionListCommand` | ✅ |
| `CliChannel` | `oryxos-channel-cli/.../CliChannel.java` | ✅ |
| `Session` 实体 + `SessionRepository` | `oryxos-storage/.../Session.java`、`SessionRepository.java` | ✅ |
| `SessionManager`(新增 `getOrCreate`/`get`,`save` 签名未动) | `oryxos-core/.../SessionManager.java` + 实现 `JpaSessionManager` | ✅ |
| `sessions` 表手工脚本 | `oryxos-storage/src/main/resources/schema.sql`(冒烟时核实 9 列真实建出) | ✅ |
| 约定:轻命令不起 Spring | `init`/`status`/`profile *`/`provider list`/`tool list`/`session list` 无 Spring 依赖;冒烟 `init` 0.31s | ✅ |
| 约定:重命令显式声明 JPA 扫描 | `OryxOsApplication` 的 `@EntityScan`/`@EnableJpaRepositories`(保留);`OryxOsApplicationTest` 断言;冒烟日志 `Found 3 JPA repository interfaces` | ✅ |

**中文注释抽查**:对本节新增/修改的 34 个 Java 文件用脚本逐方法扫描(含 private、构造器、测试方法),0 条缺失。脚本经已知缺注释的历史文件验证有效(它能标出第16节 `ProfileLoader` 的 11 处,这些是前序节遗留、不在本节改动范围内,未改动)。

**辅助类**(包私有或命令树结构,非新增对外概念):`SessionMessageCodec`、`WorkspacePaths`、`ProfileTemplates`、`ContextBlocker`、`SessionRestartConfig`(测试)。`ProviderCommand`/`ToolCommand`/`SessionCommand` 是 `provider`/`tool`/`session` 的父命令,与已确认的 `ProfileCommand` 同构(见下"偏离与发现 2")。

### 4. 前序节回归

第16、17节的测试全部在本次 `clean verify` 中重跑并全绿(core 38、provider 19、storage 里的 `LlmCallRepositoryTest`/`ToolInvocationRepositoryTest`、web 4)。第17节的 `SessionManager` 在测试里只用 Mockito mock,没有手写 fake,所以新增接口方法无需改任何前序夹具。

### 5. H4 六条全局不变量

| # | 不变量 | 结果 |
|---|---|---|
| ① | 涉外 IO 首行过 `Sandbox.enforce` | Sandbox 第24节才交付:`init`/`profile create|delete` 的写文件处已在 Javadoc 注明"第24节接 SandboxChecker";当前以 `[A-Za-z0-9_-]+` 名称白名单防路径穿越(冒烟 `profile create ../evil` 被拒) ✅ |
| ② | LLM / 工具审计成败都落库 | 本节未改动;重命令启动 JPA 扫描到 3 个仓库(含两张审计表),审计可写 ✅ |
| ③ | 无明文 key | grep 本节改动文件无命中;`provider list` 只输出 `name` 与 `base-url` ✅ |
| ④ | `session_id` 只在 `SessionManager` 内拼接 | `%3A` 只出现在 `JpaSessionManager.java`,并有测试守护;`CliChannel` 只传三元组 ✅ |
| ⑤ | 无 Reactor / `CompletableFuture` / 自建线程池 | grep 无命中;`serve`/`gateway` 用 `CountDownLatch` 同步阻塞 ✅ |
| ⑥ | 无 Spring AI 自动工具执行路径 | grep 无 `ChatClient`/`ToolCallingManager`/`internalToolExecutionEnabled`;本节不调用模型 ✅ |

附:`System.in/out/err` 在 main 源码里只出现在 `ChatCommand` 装配处一处(宪法"日志禁用 System.out"针对日志,CLI 交互输出经注入的流)。

## 二、实现中的偏离与发现

1. **冒烟抓到 `verify` 发现不了的真 bug(已修)**:`provider list` / `tool list` / `session list` 最初被注册成三个同名顶层 `list` 子命令,Picocli 构造时抛 `DuplicateNameException`,**所有命令一个都起不来**,而编译、静态检查、既有测试全部是绿的。已为它们各加父命令(同 `profile`),并加 `OryxOsCliTest`(JVM 内、不起进程不起 Spring)守住"12 个子命令都能注册、`--help` 正常"。这条课件说属进程级行为不写自动化测试,我判断进程内的命令树构造值得守,**请你知悉这是对课件"不测 `--help`"的有意补充**。
2. **冒烟抓到的另外两处(已修)**:`profile show` 因 `PrintWriter.print` 不自动刷新而无输出,已加 `flush`;非法 Profile 名(用户输入错误)原先会把整段堆栈打到终端,现改为一行错误,仅意外异常才落带堆栈日志。
3. **PMD/SpotBugs 暴露的真实隐患(已修)**:`Path.getParent()`/`getFileName()` 可能为 null,已改为不依赖它们;`@Transactional` 补 `rollbackFor`。
4. **按你的确认执行**:A(`java -jar` 无参改为打印帮助,起服务用 `serve`,README 与 CLAUDE.md 已同步)、B(推迟到第20节,不加兜底类型)、C~F(`JpaSessionManager` 命名、`tool list` 占位、`serve`/`gateway` 仅骨架、轻重分类)。
5. **spec 边缘情况收敛**:"存储模块未被扫描到时应在启动阶段暴露"——未新增启动期 fail-fast(课件无此要求),由 `OryxOsApplicationTest` 断言 + 人工核对启动日志保障(analyze C1 的处理)。
6. **遗留提示**:`oryxos-storage/src/main/resources/db/audit-tables.sql` 是第17节遗留的重复建表脚本,无任何地方引用(实际生效的是 `schema.sql`),本节未动,建议后续清理。

## 三、剩余人工项:harness 已判卷,下面这几项等你人工过

课件"做完怎么验"逐条状态:

| 课件人工项 | 状态 |
|---|---|
| `oryxos chat` 进入交互,完成多轮对话,`/quit` 正常退出;Demo 一对话版从头走通 | ⏳ **待第20节后验**。`chat` 在取 `AgentService` 时因缺 `ToolRegistry` Bean 失败(已冒烟确认,错误信息清晰);读—转交—打印、`/quit`、EOF、引擎报错继续等逻辑已由 `CliChannelTest` 判卷。需要真模型 key(`DEEPSEEK_API_KEY`)与第20节的 `ToolRegistry` |
| 轻命令秒回、重命令才启动 Spring | ✅ 我已冒烟:`init` 0.31s、日志无 Spring;`chat`/`serve` 才出现 Spring 启动日志。**建议你在自己机器再看一眼** |
| `chat` 启动日志 "Found N JPA repository interfaces" 的 N>0 | ✅ 冒烟实测 `Found 3 JPA repository interfaces`(`chat` 与 `serve` 都是) |
| 三种运行模式共享同一份 Profile 与会话存储 | ⏳ `serve` 已冒烟能起、参数透传生效、健康检查 200、Profile 与 sessions 表都在同一库;`chat` 与 `serve` 跨模式共享历史需第20节后用真对话验 |
| 12 个子命令都能跑、`--help` 正常 | ✅ `OryxOsCliTest` 判卷 12 条 `--help`;轻命令 `init`/`status`/`profile list|create|show|delete`/`provider list`/`tool list`/`session list` 已逐个冒烟。`gateway` 仅骨架,未单独冒烟启动 |
| 会话幂等、隔离、持久化 | ✅ harness 已覆盖(`mvn test` 绿即打勾) |

**需要你人工做的,按优先级**:
1. 第20节交付 `ToolRegistry` 后:设置真实 `DEEPSEEK_API_KEY`,`java -jar … chat`,多轮对话 + `/quit`,再进入确认历史仍在;再开一个 `serve` 看同一份 Profile/会话。
2. 在自己的机器上 `time java -jar … profile list` 确认轻命令秒回。
3. 提 PR 后看 CI 的 `-Psecurity` dependency-check(本地 verify 不含)。

全程未 commit / push / 运行 package.sh。分支 `003-lesson18-cli` 上的改动都在工作区,同步时机由你决定。
