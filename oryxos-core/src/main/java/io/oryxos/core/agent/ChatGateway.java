package io.oryxos.core.agent;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.tool.OryxTool;
import java.util.List;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * ReAct 循环调用大模型的唯一出口。
 *
 * <p>core 不能依赖 provider 模块(依赖方向是 provider → core),所以由 core 定义接口、{@code ProviderService} 实现。
 */
public interface ChatGateway {

  /**
   * 发起一次模型调用。
   *
   * @param sessionId 会话标识,用于 llm_calls 审计关联
   * @param profile 当前 Agent 的 Profile
   * @param prompt 本轮提示
   * @param availableTools 本轮可用工具,只作为 schema 随请求带上,不由模型框架执行
   * @return 模型响应,模型想调工具的请求原样交回
   */
  ChatResponse chat(
      String sessionId, Profile profile, Prompt prompt, List<OryxTool> availableTools);
}
