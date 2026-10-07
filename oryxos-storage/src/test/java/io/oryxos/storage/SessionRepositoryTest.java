package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.session.SessionManager;
import io.oryxos.core.tool.ToolResult;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * sessions 表与对话历史序列化验收:建表脚本、存取、消息往返、模拟重启。
 *
 * <p>表必须由手工建表脚本创建({@code ddl-auto=none}),SQLite 不依赖自动迁移。
 */
@DataJpaTest(
    properties = {
      "spring.datasource.url=jdbc:sqlite::memory:",
      "spring.datasource.driver-class-name=org.sqlite.JDBC",
      "spring.datasource.hikari.maximum-pool-size=1",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=classpath:schema.sql"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaSessionManager.class)
@TestPropertySource(properties = "spring.main.banner-mode=off")
class SessionRepositoryTest {

  @Autowired private SessionRepository repository;

  @Autowired private SessionManager sessionManager;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private EntityManager entityManager;

  /** 脚本建出的表必须真有设计里的全部 9 列。 */
  @Test
  @DisplayName("建表脚本建出的sessions表_9列真实存在")
  void scriptCreatedTableHasAllColumns() {
    List<Map<String, Object>> columns = jdbc.queryForList("PRAGMA table_info(sessions)");

    assertThat(columns)
        .extracting(c -> c.get("name"))
        .containsExactlyInAnyOrder(
            "session_id",
            "profile_name",
            "channel",
            "user_id",
            "messages_json",
            "status",
            "created_at",
            "last_active_at",
            "archived_at");
  }

  /** 实体能存能读,字段一致。 */
  @Test
  @DisplayName("会话实体_能存能读")
  void entityRoundTrips() {
    repository.saveAndFlush(
        new Session("cli:wang:default", "default", "cli", "wang", "2026-10-07T00:00:00Z"));
    entityManager.clear();

    Session loaded = repository.findById("cli:wang:default").orElseThrow();

    assertThat(loaded.id()).isEqualTo("cli:wang:default");
    assertThat(loaded.profileName()).isEqualTo("default");
    assertThat(loaded.messages()).isEmpty();
    assertThat(jdbc.queryForObject("select channel from sessions", String.class)).isEqualTo("cli");
    assertThat(jdbc.queryForObject("select user_id from sessions", String.class)).isEqualTo("wang");
    assertThat(jdbc.queryForObject("select status from sessions", String.class))
        .isEqualTo("active");
  }

  /** 用户消息、带 toolCalls 的模型响应、工具结果序列化后回读,条数、顺序、内容都不能变。 */
  @Test
  @DisplayName("messages_json往返_消息条数顺序内容完整")
  void messagesSurviveJsonRoundTrip() {
    var session = sessionManager.getOrCreate("cli", "wang", "default");
    var call = new AssistantMessage.ToolCall("c1", "function", "http", "{\"url\":\"x\"}");
    session.append("今天天气怎么样?\n第二行 \"带引号\" \\ 反斜杠");
    session.append(
        response(AssistantMessage.builder().content("").toolCalls(List.of(call)).build()));
    session.appendToolResult(call, ToolResult.ok("晴,25 度"));
    session.append(response(new AssistantMessage("穿短袖。")));
    session.append("");
    sessionManager.save(session);
    entityManager.flush();
    entityManager.clear();

    List<Message> loaded = sessionManager.get(session.id()).orElseThrow().messages();

    assertThat(loaded).hasSize(5);
    assertThat(loaded.get(0)).isInstanceOf(UserMessage.class);
    assertThat(loaded.get(0).getText()).isEqualTo("今天天气怎么样?\n第二行 \"带引号\" \\ 反斜杠");
    AssistantMessage withCall = (AssistantMessage) loaded.get(1);
    assertThat(withCall.getToolCalls()).containsExactly(call);
    ToolResponseMessage toolResult = (ToolResponseMessage) loaded.get(2);
    assertThat(toolResult.getResponses()).hasSize(1);
    assertThat(toolResult.getResponses().get(0).id()).isEqualTo("c1");
    assertThat(toolResult.getResponses().get(0).name()).isEqualTo("http");
    assertThat(toolResult.getResponses().get(0).responseData()).isEqualTo("晴,25 度");
    assertThat(((AssistantMessage) loaded.get(3)).getText()).isEqualTo("穿短袖。");
    assertThat(loaded.get(4).getText()).isEmpty();
  }

  /** 每次保存都要刷新最后活跃时间,且不能早于创建时间。 */
  @Test
  @DisplayName("save刷新last_active_at")
  void saveRefreshesLastActiveAt() {
    var session = sessionManager.getOrCreate("cli", "wang", "default");
    final String before = jdbc.queryForObject("select last_active_at from sessions", String.class);
    session.append("你好");

    sessionManager.save(session);
    entityManager.flush();

    String after = jdbc.queryForObject("select last_active_at from sessions", String.class);
    assertThat(after).isNotNull().isGreaterThanOrEqualTo(before);
    assertThat(jdbc.queryForObject("select messages_json from sessions", String.class))
        .contains("你好");
  }

  /**
   * 模拟重启:文件型 SQLite 上起第一个容器存会话并关闭,再起第二个全新容器查,历史仍在。
   *
   * <p>脱离测试事务,否则两个容器看到的是同一个未提交的事务。
   *
   * @param dir 临时目录,存放 SQLite 文件
   */
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("模拟重启_新建context重查_历史仍在")
  void historySurvivesRestart(@TempDir Path dir) {
    String url = "jdbc:sqlite:" + dir.resolve("restart.db");
    String id;
    try (ConfigurableApplicationContext first = start(url)) {
      var manager = first.getBean(SessionManager.class);
      var session = manager.getOrCreate("cli", "wang", "default");
      session.append("记住我叫小王");
      session.append(response(new AssistantMessage("好的,小王。")));
      manager.save(session);
      id = session.id();
    }

    try (ConfigurableApplicationContext second = start(url)) {
      var loaded = second.getBean(SessionManager.class).get(id).orElseThrow();

      assertThat(loaded.messages()).hasSize(2);
      assertThat(loaded.messages().get(0).getText()).isEqualTo("记住我叫小王");
      assertThat(loaded.messages().get(1).getText()).isEqualTo("好的,小王。");
    }
  }

  /**
   * 以指定 SQLite 文件启动一个独立的最小容器。
   *
   * @param url JDBC 连接串
   * @return 已启动的容器
   */
  private static ConfigurableApplicationContext start(String url) {
    SpringApplication app = new SpringApplication(SessionRestartConfig.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    app.setDefaultProperties(
        Map.of(
            "spring.datasource.url", url,
            "spring.datasource.driver-class-name", "org.sqlite.JDBC",
            "spring.datasource.hikari.maximum-pool-size", "1",
            "spring.jpa.hibernate.ddl-auto", "none",
            "spring.jpa.properties.hibernate.dialect",
                "org.hibernate.community.dialect.SQLiteDialect",
            "spring.sql.init.mode", "always",
            "spring.sql.init.schema-locations", "classpath:schema.sql",
            "spring.main.banner-mode", "off"));
    return app.run();
  }

  /**
   * 把一条助手消息包成模型响应。
   *
   * @param message 助手消息
   * @return 模型响应
   */
  private static ChatResponse response(AssistantMessage message) {
    return new ChatResponse(List.of(new Generation(message)));
  }

  /** DataJpaTest 需要一个配置类来定位实体与仓库;存储模块本身没有启动类。 */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("io.oryxos.storage")
  @EnableJpaRepositories("io.oryxos.storage")
  static class Config {}
}
