package io.oryxos.boot;

import io.oryxos.core.profile.ProfileLoader;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.provider.ProviderService;
import java.nio.file.Path;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 启动接线:provider 就绪后扫描 Profile 目录并注册到内存索引。 */
@Configuration
public class ProfileConfiguration {

  /** Profile 目录约定,见 TechnicalSolution §8.2。 */
  private static final Path PROFILES_DIR = Path.of(".oryxos", "profiles");

  @Bean
  public ProfileRegistry profileRegistry() {
    return new ProfileRegistry();
  }

  @Bean
  public ApplicationRunner profileLoadRunner(
      ProfileRegistry registry, ProviderService providerService) {
    // 以 provider 映射表的键集作为"全局层已接入"的事实来源:缺 key 被跳过的 provider 不在其中
    return args ->
        new ProfileLoader(PROFILES_DIR, providerService.providerNames(), System::getenv, registry)
            .load();
  }
}
