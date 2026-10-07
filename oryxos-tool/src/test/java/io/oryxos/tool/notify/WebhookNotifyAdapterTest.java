package io.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link WebhookNotifyAdapter} 的单测(课件验收 harness 第一批)。
 *
 * <p>用 MockWebServer 在本地起假 webhook,不依赖外网,守住:POST 且 body 含 content、URL 来自目标配置而非硬编码、
 * 失败(5xx、不可达)异常向上抛不吞。
 */
class WebhookNotifyAdapterTest {

  /** 解析假服务收到的请求体。 */
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 本地假 webhook。 */
  private MockWebServer server;

  /** 被测对象,使用默认 RestClient(超时配置由装配用例单独覆盖)。 */
  private WebhookNotifyAdapter adapter;

  /**
   * 每个用例前启动假 webhook 并构造被测对象。
   *
   * @throws IOException 假服务启动失败
   */
  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    adapter = new WebhookNotifyAdapter(RestClient.create());
  }

  /**
   * 每个用例后关闭假 webhook,重复关闭无副作用。
   *
   * @throws IOException 关闭失败
   */
  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  /**
   * 构造指向给定服务的 webhook 通知目标。
   *
   * @param target 假服务
   * @return 通知目标
   */
  private static NotifyTarget webhookTarget(MockWebServer target) {
    return new NotifyTarget("webhook", Map.of("url", target.url("/hook").toString()));
  }

  /**
   * 取出假服务收到的下一个请求体里的 content 字段。
   *
   * @param request 已记录的请求
   * @return content 字段文本
   * @throws IOException JSON 解析失败
   */
  private static String contentOf(RecordedRequest request) throws IOException {
    JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
    return body.get("content").asText();
  }

  /**
   * 课件关键回归:发送后假服务恰好收到一次 POST,请求体为 JSON 且 content 一致。
   *
   * @throws Exception 假服务读取失败
   */
  @Test
  @DisplayName("发送后假服务收到POST且body含content")
  void postsJsonBodyWithContent() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200));

    adapter.send(webhookTarget(server), "hello");

    assertThat(server.getRequestCount()).isEqualTo(1);
    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/hook");
    assertThat(request.getHeader("Content-Type")).contains("application/json");
    assertThat(contentOf(request)).isEqualTo("hello");
  }

  /**
   * 引号、换行、中文、emoji 经 JSON 转义后,服务端收到的内容与发送前完全一致。
   *
   * @throws Exception 假服务读取失败
   */
  @Test
  @DisplayName("特殊字符与换行与emoji原样往返")
  void specialCharactersRoundTripUnchanged() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200));
    String content = "他说:\"你好\"\n第二行\t制表 \\ 反斜杠 😀";

    adapter.send(webhookTarget(server), content);

    assertThat(contentOf(server.takeRequest())).isEqualTo(content);
  }

  /**
   * 空字符串内容按原样发送,不静默丢弃也不报错。
   *
   * @throws Exception 假服务读取失败
   */
  @Test
  @DisplayName("空字符串content原样发送")
  void emptyContentIsSentAsIs() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200));

    adapter.send(webhookTarget(server), "");

    assertThat(contentOf(server.takeRequest())).isEmpty();
  }

  /**
   * 课件关键回归:推送地址来自 NotifyTarget 配置而非硬编码——两个地址不同的目标各发一次,互不串发。
   *
   * @throws Exception 假服务读写失败
   */
  @Test
  @DisplayName("URL来自NotifyTarget配置而非硬编码")
  void urlComesFromTargetConfigNotHardcoded() throws Exception {
    try (MockWebServer other = new MockWebServer()) {
      other.start();
      server.enqueue(new MockResponse().setResponseCode(200));
      other.enqueue(new MockResponse().setResponseCode(200));

      adapter.send(webhookTarget(server), "给第一个");
      adapter.send(webhookTarget(other), "给第二个");

      assertThat(server.getRequestCount()).isEqualTo(1);
      assertThat(other.getRequestCount()).isEqualTo(1);
      assertThat(contentOf(server.takeRequest())).isEqualTo("给第一个");
      assertThat(contentOf(other.takeRequest())).isEqualTo("给第二个");
    }
  }

  /** 目标配置缺少可用地址(空 Map、键缺失、空白串)时明确报错,且不向任何地方发请求。 */
  @Test
  @DisplayName("缺少url时明确报错且不发请求")
  void missingUrlFailsWithoutSendingRequest() {
    NotifyTarget emptyConfig = new NotifyTarget("webhook", Map.of());
    NotifyTarget otherKey = new NotifyTarget("webhook", Map.of("token", "x"));
    NotifyTarget blankUrl = new NotifyTarget("webhook", Map.of("url", "   "));

    assertThatThrownBy(() -> adapter.send(emptyConfig, "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.send(otherKey, "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.send(blankUrl, "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(server.getRequestCount()).isZero();
  }

  /** target、config、content 为 null 时明确报错,而不是空指针式崩溃。 */
  @Test
  @DisplayName("入参为null时明确报错")
  void nullArgumentsFailClearly() {
    assertThatThrownBy(() -> adapter.send(null, "x")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.send(new NotifyTarget("webhook", null), "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.send(webhookTarget(server), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(server.getRequestCount()).isZero();
  }

  /** 课件关键回归:webhook 返回 5xx 时异常向上抛,不静默吞掉。 */
  @Test
  @DisplayName("webhook返回5xx时异常向上抛不静默吞掉")
  void serverErrorPropagatesInsteadOfBeingSwallowed() {
    server.enqueue(new MockResponse().setResponseCode(500));

    assertThatThrownBy(() -> adapter.send(webhookTarget(server), "hello"))
        .isInstanceOf(RestClientResponseException.class);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  /** 4xx 同样视为失败上抛,避免把"地址写错"误判成已送达。 */
  @Test
  @DisplayName("返回4xx时同样上抛")
  void clientErrorAlsoPropagates() {
    server.enqueue(new MockResponse().setResponseCode(404));

    assertThatThrownBy(() -> adapter.send(webhookTarget(server), "hello"))
        .isInstanceOf(RestClientResponseException.class);
  }

  /**
   * 目标不可达(连接被拒)时异常上抛。
   *
   * @throws IOException 关闭假服务失败
   */
  @Test
  @DisplayName("连接失败时异常上抛")
  void connectionFailurePropagates() throws IOException {
    NotifyTarget unreachable = webhookTarget(server);
    server.shutdown();

    assertThatThrownBy(() -> adapter.send(unreachable, "hello"))
        .isInstanceOf(ResourceAccessException.class);
  }

  /** 装配用例:Spring 容器装配后能取到 NotifyChannelAdapter,且存在名为 notifyRestClient 的 Bean,守住 Bean 名与限定符绑定。 */
  @Test
  @DisplayName("Spring装配后可取到NotifyChannelAdapter")
  void springContextWiresAdapterAndRestClient() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(
            NotifyConfiguration.class, WebhookNotifyAdapter.class)) {
      assertThat(context.getBean(NotifyChannelAdapter.class))
          .isInstanceOf(WebhookNotifyAdapter.class);
      assertThat(context.getBean("notifyRestClient")).isInstanceOf(RestClient.class);
    }
  }
}
