package io.oryxos.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** {@code oryxos status}:看一眼当前工作区状态。轻命令,只查文件系统,不启动 Spring。 */
@Command(name = "status", mixinStandardHelpOptions = true, description = "查看工作区与数据库状态")
public class StatusCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  /**
   * 打印工作区是否存在、Profile 数量、数据库文件是否存在。
   *
   * @return 退出码 0
   * @throws IOException 列目录失败
   */
  @Override
  public Integer call() throws IOException {
    PrintWriter out = spec.commandLine().getOut();
    Path root = WorkspacePaths.root();
    out.println(
        "工作区: " + root.toAbsolutePath() + (Files.isDirectory(root) ? " (存在)" : " (不存在,先执行 init)"));
    out.println("Profile 数量: " + countProfiles());
    Path db = WorkspacePaths.dbPath();
    out.println("数据库: " + db + (Files.exists(db) ? " (存在)" : " (尚未创建)"));
    return 0;
  }

  /**
   * 统计 Profile 目录下的 YAML 文件数。
   *
   * @return 数量,目录不存在为 0
   * @throws IOException 列目录失败
   */
  private static long countProfiles() throws IOException {
    Path dir = WorkspacePaths.profilesDir();
    if (!Files.isDirectory(dir)) {
      return 0;
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
          .count();
    }
  }
}
