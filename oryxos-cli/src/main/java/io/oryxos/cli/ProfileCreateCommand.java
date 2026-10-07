package io.oryxos.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code oryxos profile create <name>}:写入最小 Profile 模板。轻命令。
 *
 * <p>已存在则报错,不覆盖;名称经 {@link WorkspacePaths} 校验,防路径穿越。写文件处第 24 节落地后需接入 {@code SandboxChecker}。
 */
@Command(name = "create", mixinStandardHelpOptions = true, description = "创建一个最小 Profile")
public class ProfileCreateCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Parameters(index = "0", paramLabel = "<name>", description = "Profile 名")
  private String name;

  /**
   * 创建 Profile 文件。
   *
   * @return 成功 0;已存在 1
   * @throws IOException 写文件失败
   */
  @Override
  public Integer call() throws IOException {
    Path file = WorkspacePaths.profileFile(name);
    if (Files.exists(file)) {
      spec.commandLine().getErr().println("错误: Profile 已存在: " + name);
      return 1;
    }
    Files.createDirectories(WorkspacePaths.profilesDir());
    Files.writeString(file, ProfileTemplates.profile(name, "新建的 Agent"), StandardCharsets.UTF_8);
    spec.commandLine().getOut().println("已创建: " + file);
    return 0;
  }
}
