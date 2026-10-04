package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

/** 表必须由手工建表脚本创建(ddl-auto=none),口径与 llm_calls 一致。 */
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
class ToolInvocationRepositoryTest {

  @Autowired private ToolInvocationRepository repository;

  @Autowired private JdbcTemplate jdbc;

  /** 脚本建出的表必须真有 success 与 error_message 两列。 */
  @Test
  @DisplayName("建表脚本建出的表_success和error_message两列真实存在")
  void scriptCreatedTableHasSuccessAndErrorMessageColumns() {
    List<Map<String, Object>> columns = jdbc.queryForList("PRAGMA table_info(tool_invocations)");

    assertThat(columns)
        .extracting(c -> c.get("name"))
        .contains("session_id", "tool_name", "input", "success", "error_message", "duration_ms");
  }

  /** 成功记录能存能读。 */
  @Test
  @DisplayName("成功记录_能存能读")
  void successRecordRoundTrips() {
    ToolInvocation saved =
        repository.saveAndFlush(
            new ToolInvocation(
                "s-1", "http_get", "{\"url\":\"u\"}", true, null, 12L, "2026-10-04T00:00:00Z"));

    ToolInvocation loaded = repository.findById(saved.getId()).orElseThrow();

    assertThat(loaded.getSessionId()).isEqualTo("s-1");
    assertThat(loaded.getToolName()).isEqualTo("http_get");
    assertThat(loaded.getInput()).isEqualTo("{\"url\":\"u\"}");
    assertThat(loaded.isSuccess()).isTrue();
    assertThat(loaded.getErrorMessage()).isNull();
    assertThat(loaded.getDurationMs()).isEqualTo(12L);
  }

  /** 失败记录 success 为 false 且保留原因。 */
  @Test
  @DisplayName("失败记录_success为false且保存错误信息")
  void failureRecordKeepsErrorMessage() {
    ToolInvocation saved =
        repository.saveAndFlush(
            new ToolInvocation(
                "s-2", "read_file", "{}", false, "文件不存在", 3L, "2026-10-04T00:00:01Z"));

    ToolInvocation loaded = repository.findById(saved.getId()).orElseThrow();

    assertThat(loaded.isSuccess()).isFalse();
    assertThat(loaded.getErrorMessage()).isEqualTo("文件不存在");
  }

  /** 按会话标识能查回该会话的全部调用,便于与 llm_calls 关联。 */
  @Test
  @DisplayName("按sessionId查询只返回该会话的记录")
  void findBySessionIdReturnsOnlyThatSession() {
    repository.saveAndFlush(
        new ToolInvocation("s-a", "t1", "{}", true, null, 1L, "2026-10-04T00:00:00Z"));
    repository.saveAndFlush(
        new ToolInvocation("s-a", "t2", "{}", false, "x", 1L, "2026-10-04T00:00:01Z"));
    repository.saveAndFlush(
        new ToolInvocation("s-b", "t3", "{}", true, null, 1L, "2026-10-04T00:00:02Z"));

    assertThat(repository.findBySessionId("s-a")).hasSize(2);
  }

  /** DataJpaTest 需要一个配置类来定位实体与仓库;存储模块本身没有启动类。 */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("io.oryxos.storage")
  @EnableJpaRepositories("io.oryxos.storage")
  static class Config {}
}
