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

/** {@code oryxos profile list}:列出 {@code .oryxos/profiles/} 下的 Profile 文件。轻命令,直接读目录,不启动 Spring。 */
@Command(name = "list", mixinStandardHelpOptions = true, description = "列出所有 Profile")
public class ProfileListCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  /**
   * 列文件名。
   *
   * @return 退出码 0
   * @throws IOException 列目录失败
   */
  @Override
  public Integer call() throws IOException {
    PrintWriter out = spec.commandLine().getOut();
    Path dir = WorkspacePaths.profilesDir();
    if (!Files.isDirectory(dir)) {
      out.println("暂无 Profile(目录不存在,先执行 init)");
      return 0;
    }
    try (Stream<Path> files = Files.list(dir)) {
      files
          .map(p -> String.valueOf(p.getFileName()))
          .filter(n -> n.endsWith(".yaml") || n.endsWith(".yml"))
          .sorted()
          .forEach(out::println);
    }
    return 0;
  }
}
