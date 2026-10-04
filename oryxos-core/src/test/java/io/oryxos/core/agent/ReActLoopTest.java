package io.oryxos.core.agent;

import static io.oryxos.core.agent.TestFixtures.call;
import static io.oryxos.core.agent.TestFixtures.profile;
import static io.oryxos.core.agent.TestFixtures.textResponse;
import static io.oryxos.core.agent.TestFixtures.toolCallResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.tool.ToolResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;

/** ReActLoop 验收:收尾、工具回填、最大轮数兜底、累积、失败不崩溃。 */
class ReActLoopTest {

  private final ChatGateway providerService = mock(ChatGateway.class);
  private final PromptBuilder promptBuilder = mock(PromptBuilder.class);
  private final ToolExecutor toolExecutor = mock(ToolExecutor.class);
  private final ReActLoop loop = new ReActLoop(promptBuilder, providerService, toolExecutor);
  private final AssistantMessage.ToolCall httpGetCall =
      call("c1", "http_get", "{\"url\":\"https://weather.example\"}");
  private FakeSession session;

  /** 提示组装与工具列表都打桩,循环自身只管调度。 */
  @BeforeEach
  void setUp() {
    session = new FakeSession("s-1", "weather-agent");
    when(promptBuilder.build(any(), any())).thenReturn(new Prompt("p"));
    when(promptBuilder.availableTools(any())).thenReturn(List.of());
  }

  /** 无工具调用:只调一次模型,返回其文本。 */
  @Test
  @DisplayName("无工具调用_一轮收尾")
  void finishesInOneRoundWithoutToolCalls() {
    when(providerService.chat(any(), any(), any(), any())).thenReturn(textResponse("你好"));

    String reply = loop.run(session, "hi", profile(List.of(), null, null));

    assertThat(reply).isEqualTo("你好");
    verify(providerService, times(1)).chat(any(), any(), any(), any());
  }

  /** 有工具调用:执行工具并把结果回填进会话,下一轮收尾。 */
  @Test
  @DisplayName("有工具调用_执行并回填进下一轮")
  void executesToolAndFeedsResultBack() {
    when(providerService.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(httpGetCall), textResponse("穿外套"));
    when(toolExecutor.execute("s-1", httpGetCall)).thenReturn(ToolResult.ok("12度"));

    String reply = loop.run(session, "查天气", profile(List.of("http_get"), null, null));

    assertThat(reply).isEqualTo("穿外套");
    verify(toolExecutor, times(1)).execute("s-1", httpGetCall);
    assertThat(session.messages()).anyMatch(m -> m instanceof ToolResponseMessage);
  }

  /** 一次响应里多个工具调用,按顺序逐个执行,不并行。 */
  @Test
  @DisplayName("一次响应多个工具调用_按顺序执行")
  void runsMultipleToolCallsInOrder() {
    AssistantMessage.ToolCall second = call("c2", "read_file", "{}");
    when(providerService.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(httpGetCall, second), textResponse("好了"));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("x"));

    loop.run(session, "go", profile(List.of(), null, null));

    InOrder order = inOrder(toolExecutor);
    order.verify(toolExecutor).execute("s-1", httpGetCall);
    order.verify(toolExecutor).execute("s-1", second);
  }

  /** 模型既没文本也没工具调用,视为无工具调用,直接收尾不进入下一轮。 */
  @Test
  @DisplayName("空响应_视为无工具调用并收尾")
  void treatsEmptyResponseAsFinal() {
    when(providerService.chat(any(), any(), any(), any())).thenReturn(textResponse(""));

    String reply = loop.run(session, "hi", profile(List.of(), null, null));

    assertThat(reply).isEmpty();
    verify(providerService, times(1)).chat(any(), any(), any(), any());
  }

  /** 坑一回归:模型永远要调工具,必须恰好转满最大轮数后强制停。 */
  @Test
  @DisplayName("模型一直要调工具_转满最大轮数强制停")
  void stopsAtMaxIterationsWhenModelNeverConverges() {
    when(providerService.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(httpGetCall)); // 每轮都要调工具,永不收敛
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("x"));

    String reply = loop.run(session, "查天气", profile(List.of("http_get"), 10, null));

    verify(providerService, times(10)).chat(any(), any(), any(), any()); // 恰好 10 轮,一轮不多
    assertTrue(reply.contains("达到最大轮数"));
  }

  /** Profile 没指定轮数时,默认 10 轮。 */
  @Test
  @DisplayName("Profile未指定轮数_默认10")
  void defaultsToTenIterations() {
    when(providerService.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(httpGetCall));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("x"));

    loop.run(session, "go", profile(List.of(), null, null));

    verify(providerService, times(10)).chat(any(), any(), any(), any());
  }

  /** Profile 指定了轮数,以 Profile 为准。 */
  @Test
  @DisplayName("Profile指定轮数_以Profile为准")
  void profileOverridesMaxIterations() {
    when(providerService.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(httpGetCall));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("x"));
    Profile three = profile(List.of(), 3, null);

    loop.run(session, "go", three);

    verify(providerService, times(3)).chat(any(), any(), any(), any());
  }

  /** 坑三回归:用户消息、每轮响应、每个工具结果都累积进会话,且 sessionId 传给模型调用。 */
  @Test
  @DisplayName("每轮响应和工具结果都累积进Session")
  void accumulatesEveryRoundIntoSession() {
    when(providerService.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(httpGetCall), textResponse("完"));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.ok("12度"));

    loop.run(session, "查天气", profile(List.of(), null, null));

    // 用户消息、assistant(工具调用)、tool 结果、assistant(最终)
    assertThat(session.messages()).hasSize(4);
    verify(providerService, times(2)).chat(any(), any(), any(), any());
    verify(providerService, times(2))
        .chat(org.mockito.ArgumentMatchers.eq("s-1"), any(), any(), any());
  }

  /** 工具失败:失败原因作为结果回填给模型,循环继续而不抛出。 */
  @Test
  @DisplayName("工具执行失败_失败原因回填且循环不抛出")
  void feedsToolFailureBackWithoutThrowing() {
    when(providerService.chat(any(), any(), any(), any()))
        .thenReturn(toolCallResponse(httpGetCall), textResponse("抱歉查不到"));
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.fail("连接超时", true));

    String reply = loop.run(session, "查天气", profile(List.of(), null, null));

    assertThat(reply).isEqualTo("抱歉查不到");
    assertThat(session.messages().toString()).contains("连接超时");
  }
}
