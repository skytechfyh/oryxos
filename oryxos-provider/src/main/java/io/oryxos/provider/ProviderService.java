package io.oryxos.provider;

import io.oryxos.core.agent.ChatGateway;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.tool.OryxTool;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * Agent 与大模型之间的前台:按 Profile 选模型、发起一次调用、把结果原样交回。
 *
 * <p>不管循环、不执行工具、不拼上下文;故障直接抛给上层,不做 fallback。
 */
public class ProviderService implements ChatGateway {

  private final Map<String, ChatModel> providerMap;
  private final ToolSchemaAdapter adapter;
  private final LlmCallAuditor audit;

  /** 构造入参即 provider 名到 ChatModel 的显式映射,不靠类型扫描。 */
  public ProviderService(
      Map<String, ChatModel> providerMap, ToolSchemaAdapter adapter, LlmCallAuditor audit) {
    this.providerMap = Map.copyOf(providerMap);
    this.adapter = adapter;
    this.audit = audit;
  }

  /** 不带工具的调用。 */
  public ChatResponse chat(String sessionId, Profile profile, Prompt prompt) {
    return chat(sessionId, profile, prompt, List.of());
  }

  /**
   * 发起一次模型调用。
   *
   * @param availableTools 本次可用工具,只翻译成 schema 随请求带上;模型回的"想调工具"请求原样交回上层
   * @throws ProviderNotFoundException profile 引用的 provider 未接入
   */
  @Override
  public ChatResponse chat(
      String sessionId, Profile profile, Prompt prompt, List<OryxTool> availableTools) {
    // 24 节接线:此处是涉外 IO,Sandbox 就位后在首行校验 HTTP 域名白名单
    String providerName = profile.provider().name();
    ChatModel model = providerMap.get(providerName);
    if (model == null) {
      throw new ProviderNotFoundException(providerName);
    }
    String modelName = profile.provider().model();

    // 必须关闭 Spring AI 的自动工具执行,否则工具会被调两次且绕过沙箱
    ToolCallingChatOptions options =
        ToolCallingChatOptions.builder()
            .model(modelName)
            .temperature(profile.provider().temperature())
            .internalToolExecutionEnabled(false)
            .toolCallbacks(adapter.toSpringAiTools(availableTools))
            .build();
    Prompt request = prompt.mutate().chatOptions(options).build();

    long startedAt = System.currentTimeMillis();
    ChatResponse response;
    try {
      response = model.call(request);
    } catch (Exception e) {
      // 失败也要留痕,再把原异常抛给上层;model.call 只会抛非受检异常,Java 精确重抛无需声明 throws
      audit.record(
          sessionId,
          providerName,
          modelName,
          null,
          false,
          e.getMessage(),
          System.currentTimeMillis() - startedAt);
      throw e;
    }
    audit.record(
        sessionId,
        providerName,
        modelName,
        response.getMetadata().getUsage(),
        true,
        null,
        System.currentTimeMillis() - startedAt);
    return response;
  }

  /** 已接入的 provider 名,供 Profile 加载时校验引用。 */
  public Set<String> providerNames() {
    return providerMap.keySet();
  }
}
