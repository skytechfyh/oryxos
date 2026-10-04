package io.oryxos.core.tool;

/**
 * 所有 Tool 的统一抽象。
 *
 * <p>第 16 节提供"参数说明"契约,供 Provider 翻译成模型能理解的工具描述;第 17 节补上 {@link #execute},供 ToolExecutor 统一执行;内置
 * Tool、MCP 等具体实现与 ToolRegistry 由第 20 节补全。
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

  /**
   * 执行工具。调度权只在 ToolExecutor 一处,Provider 与模型框架都不得自动调用它。
   *
   * @param inputJson 模型给出的入参 JSON 原文
   * @return 执行结果,失败用 {@link ToolResult#fail} 表达
   */
  ToolResult execute(String inputJson);
}
