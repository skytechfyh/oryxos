package io.oryxos.core.tool;

/**
 * 所有 Tool 的统一抽象。
 *
 * <p>第 16 节只需要"参数说明"这一最小契约,供 Provider 翻译成模型能理解的工具描述;执行方法与 ToolResult 由第 20 节补全。
 */
public interface OryxTool {

  /**
   * 工具名,模型靠它指明要调用哪个工具。
   *
   * @return 工具名
   */
  String name();

  /**
   * 给模型看的用途说明。
   *
   * @return 用途说明
   */
  String description();

  /**
   * 入参的 JSON Schema 字符串。
   *
   * @return JSON Schema
   */
  String getInputSchema();
}
