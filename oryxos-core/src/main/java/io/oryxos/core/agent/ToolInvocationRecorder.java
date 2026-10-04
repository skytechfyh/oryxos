package io.oryxos.core.agent;

/**
 * 工具调用审计的写入口。
 *
 * <p>core 不能依赖 storage 模块,所以由 core 定义接口、storage 实现并写入 {@code tool_invocations}。
 */
public interface ToolInvocationRecorder {

  /**
   * 记一笔工具调用,成功与失败都要记。实现方自身出错只记日志、不外抛,以免盖住调用方真正关心的结果。
   *
   * @param sessionId 会话标识
   * @param toolName 工具名
   * @param input 模型给出的入参 JSON 原文
   * @param success 是否成功
   * @param errorMessage 失败原因,成功时为 null
   * @param durationMs 耗时毫秒
   */
  void record(
      String sessionId,
      String toolName,
      String input,
      boolean success,
      String errorMessage,
      long durationMs);
}
