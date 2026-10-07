package io.oryxos.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** {@code oryxos profile show <name>}:打印该 Profile 的 YAML 原文。轻命令。 */
@Command(name = "show", mixinStandardHelpOptions = true, description = "查看某个 Profile 的内容")
public class ProfileShowCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Parameters(index = "0", paramLabel = "<name>", description = "Profile 名")
  private String name;

  /**
   * 打印文件内容。
   *
   * @return 成功 0;不存在 1
   * @throws IOException 读文件失败
   */
  @Override
  public Integer call() throws IOException {
    Path file = WorkspacePaths.profileFile(name);
    if (!Files.exists(file)) {
      spec.commandLine().getErr().println("错误: Profile 不存在: " + name);
      return 1;
    }
    PrintWriter out = spec.commandLine().getOut();
    out.print(Files.readString(file, StandardCharsets.UTF_8));
    // PrintWriter 只有 println 才自动刷新,print 之后必须手动 flush,否则进程退出时内容丢失
    out.flush();
    return 0;
  }
}
