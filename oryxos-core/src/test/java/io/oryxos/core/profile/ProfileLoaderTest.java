package io.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class ProfileLoaderTest {

  private static final String FULL_YAML =
      """
      name: ops-agent
      description: 运维助手
      identity:
        agent_name: 小运
        prompt: 你是运维助手
      provider:
        name: deepseek
        model: deepseek-chat
        temperature: 0.7
      tools: [http_get, read_file]
      skills: [ops-runbook]
      mcp_servers: [github]
      channels: [cli]
      notify_channels: [dingtalk]
      schedules:
        - cron: "0 9 * * *"
          prompt: 早报
      bootstrap: [AGENTS.md]
      settings:
        max_iterations: 8
        max_history_turns: 20
      """;

  @TempDir Path dir;

  private final ProfileRegistry registry = new ProfileRegistry();
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private Logger loaderLogger;

  @BeforeEach
  void captureLogs() {
    loaderLogger = (Logger) LoggerFactory.getLogger(ProfileLoader.class);
    logs.start();
    loaderLogger.addAppender(logs);
  }

  @AfterEach
  void releaseLogs() {
    loaderLogger.detachAppender(logs);
  }

  private ProfileLoader loader(Set<String> providers, Map<String, String> env) {
    Function<String, String> lookup = env::get;
    return new ProfileLoader(dir, providers, lookup, registry);
  }

  private void write(String file, String content) throws IOException {
    Files.writeString(dir.resolve(file), content);
  }

  private String errors() {
    return logs.list.stream()
        .filter(e -> e.getLevel() == Level.ERROR)
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (a, b) -> a + "\n" + b);
  }

  @Test
  @DisplayName("合法YAML_全字段解析")
  void parsesAllFieldsFromValidYaml() throws IOException {
    write("ops-agent.yaml", FULL_YAML);

    int loaded = loader(Set.of("deepseek"), Map.of()).load();

    assertThat(loaded).isEqualTo(1);
    Profile p = registry.find("ops-agent").orElseThrow();
    assertThat(p.description()).isEqualTo("运维助手");
    assertThat(p.identity().agentName()).isEqualTo("小运");
    assertThat(p.identity().prompt()).isEqualTo("你是运维助手");
    assertThat(p.provider().name()).isEqualTo("deepseek");
    assertThat(p.provider().model()).isEqualTo("deepseek-chat");
    assertThat(p.provider().temperature()).isEqualTo(0.7);
    assertThat(p.tools()).containsExactly("http_get", "read_file");
    assertThat(p.skills()).containsExactly("ops-runbook");
    assertThat(p.mcpServers()).containsExactly("github");
    assertThat(p.channels()).containsExactly("cli");
    assertThat(p.notifyChannels()).containsExactly("dingtalk");
    assertThat(p.schedules()).hasSize(1);
    assertThat(p.schedules().get(0)).containsEntry("cron", "0 9 * * *");
    assertThat(p.bootstrap()).containsExactly("AGENTS.md");
    assertThat(p.settings().maxIterations()).isEqualTo(8);
    assertThat(p.settings().maxHistoryTurns()).isEqualTo(20);
  }

  @Test
  @DisplayName("只写必填字段_其余缺省为空")
  void omittedOptionalFieldsDefaultToEmpty() throws IOException {
    write("min.yaml", "name: min\nprovider:\n  name: deepseek\n  model: m\n");

    loader(Set.of("deepseek"), Map.of()).load();

    Profile p = registry.find("min").orElseThrow();
    assertThat(p.tools()).isEmpty();
    assertThat(p.notifyChannels()).isEmpty();
    assertThat(p.settings().maxIterations()).isNull();
  }

  @Test
  @DisplayName("引用不存在的provider_报错清晰且不入索引")
  void unknownProviderReferenceIsRejectedWithClearError() throws IOException {
    write("bad-provider.yaml", FULL_YAML.replace("name: deepseek", "name: nope"));

    int loaded = loader(Set.of("deepseek"), Map.of()).load();

    assertThat(loaded).isZero();
    assertThat(registry.find("ops-agent")).isEmpty();
    assertThat(errors()).contains("bad-provider.yaml").contains("nope");
  }

  @Test
  @DisplayName("坏文件不阻断其余加载")
  void brokenFilesDoNotBlockOthers() throws IOException {
    write("a-broken.yaml", "name: [unclosed\n  : :");
    write("b-no-name.yaml", "provider:\n  name: deepseek\n");
    write("c-good.yaml", FULL_YAML);

    int loaded = loader(Set.of("deepseek"), Map.of()).load();

    assertThat(loaded).isEqualTo(1);
    assertThat(registry.find("ops-agent")).isPresent();
    assertThat(errors()).contains("a-broken.yaml").contains("b-no-name.yaml");
  }

  @Test
  @DisplayName("ENV占位从环境变量解析")
  void envPlaceholdersAreResolvedFromEnvironment() throws IOException {
    write(
        "env.yaml",
        """
        name: env-agent
        description: ${AGENT_DESC}
        provider:
          name: deepseek
          model: ${MODEL_NAME:deepseek-chat}
        """);

    loader(Set.of("deepseek"), Map.of("AGENT_DESC", "来自环境变量")).load();

    Profile p = registry.find("env-agent").orElseThrow();
    assertThat(p.description()).isEqualTo("来自环境变量");
    assertThat(p.provider().model()).isEqualTo("deepseek-chat"); // 未设置且带默认值
  }

  @Test
  @DisplayName("ENV变量缺失且无默认值_该Profile被跳过并记错误")
  void missingEnvWithoutDefaultSkipsProfile() throws IOException {
    write(
        "missing-env.yaml",
        "name: m\ndescription: ${NOT_SET}\nprovider:\n  name: deepseek\n  model: x\n");

    int loaded = loader(Set.of("deepseek"), Map.of()).load();

    assertThat(loaded).isZero();
    assertThat(errors()).contains("missing-env.yaml").contains("NOT_SET");
  }

  @Test
  @DisplayName("同名Profile_后者不覆盖前者")
  void duplicateNameDoesNotOverrideFirst() throws IOException {
    write("a.yaml", FULL_YAML);
    write("b.yaml", FULL_YAML.replace("运维助手", "冒名顶替"));

    int loaded = loader(Set.of("deepseek"), Map.of()).load();

    assertThat(loaded).isEqualTo(1);
    assertThat(registry.find("ops-agent").orElseThrow().description()).isEqualTo("运维助手");
    assertThat(errors()).contains("b.yaml").contains("ops-agent");
  }

  @Test
  @DisplayName("目录不存在_索引为空不报错")
  void missingDirectoryYieldsEmptyRegistry() {
    ProfileLoader loader =
        new ProfileLoader(dir.resolve("not-exist"), Set.of("deepseek"), k -> null, registry);

    assertThat(loader.load()).isZero();
    assertThat(registry.all()).isEmpty();
  }

  @Test
  @DisplayName("非YAML文件被忽略")
  void nonYamlFilesAreIgnored() throws IOException {
    write("README.md", "# not yaml");
    write("good.yml", FULL_YAML);

    assertThat(loader(Set.of("deepseek"), Map.of()).load()).isEqualTo(1);
  }
}
