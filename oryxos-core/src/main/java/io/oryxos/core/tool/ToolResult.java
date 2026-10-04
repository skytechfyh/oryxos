package io.oryxos.core.tool;

/**
 * 一次工具执行的结果。
 *
 * <p>失败也用它表达(而不是抛异常),这样 ReAct 循环能把失败原因回填给模型,让模型自己决定重试或换办法。
 *
 * @param success 是否成功
 * @param content 成功时的结果内容
 * @param errorMessage 失败时的原因
 * @param retryable 失败后是否值得重试
 */
public record ToolResult(boolean success, String content, String errorMessage, boolean retryable) {

  /**
   * 构造成功结果。
   *
   * @param content 结果内容
   * @return 成功的 ToolResult
   */
  public static ToolResult ok(String content) {
    return new ToolResult(true, content, null, false);
  }

  /**
   * 构造失败结果。
   *
   * @param errorMessage 失败原因
   * @param retryable 是否可重试
   * @return 失败的 ToolResult
   */
  public static ToolResult fail(String errorMessage, boolean retryable) {
    return new ToolResult(false, null, errorMessage, retryable);
  }
}
