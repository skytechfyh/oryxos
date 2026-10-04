package io.oryxos.core.agent;

import io.oryxos.core.session.Session;
import io.oryxos.core.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;

/** 测试用的内存 Session,真正的实现由第 18 节交付。 */
final class FakeSession implements Session {

  private final String id;
  private final String profileName;
  private final List<Message> messages = new ArrayList<>();

  /**
   * 构造内存会话。
   *
   * @param id 会话标识
   * @param profileName 所属 Profile 名
   */
  FakeSession(String id, String profileName) {
    this.id = id;
    this.profileName = profileName;
  }

  /** {@inheritDoc} */
  @Override
  public String id() {
    return id;
  }

  /** {@inheritDoc} */
  @Override
  public String profileName() {
    return profileName;
  }

  /** {@inheritDoc} */
  @Override
  public List<Message> messages() {
    return messages;
  }

  /** {@inheritDoc} */
  @Override
  public void append(String userMessage) {
    messages.add(new UserMessage(userMessage));
  }

  /** {@inheritDoc} */
  @Override
  public void append(ChatResponse response) {
    messages.add(response.getResult().getOutput());
  }

  /** {@inheritDoc} */
  @Override
  public void appendToolResult(AssistantMessage.ToolCall call, ToolResult result) {
    String data = result.success() ? result.content() : "ERROR: " + result.errorMessage();
    messages.add(
        ToolResponseMessage.builder()
            .responses(List.of(new ToolResponseMessage.ToolResponse(call.id(), call.name(), data)))
            .build());
  }
}
