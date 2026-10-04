package io.oryxos.core.session;

import io.oryxos.core.tool.ToolResult;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 一次对话的全部状态的最小契约。
 *
 * <p>第 17 节的 ReAct 循环只需要读写消息序列;持久化实体与 {@code session_id} 的拼接规则由第 18 节补全,届时不得改动这里的签名。
 */
public interface Session {

  /**
   * 会话标识,用于关联 llm_calls 与 tool_invocations 审计。
   *
   * @return 会话标识
   */
  String id();

  /**
   * 该会话所属的 Profile 名。
   *
   * @return Profile 名
   */
  String profileName();

  /**
   * 完整的对话消息序列(含模型响应与工具结果),按时间顺序。
   *
   * @return 消息列表
   */
  List<Message> messages();

  /**
   * 追加一条用户消息。
   *
   * @param userMessage 用户输入
   */
  void append(String userMessage);

  /**
   * 追加一轮模型响应,保证可审计、下一轮可衔接。
   *
   * @param response 模型响应
   */
  void append(ChatResponse response);

  /**
   * 追加一次工具执行结果。需要带上原调用,因为模型靠调用 id 把结果对应回它发出的请求。
   *
   * @param call 模型发起的工具调用
   * @param result 工具执行结果
   */
  void appendToolResult(AssistantMessage.ToolCall call, ToolResult result);
}
