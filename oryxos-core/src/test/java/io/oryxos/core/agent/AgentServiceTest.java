package io.oryxos.core.agent;

import static io.oryxos.core.agent.TestFixtures.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.BizException;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.SessionManager;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AgentService 验收:处理期间可取 Profile、异常也清理、结束后持久化。 */
class AgentServiceTest {

  private final ProfileRegistry profileRegistry = mock(ProfileRegistry.class);
  private final ReActLoop reActLoop = mock(ReActLoop.class);
  private final SessionManager sessionManager = mock(SessionManager.class);
  private final AgentService agentService =
      new AgentService(profileRegistry, reActLoop, sessionManager);
  private final Profile profile = profile(List.of(), null, null);
  private FakeSession session;

  /** Registry 按 Profile 名返回测试 Profile。 */
  @BeforeEach
  void setUp() {
    session = new FakeSession("s-1", "weather-agent");
    when(profileRegistry.find("weather-agent")).thenReturn(Optional.of(profile));
  }

  /** 处理期间工具侧能取到当前 Profile。 */
  @Test
  @DisplayName("处理期间ProfileContext可取到当前Profile")
  void profileContextAvailableDuringProcessing() {
    AtomicReference<Profile> seen = new AtomicReference<>();
    when(reActLoop.run(any(), any(), any()))
        .thenAnswer(
            inv -> {
              seen.set(ProfileContext.current());
              return "ok";
            });

    String reply = agentService.process(session, "hi");

    assertThat(reply).isEqualTo("ok");
    assertThat(seen.get()).isSameAs(profile);
  }

  /** 最阴险的一类 bug:ThreadLocal 泄漏,单请求测试不报错,线程复用时串号。 */
  @Test
  @DisplayName("处理中抛异常_ProfileContext也必须被清掉")
  void profileContextClearedWhenProcessingThrows() {
    when(reActLoop.run(any(), any(), any())).thenThrow(new RuntimeException("boom"));

    assertThrows(RuntimeException.class, () -> agentService.process(session, "hi"));

    assertNull(ProfileContext.current()); // finally 没清,下一个复用此线程的请求会拿到别人的 Profile
  }

  /** 正常结束:历史被持久化,身份已清除。 */
  @Test
  @DisplayName("正常结束_Session被持久化且ProfileContext已清除")
  void savesSessionAndClearsContextOnSuccess() {
    when(reActLoop.run(any(), any(), any())).thenReturn("ok");

    agentService.process(session, "hi");

    verify(sessionManager).save(session);
    assertNull(ProfileContext.current());
  }

  /** Profile 不存在是调用方错误,抛业务异常,且同样不留残余身份。 */
  @Test
  @DisplayName("Profile不存在_抛业务异常")
  void missingProfileThrowsBizException() {
    FakeSession ghost = new FakeSession("s-2", "ghost");
    when(profileRegistry.find("ghost")).thenReturn(Optional.empty());

    assertThrows(BizException.class, () -> agentService.process(ghost, "hi"));

    assertNull(ProfileContext.current());
  }
}
