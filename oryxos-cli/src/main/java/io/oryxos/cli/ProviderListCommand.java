package io.oryxos.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * {@code oryxos provider list}:列出全局层接入的 provider。轻命令,直接读 classpath 里的 {@code application.yaml}。
 *
 * <p>只打印 {@code name} 与 {@code base-url},绝不打印 api-key。
 */
@Command(name = "list", mixinStandardHelpOptions = true, description = "列出已声明的 provider")
public class ProviderListCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  /**
   * 读配置并打印。
   *
   * @return 退出码 0
   * @throws IOException 读取失败
   */
  @Override
  public Integer call() throws IOException {
    PrintWriter out = spec.commandLine().getOut();
    List<?> providers = readProviders();
    if (providers.isEmpty()) {
      out.println("未找到 oryxos.providers 配置");
      return 0;
    }
    for (Object item : providers) {
      if (item instanceof Map<?, ?> provider) {
        out.println(provider.get("name") + "\t" + provider.get("base-url"));
      }
    }
    return 0;
  }

  /**
   * 读取 {@code oryxos.providers} 列表,读不到返回空列表。
   *
   * @return provider 配置项
   * @throws IOException 读取失败
   */
  private List<?> readProviders() throws IOException {
    try (InputStream in = ProviderListCommand.class.getResourceAsStream("/application.yaml")) {
      if (in == null) {
        return List.of();
      }
      // SafeConstructor 只构造基本类型,避免 YAML 反序列化攻击
      Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
      if (root instanceof Map<?, ?> top
          && top.get("oryxos") instanceof Map<?, ?> oryxos
          && oryxos.get("providers") instanceof List<?> providers) {
        return providers;
      }
      return List.of();
    }
  }
}
