package io.oryxos.core.agent;

import io.oryxos.core.profile.Profile;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/** 各测试类共用的构造工具:Profile、模型响应、工具调用。 */
final class TestFixtures {

  /** 工具类,禁止实例化。 */
  private TestFixtures() {}

  /**
   * 构造一个最小 Profile。
   *
   * @param tools 可用工具名
   * @param maxIterations 最大轮数,null 表示不指定
   * @param maxHistoryTurns 历史保留轮数,null 表示不指定
   * @return Profile
   */
  static Profile profile(List<String> tools, Integer maxIterations, Integer maxHistoryTurns) {
    return new Profile(
        "weather-agent",
        "测试 Agent",
        new Profile.Identity("小天", "你是天气助手"),
        new Profile.ProviderRef("deepseek", "deepseek-chat", 0.7),
        tools,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new Profile.Settings(maxIterations, maxHistoryTurns));
  }

  /**
   * 构造只含文本的模型响应(无工具调用)。
   *
   * @param text 响应文本
   * @return ChatResponse
   */
  static ChatResponse textResponse(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  /**
   * 构造要求调用工具的模型响应。
   *
   * @param calls 工具调用
   * @return ChatResponse
   */
  static ChatResponse toolCallResponse(AssistantMessage.ToolCall... calls) {
    AssistantMessage message =
        AssistantMessage.builder().content("").toolCalls(List.of(calls)).build();
    return new ChatResponse(List.of(new Generation(message)));
  }

  /**
   * 构造一次工具调用。
   *
   * @param id 调用 id
   * @param name 工具名
   * @param arguments 入参 JSON
   * @return ToolCall
   */
  static AssistantMessage.ToolCall call(String id, String name, String arguments) {
    return new AssistantMessage.ToolCall(id, "function", name, arguments);
  }
}
