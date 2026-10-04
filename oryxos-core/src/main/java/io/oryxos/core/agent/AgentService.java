package io.oryxos.core.agent;

import io.oryxos.core.BizException;
import io.oryxos.core.ErrorCode;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;

/**
 * 一次处理的编排者。
 *
 * <p>CLI、Web、定时三种触发源最终都调同一个 {@link #process}。它在入口把当前 Profile 放进 {@link
 * ProfileContext}、出口(含异常)清掉,并在处理完把累积的历史持久化。
 */
public class AgentService {

  private final ProfileRegistry profileRegistry;
  private final ReActLoop reActLoop;
  private final SessionManager sessionManager;

  /**
   * 构造服务。
   *
   * @param profileRegistry Profile 索引
   * @param reActLoop ReAct 循环
   * @param sessionManager 会话管理
   */
  public AgentService(
      ProfileRegistry profileRegistry, ReActLoop reActLoop, SessionManager sessionManager) {
    this.profileRegistry = profileRegistry;
    this.reActLoop = reActLoop;
    this.sessionManager = sessionManager;
  }

  /**
   * 处理一条用户消息。
   *
   * @param session 当前会话
   * @param userMessage 用户输入
   * @return Agent 的最终答复
   * @throws BizException 会话引用的 Profile 不存在
   */
  public String process(Session session, String userMessage) {
    Profile profile =
        profileRegistry
            .find(session.profileName())
            .orElseThrow(
                () ->
                    new BizException(ErrorCode.NOT_FOUND, "Profile 不存在: " + session.profileName()));
    // 工具执行时靠它知道"当前是哪个 Agent"
    ProfileContext.set(profile);
    try {
      String reply = reActLoop.run(session, userMessage, profile);
      sessionManager.save(session);
      return reply;
    } finally {
      // 线程可能被复用,用完必须清,否则串号
      ProfileContext.clear();
    }
  }
}
