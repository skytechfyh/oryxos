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

/** 表必须由手工建表脚本创建(ddl-auto=none),否则测试绿了、生产跑真脚本时列名对不上。 */
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
class LlmCallRepositoryTest {

  @Autowired private LlmCallRepository repository;

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("建表脚本建出的表_success和error_message两列真实存在")
  void scriptCreatedTableHasSuccessAndErrorMessageColumns() {
    List<Map<String, Object>> columns = jdbc.queryForList("PRAGMA table_info(llm_calls)");

    assertThat(columns).extracting(c -> c.get("name")).contains("success", "error_message");
  }

  @Test
  @DisplayName("成功记录_能存能读")
  void successRecordRoundTrips() {
    LlmCall saved =
        repository.saveAndFlush(
            new LlmCall(
                "s-1",
                "deepseek",
                "deepseek-chat",
                10,
                20,
                30,
                123L,
                true,
                null,
                "2026-10-03T00:00:00Z"));

    LlmCall loaded = repository.findById(saved.getId()).orElseThrow();

    assertThat(loaded.getSessionId()).isEqualTo("s-1");
    assertThat(loaded.getProvider()).isEqualTo("deepseek");
    assertThat(loaded.getModel()).isEqualTo("deepseek-chat");
    assertThat(loaded.getTotalTokens()).isEqualTo(30);
    assertThat(loaded.getDurationMs()).isEqualTo(123L);
    assertThat(loaded.isSuccess()).isTrue();
    assertThat(loaded.getErrorMessage()).isNull();
  }

  @Test
  @DisplayName("失败记录_success为false且保存错误信息")
  void failureRecordKeepsErrorMessage() {
    LlmCall saved =
        repository.saveAndFlush(
            new LlmCall(
                "s-2",
                "kimi",
                "moonshot-v1",
                null,
                null,
                null,
                5L,
                false,
                "connect timeout",
                "2026-10-03T00:00:01Z"));

    LlmCall loaded = repository.findById(saved.getId()).orElseThrow();

    assertThat(loaded.isSuccess()).isFalse();
    assertThat(loaded.getErrorMessage()).isEqualTo("connect timeout");
    assertThat(loaded.getPromptTokens()).isNull();
  }

  /** DataJpaTest 需要一个配置类来定位实体与仓库;存储模块本身没有启动类。 */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("io.oryxos.storage")
  @EnableJpaRepositories("io.oryxos.storage")
  static class Config {}
}
