# Research: Notify 出站通知能力

## 1. HTTP 客户端与 Bean 落点
- **Decision**: `WebhookNotifyAdapter` 构造器注入 `RestClient`;由 `oryxos-tool` 包内非 public 的 `NotifyConfiguration` 提供名为 `notifyRestClient` 的 Bean,底层 `JdkClientHttpRequestFactory`(JDK `HttpClient`),连接超时 5s、读取超时 10s。
- **Rationale**: 课件签名要求注入 `RestClient`;JDK 客户端零新增运行时依赖、同步阻塞、与虚拟线程兼容;有限超时满足"不无限阻塞"。
- **Alternatives**: 在 boot 装配(把 notify 前提散落到启动模块,否决);用 `RestClient.Builder` 注入并在适配器内构建(偏离课件构造器签名,否决);okhttp 工厂(新增运行时依赖,否决)。

## 2. 依赖与版本(CVE 门禁前置)
- `spring-web`:版本由 Boot 3.5.16 BOM 管理(6.2.x),随 Boot 补丁线升级,不单独声明版本。
- `mockwebserver`:Maven Central 4.x 线最新发布版为 4.12.0,与 Boot BOM 管理的 `okhttp3.version` 一致,故不写版本号;仅 test scope,不进入 fat JAR。另有 `mockwebserver3` 5.x,但属另一套 API 且课件指 MockWebServer,不采用。
- 动手前须在 implement 阶段跑 `./mvnw -pl oryxos-tool dependency:tree` 确认解析正常,并检查 4.12.0 无 CVSS≥7 的已知漏洞;如有则按软门禁停下。CI 的 `-Psecurity` dependency-check 为最终判定。

## 3. 失败与边界行为
- 5xx / 4xx:`RestClient.retrieve()` 默认对 4xx/5xx 抛 `RestClientResponseException`,不捕获,直接上抛(满足 FR-005)。
- 连接失败 / 超时:`ResourceAccessException`,不捕获。
- 缺 `url`、`config` 为 null 或为空:适配器在发请求前抛 `IllegalArgumentException`(消息中文,说明缺哪项);`target` 为 null 同理。
- 空字符串 `content`:原样发送(`{"content":""}`);`content` 为 null 抛 `IllegalArgumentException`。
- JSON 转义:`Map.of("content", content)` 交给 Jackson 消息转换器序列化,特殊字符/换行/emoji 由其转义,不手拼 JSON。

## 4. 沙箱调用位
- 适配器自身不做域名白名单;`NotifyTools`(24 节之后)在调用 `send` 前执行 `Sandbox.enforce`。适配器类注释写明该接线点,满足 H4 ① "Sandbox 未就位的节留调用位"。

## 5. 无自动 tool 执行路径
- 本节不引入 Spring AI 相关代码,不存在自动工具执行路径。

## Note: 实现阶段核实结论(T001/T002)
- Boot 3.5.16 BOM **不管理** okhttp / mockwebserver,故在根 pom 新增属性 `mockwebserver.version=4.12.0` 与 dependencyManagement 条目(4.x 线 Maven Central 最新发布版);OSV 查询 mockwebserver/okhttp 4.12.0、okio 3.6.0、kotlin-stdlib 1.9.25、spring-web 6.2.19 均无已知漏洞;仅 test scope。
- `oryxos-tool` 原无 `spring-context`(仅 test scope)与 Jackson:新增 `spring-context`、`jackson-databind`(均 Boot BOM 管理,无版本)。Jackson 缺失会令 `RestClient` 无法把 `Map` 序列化为 JSON,是设计必需,plan 原未列明,已在验收报告中披露。
- API 已在本地 jar 核实:`RestClient.create()/builder()/post()`、`RequestBodySpec.contentType(MediaType)`、`ResponseSpec.toBodilessEntity()`、`JdkClientHttpRequestFactory(HttpClient)` + `setReadTimeout(Duration)`、MockWebServer 4.12.0 的 `start/url/enqueue/takeRequest/shutdown/getRequestCount`、`RecordedRequest.getMethod/getHeader/getBody`、`MockResponse.setResponseCode/setBody`。
