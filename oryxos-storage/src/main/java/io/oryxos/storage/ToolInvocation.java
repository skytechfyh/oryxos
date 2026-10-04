package io.oryxos.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 一次工具调用的审计记录,对应手工建表脚本中的 {@code tool_invocations}。 */
@Entity
@Table(name = "tool_invocations")
public class ToolInvocation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(name = "tool_name", nullable = false)
  private String toolName;

  /** 模型给出的入参 JSON 原文。 */
  private String input;

  @Column(nullable = false)
  private boolean success;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  /** ISO-8601 文本,SQLite 没有原生时间类型。 */
  @Column(name = "created_at", nullable = false)
  private String createdAt;

  /** JPA 要求的无参构造。 */
  protected ToolInvocation() {}

  /**
   * 构造一条审计记录。
   *
   * @param sessionId 会话标识
   * @param toolName 工具名
   * @param input 入参 JSON 原文
   * @param success 是否成功
   * @param errorMessage 失败原因,成功时为 null
   * @param durationMs 耗时毫秒
   * @param createdAt 创建时间,ISO-8601 文本
   */
  public ToolInvocation(
      String sessionId,
      String toolName,
      String input,
      boolean success,
      String errorMessage,
      long durationMs,
      String createdAt) {
    this.sessionId = sessionId;
    this.toolName = toolName;
    this.input = input;
    this.success = success;
    this.errorMessage = errorMessage;
    this.durationMs = durationMs;
    this.createdAt = createdAt;
  }

  /**
   * 主键。
   *
   * @return 主键
   */
  public Long getId() {
    return id;
  }

  /**
   * 会话标识。
   *
   * @return 会话标识
   */
  public String getSessionId() {
    return sessionId;
  }

  /**
   * 工具名。
   *
   * @return 工具名
   */
  public String getToolName() {
    return toolName;
  }

  /**
   * 入参 JSON 原文。
   *
   * @return 入参
   */
  public String getInput() {
    return input;
  }

  /**
   * 是否成功。
   *
   * @return 成功为 true
   */
  public boolean isSuccess() {
    return success;
  }

  /**
   * 失败原因。
   *
   * @return 失败原因,成功时为 null
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * 耗时毫秒。
   *
   * @return 耗时
   */
  public long getDurationMs() {
    return durationMs;
  }

  /**
   * 创建时间。
   *
   * @return ISO-8601 文本
   */
  public String getCreatedAt() {
    return createdAt;
  }
}
