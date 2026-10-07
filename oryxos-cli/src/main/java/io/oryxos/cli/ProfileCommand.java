package io.oryxos.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** {@code oryxos profile}:Profile 管理的父命令,本身只打印帮助,干活的是四个子命令。 */
@Command(
    name = "profile",
    mixinStandardHelpOptions = true,
    description = "管理 Agent(Profile)",
    subcommands = {
      ProfileListCommand.class,
      ProfileCreateCommand.class,
      ProfileShowCommand.class,
      ProfileDeleteCommand.class
    })
public class ProfileCommand implements Runnable {

  @Spec private CommandSpec spec;

  /** 无子命令时打印帮助。 */
  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }
}
