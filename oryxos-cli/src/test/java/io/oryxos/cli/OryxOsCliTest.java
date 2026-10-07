package io.oryxos.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 命令树回归:12 个子命令都能注册、都有可用的帮助。
 *
 * <p>只在 JVM 内构造命令树并执行 {@code --help},不启动进程也不启动 Spring。它守的是一个真实踩过的坑:同名子命令 (例如三个顶层 {@code list})会让
 * Picocli 在构造时就抛 {@code DuplicateNameException},所有命令一个都起不来,而编译与静态检查都发现不了。 轻重分流本身(轻命令不起
 * Spring)仍属进程级行为,留给人工验收。
 */
class OryxOsCliTest {

  /** 12 个子命令的完整路径。 */
  private static final List<List<String>> COMMANDS =
      List.of(
          List.of("init"),
          List.of("status"),
          List.of("chat"),
          List.of("serve"),
          List.of("gateway"),
          List.of("profile", "list"),
          List.of("profile", "create"),
          List.of("profile", "show"),
          List.of("profile", "delete"),
          List.of("provider", "list"),
          List.of("tool", "list"),
          List.of("session", "list"));

  /** 顶层帮助必须列出全部一级命令,且命令树能无冲突地构造出来。 */
  @Test
  @DisplayName("顶层帮助_列出全部一级命令")
  void topLevelHelpListsAllCommands() {
    Result result = run("--help");

    assertThat(result.code()).isZero();
    assertThat(result.stdout())
        .contains("init", "status", "chat", "serve", "gateway", "profile", "provider", "tool")
        .contains("session");
  }

  /** 12 个子命令各自带 --help 都应成功退出,证明它们都被正确注册。 */
  @Test
  @DisplayName("12个子命令_各自--help都正常")
  void everyCommandHasWorkingHelp() {
    for (List<String> path : COMMANDS) {
      String[] args = new String[path.size() + 1];
      for (int i = 0; i < path.size(); i++) {
        args[i] = path.get(i);
      }
      args[path.size()] = "--help";

      Result result = run(args);

      assertThat(result.code()).as("命令 %s", path).isZero();
      assertThat(result.stdout()).as("命令 %s 的帮助", path).contains("Usage");
    }
  }

  /** 未知命令必须以非零退出,而不是静默成功。 */
  @Test
  @DisplayName("未知命令_非零退出")
  void unknownCommandFails() {
    assertThat(run("no-such-command").code()).isNotZero();
  }

  /**
   * 执行命令行并捕获标准输出与错误输出。
   *
   * <p>启动函数传入一个永不被调用的桩:本测试只跑 {@code --help},不会触发重命令的 Spring 启动。
   *
   * @param args 命令行参数
   * @return 退出码与输出
   */
  private static Result run(String... args) {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      int code = OryxOsCli.run(args, a -> null);
      return new Result(code, out.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }

  /**
   * 一次命令执行的结果。
   *
   * @param code 退出码
   * @param stdout 标准输出文本
   */
  private record Result(int code, String stdout) {}
}
