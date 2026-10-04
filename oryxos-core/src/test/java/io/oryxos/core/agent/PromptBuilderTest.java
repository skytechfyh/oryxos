package io.oryxos.core.agent;

import static io.oryxos.core.agent.TestFixtures.call;
import static io.oryxos.core.agent.TestFixtures.profile;
import static io.oryxos.core.agent.TestFixtures.textResponse;
import static io.oryxos.core.agent.TestFixtures.toolCallResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.core.tool.ToolResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/** PromptBuilder 验收:四部分顺序、历史按轮截断、末尾附日期时间。 */
class PromptBuilderTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-04T10:00:00Z"), ZoneOffset.UTC);

  private final ContextLoader contextLoader = mock(ContextLoader.class);
  private final ToolRegistry registry = mock(ToolRegistry.class);
  private final MemoryContextProvider memory = mock(MemoryContextProvider.class);
  private FakeSession session;

  /** 上下文固定返回一段启动信息,工具表返回一个工具。 */
  @BeforeEach
  void setUp() {
    session = new FakeSession("s-1", "weather-agent");
    when(contextLoader.load(any())).thenReturn("启动信息与技能");
    OryxTool tool = mock(OryxTool.class);
    when(tool.name()).thenReturn("http_get");
    when(registry.forProfile(any())).thenReturn(List.of(tool));
  }

  /** 四部分按 system → 长期记忆 → 会话历史顺序排列,工具列表单独由 availableTools 提供。 */
  @Test
  @DisplayName("四部分顺序正确")
  void assemblesFourPartsInOrder() {
    when(memory.longTermMemory(any())).thenReturn(Optional.of("用户喜欢简洁回答"));
    session.append("你好");
    PromptBuilder builder = new PromptBuilder(contextLoader, registry, memory, CLOCK);
    Profile profile = profile(List.of("http_get"), null, null);

    Prompt prompt = builder.build(session, profile);
    List<Message> messages = prompt.getInstructions();

    assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(messages.get(0).getText()).contains("你是天气助手").contains("启动信息与技能");
    assertThat(messages.get(1)).isInstanceOf(SystemMessage.class);
    assertThat(messages.get(1).getText()).contains("长期记忆").contains("用户喜欢简洁回答");
    assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
    assertThat(builder.availableTools(profile))
        .extracting(OryxTool::name)
        .containsExactly("http_get");
  }

  /** 没有记忆提供者时,跳过长期记忆这一部分。 */
  @Test
  @DisplayName("未启用长期记忆则跳过")
  void skipsLongTermMemoryWhenDisabled() {
    session.append("你好");
    PromptBuilder builder = new PromptBuilder(contextLoader, registry, null, CLOCK);

    List<Message> messages =
        builder.build(session, profile(List.of(), null, null)).getInstructions();

    assertThat(messages).hasSize(2);
    assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
  }

  /** 模型自己不知道今天几号,system prompt 末尾必须带当前日期时间。 */
  @Test
  @DisplayName("systemPrompt末尾含当前日期时间")
  void systemPromptEndsWithCurrentDateTime() {
    PromptBuilder builder = new PromptBuilder(contextLoader, registry, null, CLOCK);

    Prompt prompt = builder.build(session, profile(List.of(), null, null));

    assertThat(prompt.getInstructions().get(0).getText()).endsWith("2026-10-04 10:00:00");
  }

  /** 坑二回归:历史超过 N 轮只保留最近 N 轮。 */
  @Test
  @DisplayName("历史超N轮被截断")
  void truncatesHistoryBeyondMaxTurns() {
    for (int i = 1; i <= 25; i++) {
      session.append("问" + i);
      session.append(textResponse("答" + i));
    }
    PromptBuilder builder = new PromptBuilder(contextLoader, registry, null, CLOCK);

    List<Message> messages = builder.build(session, profile(List.of(), null, 20)).getInstructions();

    // 1 条 system + 20 轮 × 2 条
    assertThat(messages).hasSize(41);
    assertThat(messages.get(1).getText()).isEqualTo("问6");
    assertThat(messages.get(40).getText()).isEqualTo("答25");
  }

  /** 没配 maxHistoryTurns 时默认保留 20 轮。 */
  @Test
  @DisplayName("未指定maxHistoryTurns_默认20")
  void defaultsToTwentyHistoryTurns() {
    for (int i = 1; i <= 22; i++) {
      session.append("问" + i);
      session.append(textResponse("答" + i));
    }
    PromptBuilder builder = new PromptBuilder(contextLoader, registry, null, CLOCK);

    List<Message> messages =
        builder.build(session, profile(List.of(), null, null)).getInstructions();

    assertThat(messages).hasSize(41);
    assertThat(messages.get(1).getText()).isEqualTo("问3");
  }

  /** 按轮截断不能把 assistant 的工具调用与对应的 tool 结果拆开,否则模型接口会报错。 */
  @Test
  @DisplayName("截断不拆开工具调用与结果")
  void truncationKeepsToolCallPairsTogether() {
    session.append("旧问题");
    session.append(textResponse("旧答案"));
    session.append("查天气");
    session.append(toolCallResponse(call("c1", "http_get", "{}")));
    session.appendToolResult(call("c1", "http_get", "{}"), ToolResult.ok("12度"));
    session.append(textResponse("穿外套"));
    PromptBuilder builder = new PromptBuilder(contextLoader, registry, null, CLOCK);

    List<Message> messages = builder.build(session, profile(List.of(), null, 1)).getInstructions();

    assertThat(messages).hasSize(5); // system + 用户 + 工具调用 + 工具结果 + 最终答复
    assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
    assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
    assertThat(messages.get(3)).isInstanceOf(ToolResponseMessage.class);
  }
}
