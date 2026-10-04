package io.oryxos.core.session;

/**
 * 会话管理的最小契约。
 *
 * <p>第 17 节只需要"处理完把累积的历史存下来";创建、查找、归档与 {@code session_id} 拼接由第 18 节补全,届时不得改动这里的签名。
 */
public interface SessionManager {

  /**
   * 持久化会话(含累积完的对话历史)。
   *
   * @param session 待保存的会话
   */
  void save(Session session);
}
