package io.oryxos.provider;

import io.oryxos.provider.ProviderProperties.Provider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按全局层声明逐个创建 ChatModel,显式建立 provider 名到模型的映射表。
 *
 * <p>不用 Spring AI 的自动装配(无 key 会阻断启动),也不靠扫描容器里的 ChatModel Bean 区分 provider。
 */
@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ProviderConfiguration.class);

  @Bean
  public ProviderService providerService(
      ProviderProperties properties, ToolSchemaAdapter adapter, LlmCallAuditor audit) {
    return new ProviderService(
        buildChatModels(properties.providers(), ProviderConfiguration::openAiCompatible),
        adapter,
        audit);
  }

  /**
   * 建表规则:名称为空、base-url 为空、凭证为空(环境变量未设置)、名称重复、构建失败的 provider 都记错误日志并跳过, 不阻断启动;引用它的 Profile
   * 会按"provider 找不到"报错。
   */
  static Map<String, ChatModel> buildChatModels(
      List<Provider> providers, Function<Provider, ChatModel> factory) {
    Map<String, ChatModel> models = new LinkedHashMap<>();
    for (Provider provider : providers) {
      String name = provider.name();
      if (isBlank(name)) {
        LOG.error("跳过 provider:name 为空");
      } else if (models.containsKey(name)) {
        LOG.error("跳过 provider {}:名称重复,保留先声明的一份", name);
      } else if (isBlank(provider.baseUrl())) {
        LOG.error("跳过 provider {}:未配置 base-url", name);
      } else if (isBlank(provider.apiKey())) {
        LOG.error("跳过 provider {}:凭证为空,请检查对应的 API key 环境变量是否已设置", name);
      } else {
        try {
          models.put(name, factory.apply(provider));
        } catch (RuntimeException e) {
          LOG.error("跳过 provider {}:模型构建失败", name, e);
        }
      }
    }
    return models;
  }

  /** deepseek、kimi 等均为 OpenAI 兼容协议,按 base-url + api-key 各建一个模型。 */
  private static ChatModel openAiCompatible(Provider provider) {
    OpenAiApi api =
        OpenAiApi.builder().baseUrl(provider.baseUrl()).apiKey(provider.apiKey()).build();
    return OpenAiChatModel.builder()
        .openAiApi(api)
        .defaultOptions(OpenAiChatOptions.builder().internalToolExecutionEnabled(false).build())
        .build();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
