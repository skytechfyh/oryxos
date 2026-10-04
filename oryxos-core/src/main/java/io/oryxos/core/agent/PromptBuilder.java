package io.oryxos.core.agent;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.session.Session;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 组装每轮发给模型的提示。
 *
 * <p>按固定顺序拼四部分:system prompt(角色设定 + 启动信息 + Skill,末尾附当前日期时间)、长期记忆(没开就跳过)、会话历史(只留最近 N 轮)、当前 Profile
 * 可用的工具列表。工具列表不放进 {@link Prompt}:{@code ProviderService} 会用自己构造的选项把工具 schema 带给模型,所以这里通过 {@link
 * #availableTools} 单独提供。长期记忆是跨会话的内容,会话历史只是本次对话的往来记录,两者不混。
 */
public class PromptBuilder {

  /** 历史默认保留的轮数。 */
  public static final int MAX_HISTORY_TURNS = 20;

  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ContextLoader contextLoader;
  private final ToolRegistry toolRegistry;
  private final MemoryContextProvider memoryProvider;
  private final Clock clock;

  /**
   * 构造提示组装器。
   *
   * @param contextLoader 提供 Bootstrap 与 Skill 文本
   * @param toolRegistry 提供可用工具
   * @param memoryProvider 长期记忆提供者,未启用时为 null
   * @param clock 时钟,可注入以便测试固定时间
   */
  public PromptBuilder(
      ContextLoader contextLoader,
      ToolRegistry toolRegistry,
      MemoryContextProvider memoryProvider,
      Clock clock) {
    this.contextLoader = contextLoader;
    this.toolRegistry = toolRegistry;
    this.memoryProvider = memoryProvider;
    this.clock = clock;
  }

  /**
   * 组装本轮提示。
   *
   * @param session 当前会话,提供对话历史
   * @param profile 当前 Agent 的 Profile
   * @return 本轮提示
   */
  public Prompt build(Session session, Profile profile) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(systemPrompt(profile)));
    memory(profile).ifPresent(text -> messages.add(new SystemMessage("长期记忆:\n" + text)));
    messages.addAll(recentTurns(session.messages(), historyTurns(profile)));
    return new Prompt(messages);
  }

  /**
   * 当前 Profile 可用的工具列表,由调用方随模型请求带上。
   *
   * @param profile 当前 Agent 的 Profile
   * @return 可用工具
   */
  public List<OryxTool> availableTools(Profile profile) {
    return toolRegistry.forProfile(profile);
  }

  /**
   * 拼 system prompt:角色设定、启动信息与 Skill,最后一行是当前日期时间。 模型自己不知道今天几号,定时场景里的"今天"全靠这一行。
   *
   * @param profile 当前 Agent 的 Profile
   * @return system prompt 文本
   */
  private String systemPrompt(Profile profile) {
    StringBuilder text = new StringBuilder();
    String identity = profile.identity().prompt();
    if (identity != null && !identity.isBlank()) {
      text.append(identity).append("\n\n");
    }
    String context = contextLoader.load(profile);
    if (!context.isBlank()) {
      text.append(context).append("\n\n");
    }
    text.append("当前日期时间: ").append(LocalDateTime.now(clock).format(TIME_FORMAT));
    return text.toString();
  }

  /**
   * 取长期记忆;未启用提供者则返回空。
   *
   * @param profile 当前 Agent 的 Profile
   * @return 记忆文本
   */
  private Optional<String> memory(Profile profile) {
    if (memoryProvider == null) {
      return Optional.empty();
    }
    return memoryProvider.longTermMemory(profile);
  }

  /**
   * 解析要保留的历史轮数。
   *
   * @param profile 当前 Agent 的 Profile
   * @return Profile 指定的轮数,未指定则为默认值
   */
  private static int historyTurns(Profile profile) {
    Integer configured = profile.settings().maxHistoryTurns();
    return configured == null ? MAX_HISTORY_TURNS : configured;
  }

  /**
   * 只保留最近 N 轮。一"轮"从一条用户消息起、到下一条用户消息前为止,这样不会把 assistant 的工具调用与对应的 tool 结果拆开(拆开后模型接口会报错)。
   *
   * @param history 完整历史
   * @param turns 保留的轮数
   * @return 截断后的历史
   */
  private static List<Message> recentTurns(List<Message> history, int turns) {
    List<Integer> turnStarts = new ArrayList<>();
    for (int i = 0; i < history.size(); i++) {
      if (history.get(i) instanceof UserMessage) {
        turnStarts.add(i);
      }
    }
    if (turnStarts.size() <= turns) {
      return history;
    }
    int from = turnStarts.get(turnStarts.size() - turns);
    return history.subList(from, history.size());
  }
}
