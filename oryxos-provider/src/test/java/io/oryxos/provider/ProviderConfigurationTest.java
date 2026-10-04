package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.oryxos.provider.ProviderProperties.Provider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class ProviderConfigurationTest {

  private static Provider provider(String name, String apiKey) {
    return new Provider(name, "https://example.invalid", apiKey);
  }

  private static Map<String, ChatModel> build(List<Provider> providers) {
    return ProviderConfiguration.buildChatModels(providers, p -> mock(ChatModel.class));
  }

  @Test
  @DisplayName("凭证齐全的provider_全部进入映射表")
  void providersWithCredentialsEnterMap() {
    Map<String, ChatModel> models =
        build(List.of(provider("deepseek", "k1"), provider("kimi", "k2")));

    assertThat(models).containsOnlyKeys("deepseek", "kimi");
  }

  @Test
  @DisplayName("缺key的provider_被跳过且不阻断其他")
  void providerWithoutKeyIsSkipped() {
    // ${XXX_API_KEY:} 在环境变量未设置时解析为空串
    Map<String, ChatModel> models =
        build(List.of(provider("deepseek", ""), provider("kimi", "k2")));

    assertThat(models).containsOnlyKeys("kimi");
  }

  @Test
  @DisplayName("key为null或空白_同样被跳过")
  void nullOrBlankKeyIsSkipped() {
    Map<String, ChatModel> models =
        build(List.of(provider("a", null), provider("b", "  "), provider("c", "k")));

    assertThat(models).containsOnlyKeys("c");
  }

  @Test
  @DisplayName("重复的provider名_后者被跳过不覆盖前者")
  void duplicateProviderNameKeepsFirst() {
    ChatModel first = mock(ChatModel.class);
    ChatModel second = mock(ChatModel.class);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    Map<String, ChatModel> models =
        ProviderConfiguration.buildChatModels(
            List.of(provider("deepseek", "k1"), provider("deepseek", "k2")),
            p -> calls.getAndIncrement() == 0 ? first : second);

    assertThat(models).containsOnlyKeys("deepseek");
    assertThat(models.get("deepseek")).isSameAs(first);
  }

  @Test
  @DisplayName("name或baseUrl为空_被跳过")
  void blankNameOrBaseUrlIsSkipped() {
    Map<String, ChatModel> models =
        build(
            List.of(
                new Provider("", "https://x", "k"),
                new Provider("nourl", "", "k"),
                provider("ok", "k")));

    assertThat(models).containsOnlyKeys("ok");
  }

  @Test
  @DisplayName("单个模型构建失败_只跳过该家")
  void factoryFailureSkipsOnlyThatProvider() {
    Map<String, ChatModel> models =
        ProviderConfiguration.buildChatModels(
            List.of(provider("bad", "k"), provider("good", "k")),
            p -> {
              if ("bad".equals(p.name())) {
                throw new IllegalStateException("boom");
              }
              return mock(ChatModel.class);
            });

    assertThat(models).containsOnlyKeys("good");
  }
}
