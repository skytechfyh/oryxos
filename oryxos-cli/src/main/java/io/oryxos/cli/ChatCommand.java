package io.oryxos.cli;

import io.oryxos.channel.cli.CliChannel;
import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.SessionManager;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code oryxos chat}:在终端里和 Agent 交互式对话。
 *
 * <p>重命令:要调模型、跑引擎,才启动 Spring 上下文。以 {@code web-application-type=none} 启动,终端对话不需要 Tomcat, 否则会和已运行的
 * {@code serve} 抢端口。命令本身不含任何 Agent 逻辑,真正的"读—转交—打印"在 {@link CliChannel}。
 */
@Command(name = "chat", mixinStandardHelpOptions = true, description = "在终端里和 Agent 交互式对话")
public class ChatCommand implements Callable<Integer> {

  private final Function<String[], ConfigurableApplicationContext> contextStarter;

  @Option(
      names = "--profile",
      defaultValue = "default",
      description = "要对话的 Agent,默认 ${DEFAULT-VALUE}")
  private String profileName;

  /**
   * 构造命令。
   *
   * @param contextStarter 启动 Spring 上下文的函数,由 boot 注入
   */
  public ChatCommand(Function<String[], ConfigurableApplicationContext> contextStarter) {
    this.contextStarter = contextStarter;
  }

  /**
   * 启动容器、组装通道并进入对话循环,结束后关闭容器。
   *
   * @return 退出码,取自 {@link CliChannel#run}
   */
  @Override
  public Integer call() {
    try (ConfigurableApplicationContext context =
        contextStarter.apply(new String[] {"--spring.main.web-application-type=none"})) {
      // System.in/out/err 只在这个装配处出现一次,通道内部只用注入的流
      CliChannel channel =
          new CliChannel(
              context.getBean(AgentService.class),
              context.getBean(SessionManager.class),
              context.getBean(ProfileRegistry.class),
              System.in,
              System.out,
              System.err);
      return channel.run(profileName, currentUser());
    }
  }

  /**
   * 当前用户:取操作系统登录名;取不到时用固定值,保证会话身份非空。
   *
   * @return 用户标识
   */
  private static String currentUser() {
    String user = System.getProperty("user.name");
    return user == null || user.isBlank() ? "local" : user;
  }
}
