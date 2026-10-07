package io.oryxos.cli;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Unmatched;

/**
 * {@code oryxos gateway}:守护进程模式,同时挂多个通道。
 *
 * <p>重命令。本节只做"启动并阻塞"的入口骨架,不实现任何 IM 通道。
 */
@Command(
    name = "gateway",
    mixinStandardHelpOptions = true,
    description = "以守护进程方式运行,同时挂多个通道(--spring.* 参数透传给 Spring)")
public class GatewayCommand implements Callable<Integer> {

  private final Function<String[], ConfigurableApplicationContext> contextStarter;

  @Unmatched private List<String> springArgs;

  /**
   * 构造命令。
   *
   * @param contextStarter 启动 Spring 上下文的函数,由 boot 注入
   */
  public GatewayCommand(Function<String[], ConfigurableApplicationContext> contextStarter) {
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
