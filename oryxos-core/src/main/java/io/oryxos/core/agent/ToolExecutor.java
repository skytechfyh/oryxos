package io.oryxos.core.agent;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.core.tool.ToolResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * 工具执行的唯一入口。
 *
 * <p>这也是第 16 节要关掉 Spring AI 自动执行的原因:执行权必须收在这里,不能有第二条路。一次调用不论成败都写一条 {@code tool_invocations}
 * 审计;失败不抛出,而是返回失败的 {@link ToolResult},让循环把原因回填给模型。失败同时落审计与日志,所以并没有被吞掉。
 */
public class ToolExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(ToolExecutor.class);

  private final ToolRegistry toolRegistry;
  private final ToolInvocationRecorder recorder;

  /**
   * 构造执行器。
   *
   * @param toolRegistry 工具注册表
   * @param recorder 审计写入口
   */
  public ToolExecutor(ToolRegistry toolRegistry, ToolInvocationRecorder recorder) {
    this.toolRegistry = toolRegistry;
    this.recorder = recorder;
  }

  /**
   * 执行模型要求的一次工具调用。
   *
   * @param sessionId 会话标识,用于审计关联
   * @param call 模型发起的工具调用
   * @return 执行结果;未知工具、越权工具、工具抛异常都返回失败结果而不是抛出
   */
  public ToolResult execute(String sessionId, AssistantMessage.ToolCall call) {
    long startedAt = System.currentTimeMillis();
    ToolResult result;
    try {
      result = run(call);
    } catch (RuntimeException e) {
      LOG.error("工具执行异常: session={}, tool={}", sessionId, call.name(), e);
      result = ToolResult.fail(e.getMessage() == null ? e.toString() : e.getMessage(), false);
    }
    recorder.record(
        sessionId,
        call.name(),
        call.arguments(),
        result.success(),
        result.errorMessage(),
        System.currentTimeMillis() - startedAt);
    return result;
  }

  /**
   * 查找、校验并执行工具。
   *
   * @param call 工具调用
   * @return 执行结果
   */
  private ToolResult run(AssistantMessage.ToolCall call) {
    Optional<OryxTool> tool = toolRegistry.find(call.name());
    if (tool.isEmpty()) {
      return ToolResult.fail("未知工具: " + call.name(), false);
    }
    Profile profile = ProfileContext.current();
    if (profile != null && !profile.tools().contains(call.name())) {
      return ToolResult.fail("当前 Agent 不可使用工具: " + call.name(), false);
    }
    // 24 节接线:涉外 IO 的工具在此处执行前过 Sandbox.enforce(文件路径 / Shell 首 token / HTTP 域名白名单)
    return tool.get().execute(call.arguments());
  }
}
