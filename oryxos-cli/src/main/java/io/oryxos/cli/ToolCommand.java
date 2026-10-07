package io.oryxos.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** {@code oryxos tool}:查看工具的父命令,本身只打印帮助,干活的是 {@code list} 子命令。 */
@Command(
    name = "tool",
    mixinStandardHelpOptions = true,
    description = "查看工具",
    subcommands = {ToolListCommand.class})
public class ToolCommand implements Runnable {

  @Spec private CommandSpec spec;

  /** 无子命令时打印帮助。 */
  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }
}
