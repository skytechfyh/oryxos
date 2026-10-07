# 第19节 Notify 出站通知能力 验收报告

**分支**:`004-lesson19-notify` | **日期**:2026-10-07 | **tasks**:T001~T016(含 T007b)全部 `[X]`

## 本节范围(用户在第1步软门禁确认)
做:`NotifyChannelAdapter`、`NotifyTarget`、`WebhookNotifyAdapter`、包内 `NotifyConfiguration`、`WebhookNotifyAdapterTest`。
**推迟到 24 节之后**:`NotifyTools`、`NotifyToolsTest`、`Profile.notifyChannels` 结构化、`ProfileContext` 改动、Sandbox 相关类型。

## 六项证据 DoD

**1. `./mvnw clean verify` 全绿**:10 个模块全 SUCCESS,Spotless / Checkstyle(0 violations)/ PMD+P3C / SpotBugs+FindSecBugs 全过,`BUILD SUCCESS`。
实现中被门禁拦下并修正的两处:① Checkstyle 方法名规则——测试方法名改 ASCII 驼峰、中文名放 `@DisplayName`(与前序节同做法);② SpotBugs 3 处——`NotifyTarget` 加 `Map.copyOf` 防御性拷贝(真修复),`WebhookNotifyAdapter` 的 `RestClient` 注入按 `CliChannel` 先例写入 `config/spotbugs-exclude.xml` 并附理由。

**pom / 依赖改动与 CVE 核对**(须 PR 的 CI dependency-check 做最终判定):
| 改动 | 说明 |
|---|---|
| `oryxos-tool` + `spring-web`、`spring-context`、`jackson-databind` | 均 Boot BOM 管理、无版本。`spring-context` 与 Jackson 在 plan 中原未列明:实测二者在 `oryxos-tool` 仅 test scope 或缺失,`@Component`/`@Bean` 与 `RestClient` 的 JSON 序列化必需;已经你同意 C1 与 Jackson 处理方案后加入 |
| `oryxos-tool` + `mockwebserver`(test) | **Boot BOM 不管理 okhttp**(与 plan 假设不符),故在根 pom 新增 `mockwebserver.version=4.12.0`(4.x 线 Maven Central 最新发布版),仅 test scope |
| 根 pom + `jackson-bom` 2.21.7 | Boot 3.5.16 自带 2.21.4,OSV 报多个 HIGH(GHSA-cxp5-3px4-pw24、GHSA-q4xh-88c3-wmh7、GHSA-wv8q-qhhj-9h54 等)与若干 MODERATE;经你选择"根 pom 固定到 2.21.7",声明在 Boot BOM 之前。OSV 查询 jackson-databind/core 2.21.7 无已知漏洞;`oryxos-boot` 依赖树确认 `jackson-databind:2.21.7` |
OSV 对 mockwebserver/okhttp 4.12.0、okio 3.6.0、kotlin-stdlib 1.9.25、spring-web 6.2.19 均无已知漏洞。**这次 Jackson 升级影响全项目**,已由完整 `clean verify` 验证前序节无回归;`config/dependency-check-suppressions.xml` 未改动。

**2. harness 映射**:`WebhookNotifyAdapterTest`(存在,10 个用例,全绿)
| 课件关键回归 | 用例 |
|---|---|
| POST 且 body 含 `content` | `postsJsonBodyWithContent`(`@DisplayName` 发送后假服务收到POST且body含content) |
| URL 来自 `NotifyTarget.config` 非硬编码 | `urlComesFromTargetConfigNotHardcoded`(两目标不串发) |
| 5xx 异常向上抛 | `serverErrorPropagatesInsteadOfBeingSwallowed` |
| 补充边界 | 特殊字符往返、空串原样、缺 url(三种)、null 入参、4xx 上抛、连接失败上抛、Spring 装配(Bean 名 `notifyRestClient` 与 `@Qualifier` 绑定) |
第二批 `NotifyToolsTest` 按约定推迟。

**3. 交付物存在性**:`NotifyChannelAdapter`、`NotifyTarget`、`WebhookNotifyAdapter`、`NotifyConfiguration`(包内)、`WebhookNotifyAdapterTest` 均已 ls 核对;`NotifyTools`/Sandbox 类型 grep 无(仅类注释提到调用位)。抽查脚本与 Checkstyle 均确认每个方法(含构造器、测试方法、`@BeforeEach/@AfterEach`)有中文 Javadoc,类有中文类注释。新增 public 类型仅课件点名的三个。

**4. 前序节回归**:core 38、storage 20、provider 19、web 4、channel-cli 7、cli 3、boot 6(含 JPA 扫描断言与上下文装配,`WebhookNotifyAdapter` 经 `scanBasePackages` 被装配成功),全绿、0 失败 0 跳过;oryxos-tool 本节新增 10。

**5. H4 六条不变量**
① 涉外 IO:`WebhookNotifyAdapter` 类注释写明 `NotifyTools` 在 `send` 前 `Sandbox.enforce`(24 节接线),本节无 Sandbox 类型(留调用位)。
② 本节无 LLM/Tool 调用,不涉审计;`notify` 作为 Tool 的 `tool_invocations` 审计随 `NotifyTools` 经 `ToolExecutor` 落库。
③ `grep` 无明文 key/secret/password。
④ `session_id` 转义拼接仍只在 `JpaSessionManager`。
⑤ `oryxos-tool/src` 无 Reactor / `CompletableFuture` / `ExecutorService` / `new Thread`。
⑥ 无 Spring AI / `ChatClient` / `ToolCallback` 自动工具执行路径。
接口中立性(T012):`NotifyChannelAdapter`、`NotifyTarget` 的签名与注释 grep 无任何渠道特有词。

## 偏离 plan / 披露
- plan 原假设"mockwebserver 由 Boot BOM 管理"不成立,改为根 pom 属性;plan 未列 `spring-context`、`jackson-databind`、`jackson-bom` 固定——均已披露并经你确认(C1;Jackson 方案选择)。
- `NotifyTarget` 紧凑构造器做 `Map.copyOf` 拷贝(SpotBugs 要求),签名与课件一致;已同步 data-model.md。
- 课件示例 `WebhookNotifyAdapter` 缺 `MediaType`/`Map` import,已补;`spring-web` 为 compile 依赖。

## harness 已判卷,以下人工项等你过
1. **真实 webhook**:配置一个真实群机器人地址(经环境变量注入),触发一次 `send`,确认群里收到消息(假 webhook 验协议,真 webhook 验配置)。本节没有 `NotifyTools`,可用临时脚本/JShell 调 `WebhookNotifyAdapter`,或等 24 节后用对话触发。
2. **接口中立性自查**(思维练习):换成企业微信官方 SDK 实现时 `send(NotifyTarget, String)` 需要改吗?应不需要。
3. **PR 的 CI dependency-check**(`-Psecurity`,需 `NVD_API_KEY`)是依赖 CVE 的最终判定,尤其是 Jackson 2.21.7 与新增 test 依赖。
4. **推迟项提醒**:24 节之后补 `NotifyTools`、`NotifyToolsTest`(`enforce` 先于 `send` 的 `InOrder` 断言)、`Profile.notifyChannels` 结构化(现为 `List<String>`)与 `ProfileContext.resolveNotifyChannel` 相关改造,届时属于改动前序公共接口,需另走软门禁。

> 全程未 commit / push / 运行 package.sh。
