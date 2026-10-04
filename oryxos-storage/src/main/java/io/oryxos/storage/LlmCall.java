package io.oryxos.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 一次 LLM 调用的审计记录,对应手工建表脚本中的 {@code llm_calls}。 */
@Entity
@Table(name = "llm_calls")
public class LlmCall {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(nullable = false)
  private String provider;

  @Column(nullable = false)
  private String model;

  @Column(name = "prompt_tokens")
  private Integer promptTokens;

  @Column(name = "completion_tokens")
  private Integer completionTokens;

  @Column(name = "total_tokens")
  private Integer totalTokens;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  @Column(nullable = false)
  private boolean success;

  @Column(name = "error_message")
  private String errorMessage;

  /** ISO-8601 文本,SQLite 没有原生时间类型。 */
  @Column(name = "created_at", nullable = false)
  private String createdAt;

  /** JPA 要求的无参构造。 */
  protected LlmCall() {}

  public LlmCall(
      String sessionId,
      String provider,
      String model,
      Integer promptTokens,
      Integer completionTokens,
      Integer totalTokens,
      long durationMs,
      boolean success,
      String errorMessage,
      String createdAt) {
    this.sessionId = sessionId;
    this.provider = provider;
    this.model = model;
    this.promptTokens = promptTokens;
    this.completionTokens = completionTokens;
    this.totalTokens = totalTokens;
    this.durationMs = durationMs;
    this.success = success;
    this.errorMessage = errorMessage;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public Integer getPromptTokens() {
    return promptTokens;
  }

  public Integer getCompletionTokens() {
    return completionTokens;
  }

  public Integer getTotalTokens() {
    return totalTokens;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getCreatedAt() {
    return createdAt;
  }
}
