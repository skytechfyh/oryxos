package io.oryxos.core.agent;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.session.Session;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * ReAct 循环:让模型"想一步、做一步、看结果",直到给出最终答复。
 *
 * <p>循环只做调度:转圈、判断该不该停、把每轮结果累积进会话。拼提示交给 {@link PromptBuilder},调模型交给 {@link ChatGateway},执行工具交给
 * {@link ToolExecutor}。这是自实现的循环,不使用 Spring AI 的 Agent 抽象,以便完整掌控停止条件、失败处理与上下文。
 */
public class ReActLoop {

  /** 最大轮数默认值,防止模型陷入反复调工具的死循环。 */
  public static final int MAX_ITERATIONS = 10;

  private final PromptBuilder promptBuilder;
  private final ChatGateway chatGateway;
  private final ToolExecutor toolExecutor;

  /**
   * 构造循环。
   *
   * @param promptBuilder 提示组装器
   * @param chatGateway 模型调用出口
   * @param toolExecutor 工具执行器
   */
  public ReActLoop(
      PromptBuilder promptBuilder, ChatGateway chatGateway, ToolExecutor toolExecutor) {
    this.promptBuilder = promptBuilder;
    this.chatGateway = chatGateway;
    this.toolExecutor = toolExecutor;
  }

  /**
   * 处理一条用户消息,返回最终答复。
   *
   * @param session 当前会话,累积整个调用链
   * @param userMessage 用户输入
   * @param profile 当前 Agent 的 Profile
   * @return 最终答复;转满最大轮数则返回含"达到最大轮数"的提示
   */
  public String run(Session session, String userMessage, Profile profile) {
    session.append(userMessage);
    int maxIterations = maxIterations(profile);
    List<OryxTool> tools = promptBuilder.availableTools(profile);
    for (int i = 0; i < maxIterations; i++) {
      Prompt prompt = promptBuilder.build(session, profile);
      ChatResponse response = chatGateway.chat(session.id(), profile, prompt, tools);
      // 先存回再说,每一轮都留痕,事后可审计
      session.append(response);
      if (!response.hasToolCalls()) {
        return text(response);
      }
      for (AssistantMessage.ToolCall call : response.getResult().getOutput().getToolCalls()) {
        ToolResult result = toolExecutor.execute(session.id(), call);
        session.appendToolResult(call, result);
      }
    }
    return "达到最大轮数,已停止";
  }

  /**
   * 解析最大轮数。
   *
   * @param profile 当前 Agent 的 Profile
   * @return Profile 指定的轮数,未指定则为默认值
   */
  private static int maxIterations(Profile profile) {
    Integer configured = profile.settings().maxIterations();
    return configured == null ? MAX_ITERATIONS : configured;
  }

  /**
   * 取响应文本,没有文本时返回空串。
   *
   * @param response 模型响应
   * @return 文本
   */
  private static String text(ChatResponse response) {
    if (response.getResults().isEmpty()) {
      return "";
    }
    String text = response.getResult().getOutput().getText();
    return text == null ? "" : text;
  }
}
