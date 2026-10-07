package io.oryxos.core.session;

import java.util.Optional;

/**
 * 会话管理契约。
 *
 * <p>所有入口(CLI 传 {@code "cli"}、Web 传 {@code "web"}、定时传 {@code "scheduler"})只提供"渠道 + 用户 + Agent
 * 名"三元组,会话标识 {@code session_id} 只在实现内部生成,入口不得自行拼接;否则两处各拼一遍、格式差一个分隔符,同一个人就会出现两条互不相认的历史。
 *
 * <p>{@link #save} 为第 17 节既有契约,签名不得改动。
 */
public interface SessionManager {

  /**
   * 按身份三元组获取或创建会话,幂等:同一三元组始终返回同一个会话。
   *
   * @param channel 接入渠道
   * @param user 用户标识
   * @param profileName Agent(Profile)名
   * @return 已有或新建的会话
   */
  Session getOrCreate(String channel, String user, String profileName);

  /**
   * 按会话标识查找会话。
   *
   * @param sessionId 会话标识
   * @return 找到的会话,不存在返回空
   */
  Optional<Session> get(String sessionId);

  /**
   * 持久化会话(含累积完的对话历史)。
   *
   * @param session 待保存的会话
   */
  void save(Session session);
}
