package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.BizException;
import io.oryxos.core.session.SessionManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 会话管理验收:幂等、隔离、身份校验、id 唯一生成处。
 *
 * <p>会话层是所有入口共用的地基,口径问题最难查,所以在这里钉死。表由手工建表脚本创建({@code ddl-auto=none}),与审计表测试口径一致。
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
class SessionManagerTest {

  @Autowired private SessionManager sessionManager;

  @Autowired private JdbcTemplate jdbc;

  /** 课件关键回归:幂等是多轮对话能串起来的前提,渠道不同则是不同会话。 */
  @Test
  @DisplayName("同一三元组_历次getOrCreate都是同一个Session")
  void sameTripleGetOrCreateReturnsSameSession() {
    var first = sessionManager.getOrCreate("cli", "wang", "default");
    var second = sessionManager.getOrCreate("cli", "wang", "default");
    assertThat(second.id()).isEqualTo(first.id());

    var other = sessionManager.getOrCreate("web", "wang", "default");
    assertThat(other.id()).isNotEqualTo(first.id());
  }

  /** 用户或 Agent 名任一不同,也必须是不同会话。 */
  @Test
  @DisplayName("user或profile任一不同_都是不同Session")
  void differentUserOrProfileGivesDifferentSession() {
    var base = sessionManager.getOrCreate("cli", "wang", "default");

    assertThat(sessionManager.getOrCreate("cli", "li", "default").id()).isNotEqualTo(base.id());
    assertThat(sessionManager.getOrCreate("cli", "wang", "weather").id()).isNotEqualTo(base.id());
  }

  /** 同一身份重复获取后库里只能有一行,而不是每次都新建。 */
  @Test
  @DisplayName("同身份重复获取_表内只有一行")
  void repeatedGetOrCreateKeepsSingleRow() {
    sessionManager.getOrCreate("cli", "wang", "default");
    sessionManager.getOrCreate("cli", "wang", "default");
    sessionManager.getOrCreate("cli", "wang", "default");

    Integer rows = jdbc.queryForObject("select count(*) from sessions", Integer.class);
    assertThat(rows).isEqualTo(1);
  }

  /** 身份里含分隔符时不能因拼接而碰撞成同一个标识。 */
  @Test
  @DisplayName("身份含分隔符_不同身份不碰撞")
  void identitiesContainingSeparatorDoNotCollide() {
    var left = sessionManager.getOrCreate("a:b", "c", "p");
    var right = sessionManager.getOrCreate("a", "b:c", "p");
    var percent = sessionManager.getOrCreate("a%3Ab", "c", "p");

    assertThat(left.id()).isNotEqualTo(right.id());
    assertThat(percent.id()).isNotEqualTo(left.id());
  }

  /** 空白或 null 的身份项必须被明确拒绝,而不是创建出不可识别的会话。 */
  @Test
  @DisplayName("身份任一项为空白或null_抛BizException")
  void blankIdentityIsRejected() {
    assertThatThrownBy(() -> sessionManager.getOrCreate(" ", "wang", "default"))
        .isInstanceOf(BizException.class);
    assertThatThrownBy(() -> sessionManager.getOrCreate("cli", "", "default"))
        .isInstanceOf(BizException.class);
    assertThatThrownBy(() -> sessionManager.getOrCreate("cli", "wang", null))
        .isInstanceOf(BizException.class);
  }

  /** 新建会话为活跃状态、历史为空;按标识能找回,找不到返回空而不是抛异常。 */
  @Test
  @DisplayName("新建会话_active且历史为空_get按id找回_不存在返回empty")
  void newSessionIsActiveAndFindable() {
    var created = sessionManager.getOrCreate("cli", "wang", "default");

    assertThat(created.profileName()).isEqualTo("default");
    assertThat(created.messages()).isEmpty();
    assertThat(jdbc.queryForObject("select status from sessions", String.class))
        .isEqualTo("active");
    assertThat(sessionManager.get(created.id())).isPresent();
    assertThat(sessionManager.get("not-exist")).isEmpty();
  }

  /** save 只接受本实现产生的会话,传入别的实现必须报错而不是静默忽略。 */
  @Test
  @DisplayName("save传入非本实现的Session_抛BizException")
  void saveRejectsForeignSession() {
    var foreign = org.mockito.Mockito.mock(io.oryxos.core.session.Session.class);

    assertThatThrownBy(() -> sessionManager.save(foreign)).isInstanceOf(BizException.class);
  }

  /**
   * "id 生成只此一处":扫描全仓 main 源码,转义标记 {@code %3A} 只能出现在 {@link JpaSessionManager}。
   *
   * <p>找不到仓库根目录时直接失败,避免扫了个空集却显示通过。
   */
  @Test
  @DisplayName("session_id拼接只发生在JpaSessionManager一处")
  void sessionIdIsBuiltInExactlyOnePlace() throws IOException {
    Path root = findRepositoryRoot();
    List<Path> hits;
    try (Stream<Path> modules = Files.list(root)) {
      hits =
          modules
              .filter(p -> p.getFileName().toString().startsWith("oryxos-"))
              .map(p -> p.resolve("src/main/java"))
              .filter(Files::isDirectory)
              .flatMap(SessionManagerTest::javaFiles)
              .filter(SessionManagerTest::containsEscapeMarker)
              .toList();
    }

    assertThat(hits).hasSize(1);
    assertThat(hits.get(0).getFileName().toString()).isEqualTo("JpaSessionManager.java");
  }

  /**
   * 从当前工作目录向上找到含 oryxos-core 目录的仓库根。
   *
   * @return 仓库根目录
   */
  private static Path findRepositoryRoot() {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      if (Files.isDirectory(dir.resolve("oryxos-core")) && Files.exists(dir.resolve("pom.xml"))) {
        return dir;
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("找不到仓库根目录,无法执行源码扫描断言");
  }

  /**
   * 列出目录下全部 Java 源文件。
   *
   * @param dir 源码目录
   * @return 源文件流
   */
  private static Stream<Path> javaFiles(Path dir) {
    try {
      return Files.walk(dir).filter(p -> p.toString().endsWith(".java"));
    } catch (IOException e) {
      throw new IllegalStateException("扫描源码失败: " + dir, e);
    }
  }

  /**
   * 判断源文件是否含会话标识的分隔符转义标记。
   *
   * @param file 源文件
   * @return 含则为 true
   */
  private static boolean containsEscapeMarker(Path file) {
    try {
      return Files.readString(file).contains("%3A");
    } catch (IOException e) {
      throw new IllegalStateException("读取源码失败: " + file, e);
    }
  }

  /** DataJpaTest 需要一个配置类来定位实体与仓库;存储模块本身没有启动类。 */
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("io.oryxos.storage")
  @EnableJpaRepositories("io.oryxos.storage")
  static class Config {}
}
