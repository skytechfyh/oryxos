---

description: "第19节 Notify 出站通知能力任务清单"
---

# Tasks: Notify 出站通知能力(接口、通知目标、webhook 实现)

**Input**: `specs/004-notify-adapter/` 下的 plan.md、spec.md、research.md、data-model.md、contracts/notify-channel-adapter.md、quickstart.md

**Prerequisites**: 第1步软门禁已由用户确认——本节范围仅为 `NotifyChannelAdapter`、`NotifyTarget`、`WebhookNotifyAdapter`(+ 包内 `NotifyConfiguration`)与 `WebhookNotifyAdapterTest`;`NotifyTools`/`NotifyToolsTest`、`Profile.notifyChannels` 结构化、`ProfileContext` 改动、Sandbox 相关类型一律推迟到 24 节之后。

**Tests**: 课件"验收 harness"第一批 `WebhookNotifyAdapterTest` 先于 `WebhookNotifyAdapter` 实现落地(harness 先行),关键用例(POST 且 body 含 `content`、URL 来自配置而非硬编码、5xx 异常上抛)原样保留,方法名沿用中文风格;另补 spec 边界用例。

**Organization**: 按 spec 用户故事分组:US1 推送(P1)、US2 地址只来自配置(P1)、US3 故障明确上抛(P1)、US4 接口中立(P2,设计自查,无运行时代码)。

## Format: `[ID] [P?] [Story] Description`

- 代码风格:Google 格式 + 阿里 P3C,**每个方法(含 private、构造器、测试方法)带中文 Javadoc,类带中文类注释**;避开 Java 18+ 增强 switch `default ->`;每个任务完成后跑 `oryxos-tool` 测试,红了当场修。
- 构建一律 `./mvnw`;全程不 commit / push;不新增 plan 未列明的 public 类型、配置键、表。
- 反作弊:不删断言、不 `@Disabled`、不放宽阈值。

---

## Phase 1: Setup(依赖与前置核实)

- [X] T001 在 `oryxos-tool/pom.xml` 增加依赖 `org.springframework:spring-web` 与 `org.springframework:spring-context`(均无版本,Boot BOM 管理;`spring-context` 必需,因 `dependency:tree` 实测其在 `oryxos-tool` 仅 test scope,而 `@Component`/`@Configuration`/`@Bean` 需要它)以及 `com.squareup.okhttp3:mockwebserver`(`<scope>test</scope>`,无版本,Boot BOM 管理的 `okhttp3.version`=4.12.0);**先**查 Maven Central 确认 4.x 线最新发布版为 4.12.0、与 BOM 一致,并核对其无 CVSS≥7 已知漏洞;再执行 `./mvnw -pl oryxos-tool -am dependency:tree`,确认依赖解析成功、`spring-context` 与 `spring-web` 同为 6.2.x 且为 compile scope、Tomcat/log4j/swagger-ui 等已固定版本不回退、mockwebserver 不进入 fat JAR(仅 test scope);有 CVSS≥7 或版本回退按软门禁停下
- [X] T002 核实本地 API(H3,核实不到按软门禁停下):Spring Framework 6.2.x 的 `RestClient`(`builder()`、`post().uri(String).contentType(MediaType).body(Object).retrieve().toBodilessEntity()`)、`JdkClientHttpRequestFactory`(`setReadTimeout(Duration)`、以 `HttpClient.newBuilder().connectTimeout(Duration)` 构造的方式)、MockWebServer 4.12.0(`okhttp3.mockwebserver.MockWebServer`、`MockResponse`、`takeRequest`、`enqueue`、`shutdown`);结论追加到 `specs/004-notify-adapter/research.md` 末尾 Note

---

## Phase 2: Foundational(阻塞所有用户故事)

**Purpose**: 接口与数据类型,字面量逐字按课件。

- [X] T003 [P] 在 `oryxos-tool/src/main/java/io/oryxos/tool/notify/NotifyTarget.java` 建 `public record NotifyTarget(String channelType, Map<String, String> config)`(字面量与课件一致,record 本身不加校验;类注释说明"渠道类型 + 一份渠道配置,配置含义由实现解释")
- [X] T004 [P] 在 `oryxos-tool/src/main/java/io/oryxos/tool/notify/NotifyChannelAdapter.java` 建 `public interface NotifyChannelAdapter { void send(NotifyTarget target, String content); }`(接口注释写明"成功即正常返回,失败抛运行时异常、不吞;签名不含任何渠道特有概念")
- [X] T005 在 `oryxos-tool/src/main/java/io/oryxos/tool/notify/NotifyConfiguration.java` 建**包内可见**(非 public)`@Configuration` 类,提供 `@Bean("notifyRestClient") RestClient`:底层 `JdkClientHttpRequestFactory`,连接超时 5s、读取超时 10s;类注释说明"为何需要:课件让适配器注入 `RestClient`,工程内尚无该 Bean;为何包内可见:不新增对外概念;为何命名:以后其他模块再提供 `RestClient` 时避免歧义"。无任何异步原语

**Checkpoint**: `./mvnw -pl oryxos-tool -am test-compile` 通过。

---

## Phase 3: User Story 1 - 把一条内容推送到配置好的 webhook (Priority: P1) 🎯 MVP

**Goal**: 对配置了 url 的目标发一条内容,目标收到一次 JSON POST,`content` 字段一致。

**Independent Test**: 本地 MockWebServer 作假 webhook,发送后 `takeRequest` 断言方法、Content-Type、body。

### Tests(先于实现)

- [X] T006 [US1] 在 `oryxos-tool/src/test/java/io/oryxos/tool/notify/WebhookNotifyAdapterTest.java` 新建测试类(类注释;`@BeforeEach` 启 `MockWebServer`、构造 `RestClient.create()` 与 `WebhookNotifyAdapter`,`@AfterEach` 关闭):写 `发送后假服务收到POST且body含content`(断言请求方法 `POST`、`Content-Type` 含 `application/json`、body 经 Jackson 解析后 `content` 等于发送内容,恰好 1 次请求);写 `特殊字符与换行与emoji原样往返`(含引号、换行、中文、emoji);写 `空字符串content原样发送`(body `content` 为 `""`)

### Implementation

- [X] T007 [US1] 在 `oryxos-tool/src/main/java/io/oryxos/tool/notify/WebhookNotifyAdapter.java` 建 `public @Component class WebhookNotifyAdapter implements NotifyChannelAdapter`:构造器 `WebhookNotifyAdapter(@Qualifier("notifyRestClient") RestClient restClient)`;`send` 取 `target.config().get("url")`,`restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(Map.of("content", content)).retrieve().toBodilessEntity()`(补课件缺失的 `MediaType`/`Map` import);类注释写明沙箱调用位("域名白名单由 `NotifyTools` 在 `send` 前 `Sandbox.enforce`,24 节之后接线")与同步阻塞说明;**不 catch 任何异常**;跑 T006 的测试至绿

- [X] T007b [US1] 在 `WebhookNotifyAdapterTest` 追加 `Spring装配后可取到NotifyChannelAdapter`:用 `AnnotationConfigApplicationContext` 注册 `NotifyConfiguration` 与 `WebhookNotifyAdapter`(同包,可访问包内类),断言 `getBean(NotifyChannelAdapter.class)` 为 `WebhookNotifyAdapter`、且存在名为 `notifyRestClient` 的 `RestClient` Bean(守 Bean 名与 `@Qualifier` 绑定,对应 analyze C2;超时值不写慢测试);跑至绿

**Checkpoint**: US1 四个用例绿(含装配用例)。

---

## Phase 4: User Story 2 - 推送地址只来自目标配置 (Priority: P1)

**Goal**: 地址由 `NotifyTarget.config` 决定;缺失地址时不发请求并明确报错。

**Independent Test**: 两个假服务各发一次互不串;缺 url 抛异常且假服务 0 请求。

### Tests(先于实现)

- [X] T008 [US2] 在 `WebhookNotifyAdapterTest` 追加:`URL来自NotifyTarget配置而非硬编码`(起两个 MockWebServer,两目标各发一次,断言各只收到属于自己的 1 次请求);`缺少url时明确报错且不发请求`(`config` 为空 Map、`url` 键缺失、`url` 为空白三种,断言 `IllegalArgumentException` 且服务端请求数为 0);`target或config为null时明确报错`;`content为null时明确报错`

### Implementation

- [X] T009 [US2] 在 `WebhookNotifyAdapter.send` 开头加入入参校验:`target`、`target.config()` 为 null、`url` 缺失或空白、`content` 为 null → 抛带中文消息的 `IllegalArgumentException`,先于任何网络调用(data-model.md 校验规则);跑 T008 测试至绿

**Checkpoint**: US1/US2 全绿。

---

## Phase 5: User Story 3 - 目标故障时调用方明确得知失败 (Priority: P1)

**Goal**: 5xx、不可达一律异常上抛,不静默。

**Independent Test**: 假服务回 500 → `send` 抛异常;关闭服务端后 → 抛异常。

### Tests(先于实现)

- [X] T010 [US3] 在 `WebhookNotifyAdapterTest` 追加课件关键用例 `webhook返回5xx时异常向上抛不静默吞掉`(`MockResponse` 500,断言抛 `RestClientResponseException`,且确有请求到达);`4xx同样上抛`(如 404);`连接失败时异常上抛`(先取得服务端地址再 `shutdown`,断言抛 `ResourceAccessException`)

### Implementation

- [X] T011 [US3] 确认 `WebhookNotifyAdapter` 无任何 try/catch 吞异常(`RestClient.retrieve()` 默认对 4xx/5xx 抛 `RestClientResponseException`);若 T010 红则修实现(不改断言);跑 T010 测试至绿

**Checkpoint**: 三个 P1 故事全绿,`WebhookNotifyAdapterTest` 完整。

---

## Phase 6: User Story 4 - 接口中立,可扩展新渠道 (Priority: P2)

**Goal**: 接口与目标类型中不出现渠道特有词(设计自查)。

- [X] T012 [US4] 自查并记入验收报告:`grep -rniE "wecom|企业微信|feishu|飞书|dingtalk|钉钉|webhook" oryxos-tool/src/main/java/io/oryxos/tool/notify/NotifyChannelAdapter.java oryxos-tool/src/main/java/io/oryxos/tool/notify/NotifyTarget.java` 应无命中(注释与签名均不含);结论写入 `lesson-report.md`;不写自动化测试(课件明确"测不出来",属人工思维练习)

---

## Phase 7: Polish & 节级收尾

- [X] T013 执行 `./mvnw spotless:apply` 后跑 `./mvnw clean verify`,全绿(Spotless / Checkstyle / PMD + P3C / SpotBugs + FindSecBugs);红了修实现,不放宽检查;SpotBugs 如需排除,在 `config/spotbugs-exclude.xml` 写明理由
- [X] T014 前序节回归:确认 `clean verify` 已包含 16~18 节全部测试且全绿(逐模块贴测试数)
- [X] T015 H4 六条不变量自查并记入报告:①`WebhookNotifyAdapter` 的 Sandbox 调用位已在类注释注明 24 节接线;②本节无 LLM/Tool 调用,不涉审计;③grep 无明文 key/URL 凭证;④`session_id` 拼接仍只在 `JpaSessionManager`;⑤`grep -rniE "Reactor|CompletableFuture|ExecutorService|new Thread" oryxos-tool/src` 无命中;⑥无 Spring AI 自动工具执行路径
- [X] T016 交付物存在性核对(ls/grep):4 个 main 类 + 1 个 test 类 + pom 改动;抽查每个方法均有中文 Javadoc;生成 `specs/004-notify-adapter/lesson-report.md`(六项证据 + 人工项清单:真实 webhook 群里收到、接口中立性自查;并提醒 PR CI 的 dependency-check 为最终判定,以及推迟项清单 `NotifyTools`/`NotifyToolsTest`/`Profile.notifyChannels`/`ProfileContext` 待 24 节之后)

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → US1 → US2 → US3 → US4 → Polish;US1~US3 共用同一测试类与同一实现类,须顺序执行,不并行。
- Phase 2 中 T003、T004 可并行(不同文件);T005 独立,但同 Phase 内先后无依赖,建议在 T003/T004 后做。
- 每个故事内:测试任务先于实现任务(harness 先行)。

## Implementation Strategy

MVP = Phase 1~3(US1):接口 + 目标 + webhook 发送 + 成功路径测试。随后 US2(配置驱动与校验)、US3(失败上抛)逐步加固,最后收尾验证。
