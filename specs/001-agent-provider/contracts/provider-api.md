# 内部接口契约(无对外 REST/CLI,本节契约为 Java 模块边界)

## ProviderService(oryxos-provider)

```java
public ChatResponse chat(String sessionId, Profile profile, Prompt prompt)                     // 无工具
public ChatResponse chat(String sessionId, Profile profile, Prompt prompt,
                         List<OryxTool> availableTools)                                        // 带工具(第17节 ReActLoop 用)
public Set<String> providerNames()                                                             // 供 Profile 启动校验
```

- 前置:`profile.provider().name()` 必须在显式映射表中,否则抛 `ProviderNotFoundException`(消息含该名称),**不审计**(未发起调用)。
- 行为:构造关闭 `internalToolExecutionEnabled` 的请求选项 → 附加经 `ToolSchemaAdapter` 翻译的工具定义 → `chatModel.call(...)`。
- 后置:成功→审计 success=true 并原样返回 `ChatResponse`(含模型的 tool call 请求,不执行);失败→审计 success=false+原因,原异常上抛。
- 构造:`ProviderService(Map<String, ChatModel>, ToolSchemaAdapter, LlmCallAuditor)`(与课件测试代码一致)。

## ToolSchemaAdapter

`List<ToolCallback> toSpringAiTools(List<OryxTool>)`——name/description/inputSchema 一一对齐;产物是仅含 schema 的 `ToolCallback`,`call()` 一律抛 `UnsupportedOperationException`,不含任何执行逻辑。

## LlmCallAuditor

`void record(String sessionId, String provider, String model, Usage usage, boolean success, String errorMessage, long durationMs)`

## ProfileLoader / ProfileRegistry(oryxos-core)

- `ProfileLoader(Path dir, Set<String> knownProviders, Function<String,String> env, ProfileRegistry registry)`;`load()` 返回成功数并向 `ProfileRegistry` 注册;任一坏文件只记 ERROR。
- `ProfileRegistry.find(String name) : Optional<Profile>`。

## 配置契约

```yaml
oryxos:
  providers:
    - name: deepseek
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY:}
```
