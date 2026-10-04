# Research: Agent Provider

## R1 依赖来源(软门禁 5/6,用户已确认采用本方案)

- **事实**:锁定 BOM `spring-ai-alibaba-bom:1.1.2.4-security-fix` 仅管理 8 个 artifact(agentscope、a2a-nacos、config-nacos、graph-observation、agent-framework、graph-core、studio、sandbox),**不含任何 provider starter,也不管理 Spring AI 本体版本**;Spring Boot 3.5.16 BOM 同样不管。
- **Decision**: 根 pom 新增 `spring-ai.version`(1.1.2,对齐 Alibaba 1.1.2.x 所基于的 Spring AI 1.1.x,Maven Central 上 `spring-ai-bom:1.1.2`、`spring-ai-starter-model-openai:1.1.2` 均可解析)并 import `spring-ai-bom`;`oryxos-provider` 依赖 `spring-ai-starter-model-openai`。
- **Rationale**: deepseek/kimi 均为 OpenAI 兼容协议,按 `name + base-url + api-key` 各手工构建一个 `OpenAiChatModel`,与"显式映射"天然契合,且不依赖未验证的独立 starter。
- **Alternatives**: DashScope starter(需另核坐标且自动装配无 key 会阻断启动)——未选。
- **待实现时验证(H3)**:`./mvnw dependency:tree -pl oryxos-provider` 成功解析;1.1.2 是否与 Alibaba 1.1.2.4 的传递依赖版本冲突;`./mvnw verify -Psecurity` 是否引入新 CVE(留给 CI/用户)。

## R2 关闭自动工具执行

- **Decision**: 请求选项使用 `ToolCallingChatOptions`,`internalToolExecutionEnabled(false)`,并把 `ToolDefinition` 放入 `toolCallbacks/toolDefinitions` 仅做 schema 下发。
- **Rationale**: constitution II;课件坑二。测试里捕获传给 `ChatModel.call` 的 `Prompt`,断言其 options 的 `getInternalToolExecutionEnabled()` 为 `false` 且工具定义非空。
- **Caveat**: 课件示意的 `Request.autoExecuteTools()` 是伪代码,以 Spring AI 1.1.2 真实 API 为准;实现任务第一步先在本地依赖反编译/查 javadoc 核实方法名,核不到按软门禁停下。

## R3 Provider 构建与自动装配

- **Decision**: `ProviderConfiguration` 读取 `ProviderProperties`,逐项:缺 name、重复 name、或 api-key 为空(`${XXX_API_KEY:}` 空默认)→ 记错误日志并跳过;其余手工 `OpenAiChatModel.builder().openAiApi(OpenAiApi.builder().baseUrl().apiKey().build())...`。同时在 `application.yaml` 排除 Spring AI 模型自动装配类。
- **Rationale**: 澄清结论"缺 key 跳过不阻断";自动装配无 key 会阻断启动(constitution II)。
- **Note**: 具体 builder API 以 1.1.2 为准,实现时核实。

## R4 Profile 解析

- **Decision**: SnakeYAML `new Yaml(new SafeConstructor(new LoaderOptions()))` 读成 `Map`,再手工映射到不可变 record;不用反射式构造器。
- **Rationale**: FindSecBugs 对 `new Yaml()` 通用反序列化报高危;record 无无参构造不适合 SnakeYAML 直接绑定。
- **`${ENV}`**: 加载器内置 `Function<String,String> env`(默认 `System::getenv`,测试注入),字符串值中 `${NAME}`/`${NAME:default}` 替换;变量缺失且无默认→该 Profile 记错误日志并跳过。
- **校验**: `ProfileLoader` 接受 `Set<String> knownProviders`(由 boot 装配时取自 `ProviderService` 映射表键集),core 不依赖 provider 模块。
- **坏文件**: 每个文件单独 try/catch,记 ERROR(含文件名与原因),继续;同名 Profile 重复→后者记错误日志并跳过,不覆盖。
- **目录**: 默认 `.oryxos/profiles/`,不存在则索引为空、不报错。

## R5 llm_calls 与审计

- **Decision**: 列见 data-model.md(**待用户确认**:TechnicalSolution §9.2 未定义该表列)。`LlmCallAuditor.record(sessionId, provider, model, usage, success, errorMessage, durationMs)`,`usage` 为 Spring AI `Usage`(可为 null)。
- **失败路径**: `catch (RuntimeException e)` 先 record(success=false,message=e.getMessage())再 `throw e`;record 自身若抛异常,捕获后记 ERROR 日志并保证**原始异常**继续抛出(边缘情况"审计失败不吞原异常")。
- **建表**: `schema.sql` 用 `CREATE TABLE IF NOT EXISTS`;生产由 `spring.sql.init` 执行(boot 配置);`LlmCallRepositoryTest` 通过 `spring.sql.init.schema-locations=classpath:schema.sql` + `ddl-auto=none` 建表,并用 `PRAGMA table_info(llm_calls)` 断言 success/error_message 列存在。

## R6 H4 自查预案

- 无明文 key(`grep sk-`);无 `CompletableFuture`/Reactor;无 `session_id` 拼接(本节只透传);无自动工具执行(测试钉死)。

## T005 核实结论(Spring AI 1.1.2,javap 实测)

- 关闭自动执行:`ToolCallingChatOptions.builder().internalToolExecutionEnabled(false)`;读取用 `getInternalToolExecutionEnabled()`。
- 请求选项里携带工具的类型是 `ToolCallback`(`getToolDefinition()` + `call(String)`),而非单独的 `ToolDefinition`。因此 `ToolSchemaAdapter` 产出"仅含 schema 的 ToolCallback":`ToolDefinition.builder().name().description().inputSchema().build()` 包一层,`call()` 一律抛 `UnsupportedOperationException`(自动执行已关闭,永不会被框架调用;执行归 ToolExecutor)。契约中 `List<ToolDefinition>` 相应调整为 `List<ToolCallback>`,方法名 `toSpringAiTools` 不变。
- 构建模型:`OpenAiChatModel.builder().openAiApi(OpenAiApi.builder().baseUrl(..).apiKey(..).build()).defaultOptions(OpenAiChatOptions.builder().model(..).temperature(..).internalToolExecutionEnabled(false).build()).build()`。
- 覆盖 Prompt 选项:`prompt.mutate().chatOptions(opts).build()`。
- Usage:`getPromptTokens()/getCompletionTokens()/getTotalTokens()`(Integer,可空);`ChatResponse.getMetadata().getUsage()`;测试可用 `DefaultUsage(Integer,Integer,Integer)`。
- 需排除的自动装配(`AutoConfiguration.imports` 实测 6 项):`OpenAiChatAutoConfiguration`、`OpenAiEmbeddingAutoConfiguration`、`OpenAiImageAutoConfiguration`、`OpenAiAudioSpeechAutoConfiguration`、`OpenAiAudioTranscriptionAutoConfiguration`、`OpenAiModerationAutoConfiguration`(包 `org.springframework.ai.model.openai.autoconfigure`)。
- 依赖解析:`spring-ai-bom:1.1.2` + `spring-ai-starter-model-openai:1.1.2` 在项目锁定的 Boot 3.5.16 / Alibaba BOM 下 `dependency:tree` 成功解析。
