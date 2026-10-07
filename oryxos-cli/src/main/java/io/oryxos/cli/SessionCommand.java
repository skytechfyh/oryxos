package io.oryxos.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** {@code oryxos session}:查看会话的父命令,本身只打印帮助,干活的是 {@code list} 子命令。 */
@Command(
    name = "session",
    mixinStandardHelpOptions = true,
    description = "查看会话",
    subcommands = {SessionListCommand.class})
public class SessionCommand implements Runnable {

  @Spec private CommandSpec spec;

  /** 无子命令时打印帮助。 */
  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }
}
