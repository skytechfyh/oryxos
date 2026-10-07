package io.oryxos.channel.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.BizException;
import io.oryxos.core.ErrorCode;
import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CliChannel 验收:读—转交—打印、/quit 退出、EOF、引擎失败、Profile 缺失。
 *
 * <p>CLI 是薄壳,所以全程 mock 引擎,只验证壳自己的行为。
 */
class CliChannelTest {

  private final AgentService agentService = mock(AgentService.class);
  private final SessionManager sessionManager = mock(SessionManager.class);
  private final ProfileRegistry profileRegistry = mock(ProfileRegistry.class);
  private final Session session = mock(Session.class);
  private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

  /** 默认 Profile 存在,会话管理返回同一个 mock 会话。 */
  @BeforeEach
  void setUp() {
    Profile profile =
        new Profile("default", null, null, null, null, null, null, null, null, null, null, null);
    when(profileRegistry.find("default")).thenReturn(Optional.of(profile));
    when(sessionManager.getOrCreate("cli", "wang", "default")).thenReturn(session);
  }

  /** 每一行输入都连同会话转交引擎,回复逐条打印,然后继续等待输入。 */
  @Test
  @DisplayName("每行输入都转交引擎_回复逐条打印")
  void everyLineIsForwardedAndReplyPrinted() {
    when(agentService.process(session, "你好")).thenReturn("你好呀");
    when(agentService.process(session, "再见吗")).thenReturn("先不见");

    int code = channel("你好\n再见吗\n/quit\n").run("default", "wang");

    assertThat(code).isZero();
    assertThat(out()).contains("你好呀").contains("先不见");
    verify(agentService, times(2)).process(eq(session), any());
  }

  /** /quit 结束循环且本身不交给引擎;首尾空白忽略。 */
  @Test
  @DisplayName("/quit首尾空白忽略_结束且不转交")
  void quitEndsLoopWithoutForwarding() {
    int code = channel("   /quit  \n不该被处理\n").run("default", "wang");

    assertThat(code).isZero();
    verify(agentService, never()).process(any(), any());
  }

  /** 输入流读完(如管道输入)视同退出,不抛异常。 */
  @Test
  @DisplayName("EOF无quit_正常结束")
  void eofEndsNormally() {
    when(agentService.process(session, "hi")).thenReturn("hello");

    int code = channel("hi\n").run("default", "wang");

    assertThat(code).isZero();
    assertThat(out()).contains("hello");
  }

  /** 引擎处理失败时打印错误提示并继续下一行,而不是吞掉或崩溃。 */
  @Test
  @DisplayName("引擎抛错_打印错误并继续下一行")
  void engineFailureIsReportedAndLoopContinues() {
    when(agentService.process(session, "boom")).thenThrow(new BizException(ErrorCode.NOT_FOUND));
    when(agentService.process(session, "ok")).thenReturn("fine");

    int code = channel("boom\nok\n/quit\n").run("default", "wang");

    assertThat(code).isZero();
    assertThat(err()).contains("错误");
    assertThat(out()).contains("fine");
  }

  /** 空行不交给引擎。 */
  @Test
  @DisplayName("空行跳过_不转交")
  void blankLineIsSkipped() {
    channel("\n   \n/quit\n").run("default", "wang");

    verify(agentService, never()).process(any(), any());
  }

  /** Profile 不存在时立刻报错返回非零,不进入循环、不创建会话、不调引擎。 */
  @Test
  @DisplayName("Profile不存在_立即报错返回1且不进入循环")
  void missingProfileFailsFast() {
    when(profileRegistry.find("ghost")).thenReturn(Optional.empty());

    int code = channel("hello\n").run("ghost", "wang");

    assertThat(code).isEqualTo(1);
    assertThat(err()).contains("ghost");
    verify(agentService, never()).process(any(), any());
    verify(sessionManager, never()).getOrCreate(any(), any(), any());
  }

  /** 会话身份由 CLI 只给三元组:渠道固定为 cli。 */
  @Test
  @DisplayName("会话以cli渠道三元组获取一次")
  void sessionIsRequestedWithCliTripleOnce() {
    channel("/quit\n").run("default", "wang");

    verify(sessionManager, times(1)).getOrCreate("cli", "wang", "default");
  }

  /**
   * 用脚本化输入构造被测对象。
   *
   * @param input 模拟的 stdin 内容
   * @return CliChannel
   */
  private CliChannel channel(String input) {
    return new CliChannel(
        agentService,
        sessionManager,
        profileRegistry,
        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
        new PrintStream(stdout, true, StandardCharsets.UTF_8),
        new PrintStream(stderr, true, StandardCharsets.UTF_8));
  }

  /**
   * 取已捕获的标准输出。
   *
   * @return 输出文本
   */
  private String out() {
    return stdout.toString(StandardCharsets.UTF_8);
  }

  /**
   * 取已捕获的错误输出。
   *
   * @return 错误输出文本
   */
  private String err() {
    return stderr.toString(StandardCharsets.UTF_8);
  }
}
