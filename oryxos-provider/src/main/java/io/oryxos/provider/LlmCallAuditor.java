package io.oryxos.provider;

import io.oryxos.storage.LlmCall;
import io.oryxos.storage.LlmCallRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

/** 把每次 LLM 调用(成功与失败)写入 {@code llm_calls}。 */
@Component
public class LlmCallAuditor {

  private static final Logger LOG = LoggerFactory.getLogger(LlmCallAuditor.class);

  private final LlmCallRepository repository;

  public LlmCallAuditor(LlmCallRepository repository) {
    this.repository = repository;
  }

  /**
   * 记一笔调用。审计自身出错只记日志、不外抛:否则会盖掉调用方真正关心的原始异常。
   *
   * @param usage 模型未返回用量时为 null,此时 token 列留空
   */
  public void record(
      String sessionId,
      String provider,
      String model,
      Usage usage,
      boolean success,
      String errorMessage,
      long durationMs) {
    try {
      repository.save(
          new LlmCall(
              sessionId,
              provider,
              model,
              usage == null ? null : usage.getPromptTokens(),
              usage == null ? null : usage.getCompletionTokens(),
              usage == null ? null : usage.getTotalTokens(),
              durationMs,
              success,
              errorMessage,
              Instant.now().toString()));
    } catch (RuntimeException e) {
      LOG.error("llm_calls 审计写入失败: sessionId={}, provider={}", sessionId, provider, e);
    }
  }
}
