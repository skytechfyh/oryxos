package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.Profile.ProviderRef;
import io.oryxos.storage.LlmCall;
import io.oryxos.storage.LlmCallRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 集成冒烟:读环境变量里的真 key、真调一次模型。验证"key 对、依赖对、真的通"。
 *
 * <p>日常与 CI 不跑(打了 integration 标签);手动跑法见 specs/001-agent-provider/quickstart.md。
 */
// 类名 ProviderSmokeIT 是课件固定字面量,IT 后缀是集成测试的通用约定,不能改名
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
@SpringBootTest(
    classes = ProviderSmokeIT.Config.class,
    properties = {
      "oryxos.providers[0].name=deepseek",
      "oryxos.providers[0].base-url=https://api.deepseek.com",
      "oryxos.providers[0].api-key=${DEEPSEEK_API_KEY}",
      "spring.datasource.url=jdbc:sqlite::memory:",
      "spring.datasource.driver-class-name=org.sqlite.JDBC",
      "spring.datasource.hikari.maximum-pool-size=1",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=classpath:schema.sql"
    })
class ProviderSmokeIT {

  @Autowired private ProviderService providerService;

  @Autowired private LlmCallRepository llmCalls;

  @Test
  @DisplayName("真调一次模型_拿到非空响应且审计多一条success为true")
  void realCallReturnsContentAndAuditsSuccess() {
    long before = llmCalls.count();
    Profile profile =
        new Profile(
            "smoke",
            null,
            null,
            new ProviderRef("deepseek", "deepseek-chat", 0.0),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    ChatResponse response = providerService.chat("smoke-1", profile, new Prompt("只回复一个字:好"));

    assertThat(response.getResult().getOutput().getText()).isNotBlank();
    List<LlmCall> all = llmCalls.findAll();
    assertThat(all).hasSize((int) before + 1);
    assertThat(all.get(all.size() - 1).isSuccess()).isTrue();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure."
            + "OpenAiAudioTranscriptionAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration"
      })
  @EntityScan("io.oryxos.storage")
  @EnableJpaRepositories("io.oryxos.storage")
  @Import({ProviderConfiguration.class, ToolSchemaAdapter.class, LlmCallAuditor.class})
  static class Config {}
}
