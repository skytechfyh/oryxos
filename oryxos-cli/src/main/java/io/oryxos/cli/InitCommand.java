package io.oryxos.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * {@code oryxos init}:创建 {@code .oryxos/} 工作区的完整结构(TechnicalSolution §8.1)。
 *
 * <p>轻命令,不启动 Spring。已存在的文件一律不覆盖,所以重复执行是安全的。写文件处第 24 节落地后需接入 {@code SandboxChecker}。
 */
@Command(name = "init", mixinStandardHelpOptions = true, description = "初始化 OryxOS 工作区(.oryxos/)")
public class InitCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  /**
   * 创建目录与默认模板。
   *
   * @return 退出码 0
   * @throws IOException 创建目录或写文件失败
   */
  @Override
  public Integer call() throws IOException {
    PrintWriter out = spec.commandLine().getOut();
    Path root = WorkspacePaths.root();
    for (String dir : new String[] {"profiles", "memory", "skills", "sessions", "logs"}) {
      Files.createDirectories(root.resolve(dir));
    }
    createIfAbsent(root.resolve("memory").resolve("MEMORY.md"), "# 长期记忆\n", out);
    createIfAbsent(root.resolve("mcp_servers.yaml"), "# MCP server 配置\nservers: []\n", out);
    createIfAbsent(root.resolve("AGENTS.md"), "# AGENTS\n\n在这里写所有 Agent 共用的行为约定。\n", out);
    createIfAbsent(root.resolve("SOUL.md"), "# SOUL\n\n在这里写 Agent 的性格与价值取向。\n", out);
    createIfAbsent(root.resolve("USER.md"), "# USER\n\n在这里写用户的背景与偏好。\n", out);
    createIfAbsent(
        WorkspacePaths.profileFile("default"),
        ProfileTemplates.profile("default", "默认 Agent"),
        out);
    out.println("工作区已就绪: " + root.toAbsolutePath());
    return 0;
  }

  /**
   * 文件不存在才写入,存在则跳过,保证不覆盖用户已有内容。
   *
   * @param file 目标文件
   * @param content 默认内容
   * @param out 输出
   * @throws IOException 写文件失败
   */
  private static void createIfAbsent(Path file, String content, PrintWriter out)
      throws IOException {
    if (Files.exists(file)) {
      out.println("已存在,跳过: " + file);
      return;
    }
    Files.writeString(file, content, StandardCharsets.UTF_8);
    out.println("已创建: " + file);
  }
}
