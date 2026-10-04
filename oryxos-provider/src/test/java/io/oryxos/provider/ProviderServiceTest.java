package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.Profile.ProviderRef;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.storage.LlmCall;
import io.oryxos.storage.LlmCallRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

class ProviderServiceTest {

  private ChatModel chatModel;
  private LlmCallAuditor audit;
  private ToolSchemaAdapter adapter;
  private ProviderService service;
  private final Prompt prompt = new Prompt("你好");

  @BeforeEach
  void setUp() {
    chatModel = mock(ChatModel.class);
    audit = mock(LlmCallAuditor.class);
    adapter = new ToolSchemaAdapter();
    service = new ProviderService(Map.of("deepseek", chatModel), adapter, audit);
  }

  private static ChatResponse response() {
    return ChatResponse.builder()
        .generations(List.of(new Generation(new AssistantMessage("你好,我是模型"))))
        .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(10, 20, 30)).build())
        .build();
  }

  private static Profile profileUsing(String provider) {
    return new Profile(
        "ops-agent",
        null,
        null,
        new ProviderRef(provider, provider + "-chat", 0.7),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static OryxTool httpGetTool() {
    return new OryxTool() {
      @Override
      public String name() {
        return "http_get";
      }

      @Override
      public String description() {
        return "发起 GET 请求";
      }

      @Override
      public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}";
      }
    };
  }

  @Test
  @DisplayName("按名路由_两个provider不串台")
  void routesByNameWithoutCrossTalk() {
    var deepseek = mock(ChatModel.class);
    var kimi = mock(ChatModel.class);
    when(kimi.call(any(Prompt.class))).thenReturn(response());
    var service = new ProviderService(Map.of("deepseek", deepseek, "kimi", kimi), adapter, audit);

    service.chat("s-1", profileUsing("kimi"), prompt);

    verify(kimi, times(1)).call(any(Prompt.class)); // 调的是 kimi
    verify(deepseek, never()).call(any(Prompt.class)); // deepseek 一次都没被碰——"不串台"的直接证据
  }

  @Test
  @DisplayName("未知provider名_抛ProviderNotFoundException且不发起调用")
  void unknownProviderThrowsWithoutCalling() {
    ProviderNotFoundException ex =
        assertThrows(
            ProviderNotFoundException.class,
            () -> service.chat("s-1", profileUsing("qwen"), prompt));

    assertThat(ex.getMessage()).contains("qwen");
    verify(chatModel, never()).call(any(Prompt.class));
  }

  @Test
  @DisplayName("调用成功_审计记录success为true且含token")
  void successfulCallIsAuditedWithTokens() {
    when(chatModel.call(any(Prompt.class))).thenReturn(response());

    ChatResponse resp = service.chat("s-1", profileUsing("deepseek"), prompt);

    assertThat(resp.getResult().getOutput().getText()).isEqualTo("你好,我是模型");
    ArgumentCaptor<Usage> usage = ArgumentCaptor.forClass(Usage.class);
    verify(audit)
        .record(
            eq("s-1"),
            eq("deepseek"),
            eq("deepseek-chat"),
            usage.capture(),
            eq(true),
            isNull(),
            anyLong());
    assertThat(usage.getValue().getTotalTokens()).isEqualTo(30);
  }

  @Test
  @DisplayName("调用失败_审计必须留下success为false的记录")
  void failedCallLeavesAuditWithSuccessFalse() {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connect timeout"));

    assertThrows(
        RuntimeException.class,
        () -> service.chat("s-1", profileUsing("deepseek"), prompt)); // 异常继续上抛

    verify(audit)
        .record(
            eq("s-1"),
            any(),
            any(),
            isNull(),
            eq(false),
            contains("timeout"),
            anyLong()); // 但审计先落了:success=false + 原因
  }

  @Test
  @DisplayName("审计写入自身失败_不掩盖原始调用异常")
  void auditFailureDoesNotMaskOriginalException() {
    LlmCallRepository repository = mock(LlmCallRepository.class);
    when(repository.save(any(LlmCall.class))).thenThrow(new IllegalStateException("db down"));
    var realAudit = new LlmCallAuditor(repository);
    var service = new ProviderService(Map.of("deepseek", chatModel), adapter, realAudit);
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connect timeout"));

    RuntimeException ex =
        assertThrows(
            RuntimeException.class, () -> service.chat("s-1", profileUsing("deepseek"), prompt));

    assertThat(ex.getMessage()).isEqualTo("connect timeout");
  }

  @Test
  @DisplayName("带工具schema调用_请求里关闭了自动执行")
  void toolCallRequestDisablesAutoExecution() {
    when(chatModel.call(any(Prompt.class))).thenReturn(response());

    service.chat("s-1", profileUsing("deepseek"), prompt, List.of(httpGetTool()));

    var captor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel).call(captor.capture());
    var options = (ToolCallingChatOptions) captor.getValue().getOptions();
    assertFalse(options.getInternalToolExecutionEnabled()); // 坑二的回归测试:一旦有人改回自动执行,这里立刻红
    assertNotNull(options.getToolCallbacks()); // 翻译过的 schema 确实带上了
    assertThat(options.getToolCallbacks()).hasSize(1);
    assertThat(options.getToolCallbacks().get(0).getToolDefinition().name()).isEqualTo("http_get");
  }

  @Test
  @DisplayName("不带工具调用_同样关闭自动执行并带上profile的模型与温度")
  void callWithoutToolsAlsoDisablesAutoExecution() {
    when(chatModel.call(any(Prompt.class))).thenReturn(response());

    service.chat("s-1", profileUsing("deepseek"), prompt);

    var captor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel).call(captor.capture());
    var options = (ToolCallingChatOptions) captor.getValue().getOptions();
    assertFalse(options.getInternalToolExecutionEnabled());
    assertThat(options.getModel()).isEqualTo("deepseek-chat");
    assertThat(options.getTemperature()).isEqualTo(0.7);
  }
}
