package io.oryxos.core.tool;

import io.oryxos.core.profile.Profile;
import java.util.List;
import java.util.Optional;

/**
 * 工具注册表的最小契约。
 *
 * <p>第 17 节只需要"按名查找"和"按 Profile 过滤"两个能力,供 ReAct 循环与工具执行器使用;完整实现(内置 Tool、MCP、{@code @Tool} Bean
 * 的统一注册)由第 20 节补全,届时不得改动这里的签名。
 */
public interface ToolRegistry {

  /**
   * 按工具名查找。
   *
   * @param name 工具名
   * @return 找到的工具,不存在返回空
   */
  Optional<OryxTool> find(String name);

  /**
   * 取某个 Profile 可用的工具子集(按 Profile 的 tools 字段过滤)。
   *
   * @param profile 当前 Agent 的 Profile
   * @return 可用工具列表
   */
  List<OryxTool> forProfile(Profile profile);
}
