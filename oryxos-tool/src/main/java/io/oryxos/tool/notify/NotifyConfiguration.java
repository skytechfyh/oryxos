package io.oryxos.tool.notify;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 为 webhook 实现提供带超时的 {@link RestClient}。
 *
 * <p>为何需要:{@link WebhookNotifyAdapter} 按课件构造器注入 RestClient,工程里尚无该 Bean。为何包内可见:不新增对外概念。
 * 为何显式命名:以后其他模块(如 HttpTools)再提供 RestClient 时,按名字绑定避免歧义。底层用 JDK HttpClient,同步阻塞、与虚拟线程兼容,
 * 超时有限,避免目标挂起时无限阻塞。
 */
@Configuration(proxyBeanMethods = false)
class NotifyConfiguration {

  /** 连接超时。 */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

  /** 读取超时。 */
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

  /**
   * 构造 notify 专用的 RestClient。
   *
   * @return 带连接与读取超时的 RestClient
   */
  @Bean("notifyRestClient")
  RestClient notifyRestClient() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().requestFactory(factory).build();
  }
}
