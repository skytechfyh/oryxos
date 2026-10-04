# 第 16 节验收报告:Agent Provider 与 Profile 加载

**分支**:`001-lesson16-agent-provider` | **日期**:2026-10-03 | **结论**:harness 全绿,剩余人工项见末尾

## 1. `./mvnw clean verify` 全绿

```
Spotless → Checkstyle(0 violations ×10) → PMD + 阿里 P3C → SpotBugs + Find Security Bugs → 测试
oryxos-core 9 / oryxos-storage 3 / oryxos-provider 19 / oryxos-web 4 / oryxos-boot 5 —— 全部 0 Failures, 0 Errors
BUILD SUCCESS(全部 10 个模块)
```

过程中处理的门禁问题(均改代码或就地豁免,**未修改任何 `config/` 门禁配置、未放宽阈值**):

| 门禁 | 问题 | 处理 |
|---|---|---|
| Checkstyle | 中文/含连续大写的测试方法名 | 方法名改 ASCII 驼峰,课件中文名原文放 `@DisplayName`(用户选择) |
| Checkstyle | 类名 `ProviderSmokeIT` 连续大写 | 课件固定字面量,就地 `@SuppressWarnings` 并写理由 |
| PMD/P3C | 接口方法缺 `@return`;注解间 `//` 注释 | 补 Javadoc;理由挪进类 Javadoc |
| SpotBugs | `REDOS`(占位符正则) | 改为手写解析 |
| SpotBugs | `NP_NULL_ON_SOME_PATH`(`getFileName()`)、`RCN_REDUNDANT_NULLCHECK`、`THROWS_METHOD_THROWS_RUNTIMEEXCEPTION` | 补空判断、删多余检查、精确重抛 |

## 2. 课件 harness 映射

| 课件测试类 | 文件 | 测试数 | 守点 |
|---|---|---|---|
| `ProfileLoaderTest` | `oryxos-core/.../profile/ProfileLoaderTest.java` | 9 | 全字段解析、引用不存在 provider 报错清晰、坏文件不阻断、`${ENV}` 解析(另含缺失/默认值/同名/空目录/非 YAML) |
| `ProviderServiceTest` | `oryxos-provider/.../ProviderServiceTest.java` | 7 | 见下表三个关键回归 + 未知名、成功审计、审计自身失败不掩盖异常、无工具也关自动执行 |
| `ToolSchemaAdapterTest` | `oryxos-provider/.../ToolSchemaAdapterTest.java` | 3 | 字段一一对齐、产物调用即拒绝(无执行逻辑) |
| `LlmCallRepositoryTest` | `oryxos-storage/.../LlmCallRepositoryTest.java` | 3 | 用 `schema.sql` 建表(`ddl-auto=none`),`PRAGMA table_info` 证明 `success`/`error_message` 列存在,成败记录都能存读 |
| `ProviderSmokeIT` | `oryxos-provider/.../ProviderSmokeIT.java` | 1 | `@Tag("integration")`,默认不跑 |
| (额外)`ProviderConfigurationTest` | 同模块 | 6 | 缺 key/重复名/空 base-url/构建失败均跳过且不阻断 |
| (额外)`LlmCallAuditorTest` | 同模块 | 3 | usage 为 null、repository 抛错不外抛 |

**三个关键回归测试(课件原文中文名保留在 `@DisplayName`)**:

| 课件方法名 | 实际方法名 | 守点 |
|---|---|---|
| `按名路由_两个provider不串台` | `routesByNameWithoutCrossTalk` | kimi `times(1)`,deepseek `never()` |
| `调用失败_审计必须留下success为false的记录` | `failedCallLeavesAuditWithSuccessFalse` | `assertThrows` + `verify(audit).record(..., eq(false), contains("timeout"), ...)` |
| `带工具schema调用_请求里关闭了自动执行` | `toolCallRequestDisablesAutoExecution` | 捕获 `Prompt`,`getInternalToolExecutionEnabled()==false` 且工具回调非空 |

## 3. 交付物存在性(ls 核对,全部非空)

- 代码:`Profile`、`ProfileLoader`、`ProfileRegistry`、`OryxTool`(core);`ProviderService`、`ToolSchemaAdapter`、`LlmCallAuditor`、`ProviderNotFoundException`、`ProviderProperties`、`ProviderConfiguration`(provider);`LlmCall`、`LlmCallRepository`(storage);`ProfileConfiguration`(boot 接线)
- 配置:`application.yaml` 的 `oryxos.providers` 全局层(`${DEEPSEEK_API_KEY:}` 占位);`.oryxos/profiles/ops-agent.yaml` 的 `provider` 段
- 表:`oryxos-storage/src/main/resources/schema.sql` 的 `llm_calls`(含 `success`/`error_message`)
- 偏差与新增项逐条记录见 `tasks.md` 末尾「实现偏差记录」

## 4. 前序节回归

第 16 节无前序代码课。骨架阶段既有测试(`oryxos-web` 4 个、`oryxos-boot` 5 个,含 Actuator 独立端口、全局异常、traceId)在本节改动后全部保持绿。

## 5. H4 六条全局不变量自查

| # | 不变量 | 结果 | 证据 |
|---|---|---|---|
| ① | 涉外 IO 首行过 `Sandbox.enforce` | 留位 | Sandbox 属第 24 节;`ProviderService.chat` 首行留"24 节接线:HTTP 域名白名单"注释位 |
| ② | LLM 调用成败都落 `llm_calls` | ✅ | `failedCallLeavesAuditWithSuccessFalse`、`successfulCallIsAuditedWithTokens`、`auditFailureDoesNotMaskOriginalException`;工具侧 `tool_invocations` 属第 17 节 |
| ③ | 无明文 key | ✅ | `grep` 全仓 `sk-…` 与 `api-key: <字面量>` 无命中;配置只用 `${XXX_API_KEY:}` |
| ④ | `session_id` 只在 `SessionManager` 内拼接 | ✅ | 本节只透传 `sessionId`,无任何拼接(grep 仅命中实体列名) |
| ⑤ | 无 Reactor / `CompletableFuture` / 自建线程池 | ✅ | `oryxos-*/src/main` grep 无命中 |
| ⑥ | 无 Spring AI 自动工具执行路径 | ✅ | 请求选项与默认模型选项均 `internalToolExecutionEnabled(false)`;`application.yaml` 排除 6 个 Spring AI OpenAI 自动装配类;回归测试钉死 |

## 6. harness 已判卷;以下几项等你人工过

1. **真模型冒烟**:本节没有真 key,未能断言"非空响应 + 审计 `success=true`"。
   - 已验证:用假 key 跑 `ProviderSmokeIT`,Spring 上下文、手工建模型、HTTP 通路全部走通,到达 DeepSeek 真实端点并拿到真实的 `401`。
   - 你来跑:`DEEPSEEK_API_KEY=<真key> ./mvnw test -pl oryxos-provider -am -Dexcluded.test.groups= -Dgroups=integration -Dsurefire.failIfNoSpecifiedTests=false`
2. **新增依赖的 CVE 检查**:本节新增 `spring-ai-bom:1.1.2` 与 `spring-ai-starter-model-openai`。`./mvnw verify -Psecurity` 需要 `NVD_API_KEY`(CI 执行),我没有跑,请你或 CI 确认没有引入新漏洞;若有需按 `config/dependency-check-suppressions.xml` 的约定评估。
3. **provider 名与 base-url 的实际可用性**:`deepseek` 用 `https://api.deepseek.com`、`kimi` 用 `https://api.moonshot.cn`,依赖 OpenAI 兼容路径 `/v1/chat/completions`。deepseek 的路径已被真实 401 响应间接验证;kimi 尚未真实验证。
4. **`llm_calls` 11 列**:文档未定义,按课件与 §3.3 推出并经你确认,后续若 §9.2 补全需对齐。
5. **构建时 Spring AI 1.1.2 与 Alibaba 1.1.2.4-security-fix 的兼容**:`dependency:tree` 解析成功、全仓测试通过,但未对 Alibaba 的 agent-framework 等模块做运行验证(本节未引入它们)。
6. **未提交**:全部改动留在工作区,未 commit / push / 运行 package.sh,由你决定。
