package io.oryxos.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code oryxos profile delete <name>}:删除该 Profile 文件。轻命令。
 *
 * <p>名称经 {@link WorkspacePaths} 校验,只能删 {@code .oryxos/profiles/} 内的 YAML。删文件处第 24 节落地后需接入 {@code
 * SandboxChecker}。
 */
@Command(name = "delete", mixinStandardHelpOptions = true, description = "删除一个 Profile")
public class ProfileDeleteCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Parameters(index = "0", paramLabel = "<name>", description = "Profile 名")
  private String name;

  /**
   * 删除文件。
   *
   * @return 成功 0;不存在 1
   * @throws IOException 删除失败
   */
  @Override
  public Integer call() throws IOException {
    Path file = WorkspacePaths.profileFile(name);
    if (!Files.deleteIfExists(file)) {
      spec.commandLine().getErr().println("错误: Profile 不存在: " + name);
      return 1;
    }
    spec.commandLine().getOut().println("已删除: " + file);
    return 0;
  }
}
