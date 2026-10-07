package io.oryxos.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * {@code oryxos tool list}:列出已注册工具。
 *
 * <p>当前只是占位:完整的 {@code ToolRegistry}(内置 Tool、MCP、{@code @Tool} Bean)由第 20 节交付,现有契约又没有"列全部"的方法
 * 且不允许改签名,所以这里如实说明,而不是编造内容。
 */
@Command(name = "list", mixinStandardHelpOptions = true, description = "列出已注册的工具")
public class ToolListCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  /**
   * 打印占位提示。
   *
   * @return 退出码 0
   */
  @Override
  public Integer call() {
    spec.commandLine().getOut().println("尚无已注册工具(完整工具注册表由第 20 节交付)");
    return 0;
  }
}
