package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.agent.ContextLoader;
import io.oryxos.core.agent.PromptBuilder;
import io.oryxos.core.agent.ReActLoop;
import io.oryxos.core.agent.ToolExecutor;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.session.Session;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.storage.LlmCall;
import io.oryxos.storage.LlmCallRepository;
import io.oryxos.storage.ToolInvocation;
import io.oryxos.storage.ToolInvocationAuditor;
import io.oryxos.storage.ToolInvocationRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 第 17 节集成冒烟:真模型 + 离线假天气工具,跑通一次"想→做→看"的多轮 ReAct 循环,并核对两张审计表。
 *
 * <p>key 只通过占位符从环境变量读取,文件里不出现明文;没设置 {@code DEEPSEEK_API_KEY} 时自动跳过。日常与 CI 不跑(打了 integration
 * 标签);手动跑法见 specs/002-react-loop/quickstart.md。工具用离线假数据,是因为 ToolRegistry、http_get 要到第 20 节才交付。
 */
// 类名沿用 ProviderSmokeIT 的 IT 后缀约定(集成测试通用写法),不能改名
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
@SpringBootTest(
    classes = ReActSmokeIT.Config.class,
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
class ReActSmokeIT {

  private static final String SESSION_ID = "react-smoke-1";

  @Autowired private ProviderService providerService;

  @Autowired private ToolInvocationAuditor auditor;

  @Autowired private ToolInvocationRepository toolInvocations;

  @Autowired private LlmCallRepository llmCalls;

  @TempDir Path workspace;

  /** 离线假天气工具:不联网,固定返回一份天气数据,并记录被调用次数。 */
  private static final class WeatherTool implements OryxTool {

    private int calls;

    /** {@inheritDoc} */
    @Override
    public String name() {
      return "get_weather";
    }

    /** {@inheritDoc} */
    @Override
    public String description() {
      return "查询城市今天的天气";
    }

    /** {@inheritDoc} */
    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},"
          + "\"required\":[\"city\"]}";
    }

    /** {@inheritDoc} */
    @Override
    public ToolResult execute(String inputJson) {
      calls++;
      return ToolResult.ok(
          "{\"city\":\"北京\",\"temp_c\":8,\"condition\":\"多云转小雨\",\"wind\":\"北风4级\"}");
    }
  }

  /** 只含一个工具的注册表,第 20 节交付真正的 ToolRegistry 前用它顶替。 */
  private static final class SingleToolRegistry implements ToolRegistry {

    private final OryxTool tool;

    /**
     * 构造注册表。
     *
     * @param tool 唯一的工具
     */
    SingleToolRegistry(OryxTool tool) {
      this.tool = tool;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OryxTool> find(String name) {
      return tool.name().equals(name) ? Optional.of(tool) : Optional.empty();
    }

    /** {@inheritDoc} */
    @Override
    public List<OryxTool> forProfile(Profile profile) {
      return List.of(tool);
    }
  }

  /** 内存会话,第 18 节交付真正的 Session 前用它顶替。 */
  private static final class MemorySession implements Session {

    private final List<Message> messages = new ArrayList<>();

    /** {@inheritDoc} */
    @Override
    public String id() {
      return SESSION_ID;
    }

    /** {@inheritDoc} */
    @Override
    public String profileName() {
      return "weather-agent";
    }

    /** {@inheritDoc} */
    @Override
    public List<Message> messages() {
      return messages;
    }

    /** {@inheritDoc} */
    @Override
    public void append(String userMessage) {
      messages.add(new UserMessage(userMessage));
    }

    /** {@inheritDoc} */
    @Override
    public void append(ChatResponse response) {
      messages.add(response.getResult().getOutput());
    }

    /** {@inheritDoc} */
    @Override
    public void appendToolResult(AssistantMessage.ToolCall call, ToolResult result) {
      String data = result.success() ? result.content() : "ERROR: " + result.errorMessage();
      messages.add(
          ToolResponseMessage.builder()
              .responses(
                  List.of(new ToolResponseMessage.ToolResponse(call.id(), call.name(), data)))
              .build());
    }
  }

  /**
   * 构造天气 Agent 的 Profile:只开放 get_weather,最多转 5 轮,温度 0 让结果稳定。
   *
   * @return Profile
   */
  private static Profile weatherProfile() {
    return new Profile(
        "weather-agent",
        "天气助手",
        new Profile.Identity("小天", "你是天气助手。需要天气数据时必须调用 get_weather 工具,拿到数据后用中文简短给出穿搭建议。"),
        new Profile.ProviderRef("deepseek", "deepseek-chat", 0.0),
        List.of("get_weather"),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new Profile.Settings(5, null));
  }

  /** 真模型自己决定调工具、看到结果后给出建议;两张审计表各留下对应记录。 */
  @Test
  @DisplayName("真模型多轮ReAct_调了工具给出建议且两张审计表都有记录")
  void realModelCallsToolThenAnswersAndAuditsBothTables() {
    WeatherTool weather = new WeatherTool();
    ToolRegistry registry = new SingleToolRegistry(weather);
    ReActLoop loop =
        new ReActLoop(
            new PromptBuilder(
                new ContextLoader(workspace), registry, null, Clock.systemDefaultZone()),
            providerService,
            new ToolExecutor(registry, auditor));
    MemorySession session = new MemorySession();

    String reply = loop.run(session, "看看北京今天天气,帮我决定穿什么", weatherProfile());

    assertThat(reply).isNotBlank();
    assertThat(weather.calls).isGreaterThanOrEqualTo(1);
    // 用户消息、工具调用请求、工具结果、最终答复,至少 4 条
    assertThat(session.messages()).hasSizeGreaterThanOrEqualTo(4);
    List<ToolInvocation> invocations = toolInvocations.findBySessionId(SESSION_ID);
    assertThat(invocations).isNotEmpty();
    assertThat(invocations).allMatch(ToolInvocation::isSuccess);
    assertThat(invocations.get(0).getToolName()).isEqualTo("get_weather");
    List<LlmCall> modelCalls =
        llmCalls.findAll().stream().filter(c -> SESSION_ID.equals(c.getSessionId())).toList();
    assertThat(modelCalls).hasSizeGreaterThanOrEqualTo(2);
    assertThat(modelCalls).allMatch(LlmCall::isSuccess);
  }

  /** 测试用的最小 Spring 配置:显式排除 Spring AI 的自动装配,模型由 ProviderConfiguration 手工构建。 */
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
  @Import({
    ProviderConfiguration.class,
    ToolSchemaAdapter.class,
    LlmCallAuditor.class,
    ToolInvocationAuditor.class
  })
  static class Config {}
}
