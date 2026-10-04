package io.oryxos.core.agent;

import io.oryxos.core.profile.Profile;

/**
 * 处理期间对工具可见的"当前 Agent 配置"。
 *
 * <p>{@code OryxTool.execute} 的签名不带 Profile,但有些工具需要知道当前 Agent(例如 notify 要读当前 Profile
 * 的通知渠道)。改工具接口代价太大,所以由 {@link AgentService}
 * 在入口放入、出口清掉。虚拟线程下每个请求独占一个线程,天然不串;但线程可能被复用,所以出口必须清,不清就会把别人的 Profile 带给下一个请求。
 */
public final class ProfileContext {

  private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

  /** 工具类,禁止实例化。 */
  private ProfileContext() {}

  /**
   * 放入当前线程的 Profile。
   *
   * @param profile 当前 Agent 的 Profile
   */
  public static void set(Profile profile) {
    CURRENT.set(profile);
  }

  /**
   * 取当前线程的 Profile。
   *
   * @return 当前 Profile,未设置返回 null
   */
  public static Profile current() {
    return CURRENT.get();
  }

  /** 清除当前线程的 Profile,必须在 finally 中调用。 */
  public static void clear() {
    CURRENT.remove();
  }
}
