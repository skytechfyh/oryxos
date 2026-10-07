package io.oryxos.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.oryxos.core.BizException;
import io.oryxos.core.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 对话历史与 {@code messages_json} 之间的编解码器。
 *
 * <p>不直接序列化 Spring AI 的 {@link Message}:它是多态层级,部分类没有无参构造,直接绑定回读不可靠。这里映射成自有的简单 JSON 数组,格式稳定、可读、版本可控。
 * 整段历史存一列,核心阶段不按条拆表。
 */
final class SessionMessageCodec {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String TYPE = "type";
  private static final String TYPE_USER = "user";
  private static final String TYPE_ASSISTANT = "assistant";
  private static final String TYPE_TOOL = "tool";
  private static final String TEXT = "text";
  private static final String TOOL_CALLS = "toolCalls";
  private static final String RESPONSES = "responses";

  /** 工具类,禁止实例化。 */
  private SessionMessageCodec() {}

  /**
   * 把消息序列编码成 JSON 数组文本。
   *
   * @param messages 按时间顺序的消息
   * @return JSON 数组文本
   * @throws BizException 出现不支持的消息类型或序列化失败
   */
  static String encode(List<Message> messages) {
    ArrayNode array = MAPPER.createArrayNode();
    for (Message message : messages) {
      array.add(encodeOne(message));
    }
    try {
      return MAPPER.writeValueAsString(array);
    } catch (JsonProcessingException e) {
      throw new BizException(ErrorCode.INTERNAL_ERROR, "会话历史序列化失败", e);
    }
  }

  /**
   * 把 JSON 数组文本解码回可变的消息列表。
   *
   * @param json JSON 数组文本
   * @return 消息列表,可继续追加
   * @throws BizException JSON 非法或含未知消息类型,不吞掉
   */
  static List<Message> decode(String json) {
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new BizException(ErrorCode.INTERNAL_ERROR, "会话历史反序列化失败", e);
    }
    if (root == null || !root.isArray()) {
      throw new BizException(ErrorCode.INTERNAL_ERROR, "会话历史格式非法: 顶层不是数组");
    }
    List<Message> messages = new ArrayList<>();
    for (JsonNode node : root) {
      messages.add(decodeOne(node));
    }
    return messages;
  }

  /**
   * 编码单条消息。
   *
   * @param message 消息
   * @return JSON 节点
   */
  private static ObjectNode encodeOne(Message message) {
    ObjectNode node = MAPPER.createObjectNode();
    if (message instanceof UserMessage user) {
      node.put(TYPE, TYPE_USER);
      node.put(TEXT, nullToEmpty(user.getText()));
    } else if (message instanceof AssistantMessage assistant) {
      node.put(TYPE, TYPE_ASSISTANT);
      node.put(TEXT, nullToEmpty(assistant.getText()));
      ArrayNode calls = node.putArray(TOOL_CALLS);
      for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
        ObjectNode item = calls.addObject();
        item.put("id", nullToEmpty(call.id()));
        item.put(TYPE, nullToEmpty(call.type()));
        item.put("name", nullToEmpty(call.name()));
        item.put("arguments", nullToEmpty(call.arguments()));
      }
    } else if (message instanceof ToolResponseMessage tool) {
      node.put(TYPE, TYPE_TOOL);
      ArrayNode responses = node.putArray(RESPONSES);
      for (ToolResponseMessage.ToolResponse response : tool.getResponses()) {
        ObjectNode item = responses.addObject();
        item.put("id", nullToEmpty(response.id()));
        item.put("name", nullToEmpty(response.name()));
        item.put("data", nullToEmpty(response.responseData()));
      }
    } else {
      throw new BizException(
          ErrorCode.INTERNAL_ERROR, "会话历史含不支持的消息类型: " + message.getClass().getName());
    }
    return node;
  }

  /**
   * 解码单条消息。
   *
   * @param node JSON 节点
   * @return 消息
   */
  private static Message decodeOne(JsonNode node) {
    String type = node.path(TYPE).asText("");
    if (TYPE_USER.equals(type)) {
      return new UserMessage(node.path(TEXT).asText(""));
    }
    if (TYPE_ASSISTANT.equals(type)) {
      List<AssistantMessage.ToolCall> calls = new ArrayList<>();
      for (JsonNode item : node.path(TOOL_CALLS)) {
        calls.add(
            new AssistantMessage.ToolCall(
                item.path("id").asText(""),
                item.path(TYPE).asText(""),
                item.path("name").asText(""),
                item.path("arguments").asText("")));
      }
      return AssistantMessage.builder()
          .content(node.path(TEXT).asText(""))
          .toolCalls(calls)
          .build();
    }
    if (TYPE_TOOL.equals(type)) {
      List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
      for (JsonNode item : node.path(RESPONSES)) {
        responses.add(
            new ToolResponseMessage.ToolResponse(
                item.path("id").asText(""),
                item.path("name").asText(""),
                item.path("data").asText("")));
      }
      return ToolResponseMessage.builder().responses(responses).build();
    }
    throw new BizException(ErrorCode.INTERNAL_ERROR, "会话历史含未知消息类型: " + type);
  }

  /**
   * null 转空串,保证 JSON 里字段总是字符串。
   *
   * @param value 原值
   * @return 非 null 的字符串
   */
  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
