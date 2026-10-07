package io.oryxos.cli;

import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * OryxOS 命令行入口,注册 12 个子命令。
 *
 * <p>命令按"要不要调模型 / 跑引擎"分两类:轻命令({@code init}、{@code profile *} 等)直接读写文件、不启动 Spring,秒回;重命令 ({@code
 * chat}、{@code serve}、{@code gateway})才启动 Spring 上下文。
 *
 * <p>本模块不依赖 boot(boot 依赖本模块),所以"启动 Spring"这个动作由 boot 以函数形式注入,见 {@link #run}。
 */
@Command(
    name = "oryxos",
    mixinStandardHelpOptions = true,
    description = "OryxOS:分布式 AI Agent OS",
    subcommands = {
      InitCommand.class,
      StatusCommand.class,
      ChatCommand.class,
      ServeCommand.class,
      GatewayCommand.class,
      ProfileCommand.class,
      ProviderCommand.class,
      ToolCommand.class,
      SessionCommand.class
    })
public class OryxOsCli implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(OryxOsCli.class);

  @Spec private CommandSpec spec;

  /** 无子命令时打印帮助。 */
  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }

  /**
   * 执行命令行。
   *
   * @param args 命令行参数
   * @param contextStarter 重命令用它启动 Spring 上下文(入参为传给 Spring 的参数),由 boot 注入
   * @return 进程退出码
   */
  public static int run(
      String[] args, Function<String[], ConfigurableApplicationContext> contextStarter) {
    CommandLine commandLine =
        new CommandLine(new OryxOsCli(), new ContextAwareFactory(contextStarter));
    // 命令里抛出的异常统一成一行中文错误 + 退出码 1:用户输入类错误只提示,意外异常才落带堆栈的日志,都不吞
    commandLine.setExecutionExceptionHandler(
        (ex, cmd, parseResult) -> {
          if (ex instanceof IllegalArgumentException) {
            LOG.debug("命令参数不合法: {}: {}", cmd.getCommandName(), ex.getMessage());
          } else {
            LOG.error("命令执行失败: {}", cmd.getCommandName(), ex);
          }
          cmd.getErr().println("错误: " + ex.getMessage());
          return 1;
        });
    return commandLine.execute(args);
  }

  /** 创建命令对象的工厂:需要启动函数的重命令用带参构造,其余走默认构造。 */
  private static final class ContextAwareFactory implements IFactory {

    private final Function<String[], ConfigurableApplicationContext> contextStarter;

    /**
     * 构造工厂。
     *
     * @param contextStarter Spring 上下文启动函数
     */
    ContextAwareFactory(Function<String[], ConfigurableApplicationContext> contextStarter) {
      this.contextStarter = contextStarter;
    }

    /**
     * 实例化命令类。
     *
     * @param cls 命令类
     * @param <K> 命令类型
     * @return 命令对象
     * @throws Exception 反射实例化失败
     */
    @Override
    public <K> K create(Class<K> cls) throws Exception {
      try {
        return cls.getDeclaredConstructor(Function.class).newInstance(contextStarter);
      } catch (NoSuchMethodException e) {
        return CommandLine.defaultFactory().create(cls);
      }
    }
  }
}
