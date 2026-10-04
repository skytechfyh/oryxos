package io.oryxos.storage;

import io.oryxos.core.agent.ToolInvocationRecorder;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 把每次工具调用(成功与失败)写入 {@code tool_invocations}。 */
@Component
public class ToolInvocationAuditor implements ToolInvocationRecorder {

  private static final Logger LOG = LoggerFactory.getLogger(ToolInvocationAuditor.class);

  private final ToolInvocationRepository repository;

  /**
   * 构造审计器。
   *
   * @param repository 审计表仓库
   */
  public ToolInvocationAuditor(ToolInvocationRepository repository) {
    this.repository = repository;
  }

  /**
   * 记一笔调用。审计自身出错只记日志、不外抛:否则会盖掉调用方真正关心的工具结果。
   *
   * @param sessionId 会话标识
   * @param toolName 工具名
   * @param input 入参 JSON 原文
   * @param success 是否成功
   * @param errorMessage 失败原因,成功时为 null
   * @param durationMs 耗时毫秒
   */
  @Override
  public void record(
      String sessionId,
      String toolName,
      String input,
      boolean success,
      String errorMessage,
      long durationMs) {
    try {
      repository.save(
          new ToolInvocation(
              sessionId,
              toolName,
              input,
              success,
              errorMessage,
              durationMs,
              Instant.now().toString()));
    } catch (RuntimeException e) {
      LOG.error("tool_invocations 审计写入失败: sessionId={}, tool={}", sessionId, toolName, e);
    }
  }
}
