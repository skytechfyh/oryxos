package io.oryxos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.oryxos.core.BizException;
import io.oryxos.core.profile.Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/** ContextLoader 验收:无缓存、Skill 缺失报错、Bootstrap 缺失 WARN、路径不越界。 */
class ContextLoaderTest {

  @TempDir Path workspace;

  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private Logger logger;
  private ContextLoader loader;

  /** 挂上日志收集器,用于断言 WARN。 */
  @BeforeEach
  void setUp() {
    loader = new ContextLoader(workspace);
    logger = (Logger) LoggerFactory.getLogger(ContextLoader.class);
    logs.start();
    logger.addAppender(logs);
  }

  /** 摘掉日志收集器,避免影响其他测试。 */
  @AfterEach
  void tearDown() {
    logger.detachAppender(logs);
  }

  /**
   * 构造引用给定文件的 Profile。
   *
   * @param bootstrap Bootstrap 文件名
   * @param skills Skill 名
   * @return Profile
   */
  private static Profile profileWith(List<String> bootstrap, List<String> skills) {
    return new Profile(
        "weather-agent",
        "测试",
        null,
        null,
        List.of(),
        skills,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        bootstrap,
        null);
  }

  /**
   * 写一个文件,必要时先建父目录。
   *
   * @param relative 工作区内相对路径
   * @param content 文件内容
   */
  private void write(String relative, String content) throws IOException {
    Path file = workspace.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  /** 改文件后下一次 load 必须立即读到新内容,守住"不缓存"。 */
  @Test
  @DisplayName("改文件后下一次load立即读到新内容")
  void reloadsFileOnEveryLoad() throws IOException {
    write("SOUL.md", "旧人格");
    Profile profile = profileWith(List.of("SOUL.md"), List.of());
    assertThat(loader.load(profile)).contains("旧人格");

    write("SOUL.md", "新人格");

    assertThat(loader.load(profile)).contains("新人格").doesNotContain("旧人格");
  }

  /** Profile 显式引用的 Skill 不存在,必须报错而不是静默跳过。 */
  @Test
  @DisplayName("Skill引用缺失_报错")
  void missingSkillThrows() {
    Profile profile = profileWith(List.of(), List.of("ghost"));

    assertThatThrownBy(() -> loader.load(profile))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("Skill");
  }

  /** Bootstrap 缺失只告警不中断,但必须留下 WARN。 */
  @Test
  @DisplayName("Bootstrap缺失_WARN且不抛")
  void missingBootstrapWarnsWithoutThrowing() {
    Profile profile = profileWith(List.of("AGENTS.md"), List.of());

    assertThat(loader.load(profile)).isEmpty();

    assertThat(logs.list)
        .anySatisfy(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getFormattedMessage()).contains("AGENTS.md");
            });
  }

  /** Bootstrap 在前、Skill 在后,按声明顺序拼接。 */
  @Test
  @DisplayName("同时含Bootstrap与Skill_顺序拼接")
  void joinsBootstrapThenSkillInOrder() throws IOException {
    write("SOUL.md", "人格");
    write("skills/weather/SKILL.md", "查天气技能");
    Profile profile = profileWith(List.of("SOUL.md"), List.of("weather"));

    String text = loader.load(profile);

    assertThat(text.indexOf("人格")).isGreaterThanOrEqualTo(0).isLessThan(text.indexOf("查天气技能"));
  }

  /** Profile 里写 ../ 不能读到工作区之外的文件。 */
  @Test
  @DisplayName("路径越界_拒绝读取")
  void rejectsPathEscapingWorkspace() {
    Profile profile = profileWith(List.of("../outside.md"), List.of());

    assertThatThrownBy(() -> loader.load(profile)).isInstanceOf(BizException.class);
  }
}
