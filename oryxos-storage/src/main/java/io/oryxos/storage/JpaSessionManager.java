package io.oryxos.storage;

import io.oryxos.core.BizException;
import io.oryxos.core.ErrorCode;
import io.oryxos.core.session.SessionManager;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 JPA 的会话管理实现,{@code session_id} 在全仓只在这里生成。
 *
 * <p>所有入口只提供"渠道 + 用户 + Agent 名"三元组;标识的拼接收敛到 {@link #buildSessionId} 一处,避免各入口各拼一遍、格式差一个分隔符,
 * 同一个人就出现两条互不相认的历史。
 */
@Component
public class JpaSessionManager implements SessionManager {

  private static final Logger LOG = LoggerFactory.getLogger(JpaSessionManager.class);

  private final SessionRepository repository;

  /**
   * 构造会话管理。
   *
   * @param repository 会话表仓库
   */
  public JpaSessionManager(SessionRepository repository) {
    this.repository = repository;
  }

  /**
   * 按三元组获取或创建会话,幂等。
   *
   * <p>先查后建;并发时若另一方抢先建成,主键冲突后回读已存在的行,保证同一身份至多一条。
   *
   * @param channel 接入渠道
   * @param user 用户标识
   * @param profileName Agent(Profile)名
   * @return 已有或新建的会话
   * @throws BizException 任一项为空白
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public io.oryxos.core.session.Session getOrCreate(
      String channel, String user, String profileName) {
    String sessionId = buildSessionId(channel, user, profileName);
    Optional<Session> existing = repository.findById(sessionId);
    if (existing.isPresent()) {
      return existing.get();
    }
    try {
      return repository.saveAndFlush(
          new Session(sessionId, profileName, channel, user, Instant.now().toString()));
    } catch (DataIntegrityViolationException e) {
      // 并发竞态:别人刚建好,回读即可;读不到说明不是主键冲突,原样上抛
      LOG.warn("会话并发创建冲突,回读已存在的会话: {}", sessionId);
      return repository.findById(sessionId).orElseThrow(() -> e);
    }
  }

  /**
   * 按会话标识查找会话。
   *
   * @param sessionId 会话标识
   * @return 找到的会话,不存在返回空
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<io.oryxos.core.session.Session> get(String sessionId) {
    return repository.findById(sessionId).map(session -> session);
  }

  /**
   * 持久化会话:历史编码写回、刷新最后活跃时间。
   *
   * @param session 待保存的会话,必须是本实现产生的
   * @throws BizException 传入的不是本实现产生的会话
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void save(io.oryxos.core.session.Session session) {
    if (!(session instanceof Session entity)) {
      throw new BizException(
          ErrorCode.BAD_REQUEST,
          "只能保存由 SessionManager 创建的会话: " + (session == null ? "null" : session.getClass()));
    }
    entity.prepareForSave(Instant.now().toString());
    repository.saveAndFlush(entity);
  }

  /**
   * 生成会话标识:{@code 渠道:用户:Agent名},全仓唯一的拼接处。
   *
   * @param channel 接入渠道
   * @param user 用户标识
   * @param profileName Agent(Profile)名
   * @return 会话标识
   * @throws BizException 任一项为空白
   */
  private static String buildSessionId(String channel, String user, String profileName) {
    return escape("channel", channel)
        + ":"
        + escape("user", user)
        + ":"
        + escape("profileName", profileName);
  }

  /**
   * 转义单个身份项:{@code %} 与 {@code :} 做百分号转义,使分隔符唯一,不同三元组不会碰撞。
   *
   * @param field 字段名,用于报错
   * @param value 字段值
   * @return 转义后的值
   * @throws BizException 值为 null 或空白
   */
  private static String escape(String field, String value) {
    if (value == null || value.isBlank()) {
      throw new BizException(ErrorCode.BAD_REQUEST, "会话身份项不能为空: " + field);
    }
    return value.replace("%", "%25").replace(":", "%3A");
  }
}
