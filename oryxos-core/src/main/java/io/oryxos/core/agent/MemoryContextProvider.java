package io.oryxos.core.agent;

import io.oryxos.core.profile.Profile;
import java.util.Optional;

/**
 * 长期记忆的可选提供者。
 *
 * <p>没有该 Bean(第 22 节之前)时,提示组装直接跳过"长期记忆"这一部分。长期记忆是跨会话都在的内容,与会话历史是两回事,不要混在一起。
 */
public interface MemoryContextProvider {

  /**
   * 取要注入 system prompt 之后的长期记忆文本。
   *
   * @param profile 当前 Agent 的 Profile
   * @return 记忆文本,没有则返回空
   */
  Optional<String> longTermMemory(Profile profile);
}
