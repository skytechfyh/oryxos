package io.oryxos.storage;

import io.oryxos.core.tool.ToolResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 会话的 JPA 实体,对应手工建表脚本中的 {@code sessions},同时实现 core 的 {@link io.oryxos.core.session.Session} 契约。
 *
 * <p>对话历史整段序列化成 JSON 存在 {@code messages_json} 一列;内存里的 {@link #messages()} 惰性从该列解码,由 {@link
 * JpaSessionManager#save} 在保存时编码写回。时间用 ISO-8601 文本,因为 SQLite 没有原生时间类型。
 */
@Entity
@Table(name = "sessions")
public class Session implements io.oryxos.core.session.Session {

  /** 活跃状态。 */
  static final String STATUS_ACTIVE = "active";

  @Id
  @Column(name = "session_id")
  private String sessionId;

  @Column(name = "profile_name", nullable = false)
  private String profileName;

  @Column(nullable = false)
  private String channel;

  @Column(name = "user_id", nullable = false)
  private String userId;

  /** 整段对话历史的 JSON 数组文本。 */
  @Column(name = "messages_json", nullable = false)
  private String messagesJson;

  /** {@code active} 或 {@code archived}。 */
  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  @Column(name = "last_active_at", nullable = false)
  private String lastActiveAt;

  /** 归档时间,本阶段只存储不写入。 */
  @Column(name = "archived_at")
  private String archivedAt;

  /** 内存中的消息序列,惰性解码,不入库(入库的是 messagesJson)。 */
  @Transient private List<Message> messages;

  /** JPA 要求的无参构造。 */
  protected Session() {}

  /**
   * 构造一个新建的活跃会话,历史为空。
   *
   * @param sessionId 会话标识,由 SessionManager 生成
   * @param profileName Agent(Profile)名
   * @param channel 接入渠道
   * @param userId 用户标识
   * @param now 当前时刻,ISO-8601 文本,同时作为创建时间与最后活跃时间
   */
  public Session(String sessionId, String profileName, String channel, String userId, String now) {
    this.sessionId = sessionId;
    this.profileName = profileName;
    this.channel = channel;
    this.userId = userId;
    this.messagesJson = "[]";
    this.status = STATUS_ACTIVE;
    this.createdAt = now;
    this.lastActiveAt = now;
  }

  /** {@inheritDoc} */
  @Override
  public String id() {
    return sessionId;
  }

  /** {@inheritDoc} */
  @Override
  public String profileName() {
    return profileName;
  }

  /**
   * 完整消息序列。首次访问时从 {@code messages_json} 解码,之后在内存里累积。
   *
   * @return 可变的消息列表
   */
  @Override
  public List<Message> messages() {
    if (messages == null) {
      messages = SessionMessageCodec.decode(messagesJson);
    }
    return messages;
  }

  /** {@inheritDoc} */
  @Override
  public void append(String userMessage) {
    messages().add(new UserMessage(userMessage));
  }

  /** {@inheritDoc} */
  @Override
  public void append(ChatResponse response) {
    messages().add(response.getResult().getOutput());
  }

  /**
   * {@inheritDoc}
   *
   * <p>失败结果以 {@code ERROR: } 前缀回传给模型,让它知道这次调用没成功。
   */
  @Override
  public void appendToolResult(AssistantMessage.ToolCall call, ToolResult result) {
    String data = result.success() ? result.content() : "ERROR: " + result.errorMessage();
    messages()
        .add(
            ToolResponseMessage.builder()
                .responses(
                    List.of(new ToolResponseMessage.ToolResponse(call.id(), call.name(), data)))
                .build());
  }

  /**
   * 保存前调用:把内存历史编码写回 {@code messages_json},并刷新最后活跃时间。
   *
   * <p>从未读取过历史时不重新编码,避免无谓地解码再编码。
   *
   * @param now 当前时刻,ISO-8601 文本
   */
  void prepareForSave(String now) {
    if (messages != null) {
      messagesJson = SessionMessageCodec.encode(messages);
    }
    lastActiveAt = now;
  }

  /**
   * 渠道。
   *
   * @return 接入渠道
   */
  public String getChannel() {
    return channel;
  }

  /**
   * 用户标识。
   *
   * @return 用户标识
   */
  public String getUserId() {
    return userId;
  }

  /**
   * 状态。
   *
   * @return {@code active} 或 {@code archived}
   */
  public String getStatus() {
    return status;
  }

  /**
   * 创建时间。
   *
   * @return ISO-8601 文本
   */
  public String getCreatedAt() {
    return createdAt;
  }

  /**
   * 最后活跃时间。
   *
   * @return ISO-8601 文本
   */
  public String getLastActiveAt() {
    return lastActiveAt;
  }

  /**
   * 归档时间。
   *
   * @return ISO-8601 文本,未归档为 null
   */
  public String getArchivedAt() {
    return archivedAt;
  }
}
