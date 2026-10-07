package io.oryxos.cli;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Unmatched;

/**
 * {@code oryxos serve}:启动 Web Service。
 *
 * <p>重命令,这里只是"以重命令方式启动并阻塞"的入口骨架,Web Service 的细节留到第 26 节。{@code --spring.*} 等未识别参数原样透传给 Spring,兼容
 * {@code --spring.profiles.active=prod} 这类用法。
 */
@Command(
    name = "serve",
    mixinStandardHelpOptions = true,
    description = "启动 Web Service(--spring.* 参数透传给 Spring)")
public class ServeCommand implements Callable<Integer> {

  private final Function<String[], ConfigurableApplicationContext> contextStarter;

  @Unmatched private List<String> springArgs;

  /**
   * 构造命令。
   *
   * @param contextStarter 启动 Spring 上下文的函数,由 boot 注入
   */
  public ServeCommand(Function<String[], ConfigurableApplicationContext> contextStarter) {
    this.contextStarter = contextStarter;
  }

  /**
   * 启动容器并阻塞到关闭。
   *
   * @return 退出码 0
   * @throws InterruptedException 等待被中断
   */
  @Override
  public Integer call() throws InterruptedException {
    String[] args = springArgs == null ? new String[0] : springArgs.toArray(new String[0]);
    ContextBlocker.awaitClose(contextStarter.apply(args));
    return 0;
  }
}
