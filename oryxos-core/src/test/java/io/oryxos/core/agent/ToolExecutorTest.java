package io.oryxos.core.agent;

import static io.oryxos.core.agent.TestFixtures.call;
import static io.oryxos.core.agent.TestFixtures.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.core.tool.ToolResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

/** ToolExecutor 验收:成败都写审计、失败带原因且不吞、未知/越权工具按失败处理。 */
class ToolExecutorTest {

  private final ToolRegistry registry = mock(ToolRegistry.class);
  private final ToolInvocationRecorder recorder = mock(ToolInvocationRecorder.class);
  private final OryxTool tool = mock(OryxTool.class);
  private final ToolExecutor executor = new ToolExecutor(registry, recorder);
  private final AssistantMessage.ToolCall httpGet = call("c1", "http_get", "{\"url\":\"u\"}");

  /** 注册表里只有一个 http_get。 */
  @BeforeEach
  void setUp() {
    when(registry.find("http_get")).thenReturn(Optional.of(tool));
    when(registry.find("ghost")).thenReturn(Optional.empty());
  }

  /** 防止 ProfileContext 泄漏到其他测试。 */
  @AfterEach
  void tearDown() {
    ProfileContext.clear();
  }

  /** 成功:返回结果并写 success=true 的审计。 */
  @Test
  @DisplayName("成功写审计success为true")
  void recordsSuccessAudit() {
    when(tool.execute("{\"url\":\"u\"}")).thenReturn(ToolResult.ok("12度"));

    ToolResult result = executor.execute("s-1", httpGet);

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("12度");
    verify(recorder, times(1))
        .record(eq("s-1"), eq("http_get"), eq("{\"url\":\"u\"}"), eq(true), isNull(), anyLong());
  }

  /** 工具自己返回失败结果:同样写 success=false 并带原因。 */
  @Test
  @DisplayName("工具返回失败_写审计success为false且带原因")
  void recordsFailureAuditWhenToolReturnsFailure() {
    when(tool.execute(any())).thenReturn(ToolResult.fail("连接超时", true));

    ToolResult result = executor.execute("s-1", httpGet);

    assertThat(result.success()).isFalse();
    verify(recorder, times(1))
        .record(eq("s-1"), eq("http_get"), any(), eq(false), eq("连接超时"), anyLong());
  }

  /** 工具抛异常:不吞,审计与结果都带原因,不向循环外抛。 */
  @Test
  @DisplayName("工具抛异常_不吞_结果与审计都带原因")
  void recordsFailureAuditWhenToolThrows() {
    when(tool.execute(any())).thenThrow(new IllegalStateException("boom"));

    ToolResult result = executor.execute("s-1", httpGet);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("boom");
    verify(recorder, times(1))
        .record(eq("s-1"), eq("http_get"), any(), eq(false), eq("boom"), anyLong());
  }

  /** 未知工具:写失败审计并返回失败结果,循环得以继续。 */
  @Test
  @DisplayName("未知工具_写失败审计并返回失败结果")
  void unknownToolFailsWithAudit() {
    ToolResult result = executor.execute("s-1", call("c2", "ghost", "{}"));

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("ghost");
    verify(recorder, times(1)).record(eq("s-1"), eq("ghost"), any(), eq(false), any(), anyLong());
  }

  /** 工具不在当前 Profile 的可用范围:按失败处理,且不执行。 */
  @Test
  @DisplayName("工具不在当前Profile可用范围_按失败处理")
  void toolOutsideProfileFails() {
    ProfileContext.set(profile(List.of("read_file"), null, null));

    ToolResult result = executor.execute("s-1", httpGet);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("http_get");
    verify(tool, times(0)).execute(any());
    verify(recorder, times(1))
        .record(eq("s-1"), eq("http_get"), any(), eq(false), any(), anyLong());
  }
}
