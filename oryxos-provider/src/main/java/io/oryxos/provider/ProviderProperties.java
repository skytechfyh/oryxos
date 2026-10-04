package io.oryxos.provider;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全局层配置 {@code oryxos.providers}:声明实例上接了哪些 provider、凭证从哪个环境变量读。
 *
 * <p>只管"连不连得上";用哪个 model、什么温度由 Profile 层决定。
 */
@ConfigurationProperties(prefix = "oryxos")
public record ProviderProperties(List<Provider> providers) {

  public ProviderProperties {
    providers = providers == null ? List.of() : List.copyOf(providers);
  }

  /**
   * 一个 provider 的连接信息。
   *
   * @param name 唯一的 provider 名,Profile 通过它引用
   * @param baseUrl OpenAI 兼容协议的服务地址
   * @param apiKey 凭证,配置里写 {@code ${XXX_API_KEY:}} 环境变量占位,不落明文
   */
  public record Provider(String name, String baseUrl, String apiKey) {}
}
