package io.oryxos.tool.notify;

import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通用 webhook 实现,核心阶段唯一的出站通知实现。
 *
 * <p>向目标配置里的 {@code url} 同步 POST 一个 JSON:{@code {"content": 内容}}。企业微信、飞书、钉钉的群机器人都提供 webhook 地址,
 * 一个通用实现就能覆盖大部分场景;各家专用 API(签名、AccessToken 刷新)留到扩展阶段。
 *
 * <p>失败不吞:目标返回 4xx/5xx 由 {@link RestClient} 抛 {@code RestClientResponseException},连接失败或超时抛 {@code
 * ResourceAccessException},全部直接向上抛,调用方据此得知没送达。
 *
 * <p>沙箱调用位:域名白名单由调用方 {@code NotifyTools} 在调用 {@link #send} 之前执行 {@code Sandbox.enforce}(24 节接线),
 * 本类自身不做白名单检查。
 */
@Component
public class WebhookNotifyAdapter implements NotifyChannelAdapter {

  /** 目标配置里存放 webhook 地址的键。 */
  private static final String URL_KEY = "url";

  /** 同步阻塞的 HTTP 客户端,带连接与读取超时。 */
  private final RestClient restClient;

  /**
   * 构造 webhook 适配器。
   *
   * @param restClient notify 专用的 RestClient;按名字限定,避免以后其他模块再提供 RestClient 时歧义
   */
  public WebhookNotifyAdapter(@Qualifier("notifyRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * 把内容 POST 到目标配置里的地址。
   *
   * <p>入参先校验后发请求:缺地址就明确报错,而不是向空地址或默认地址发出去;为什么不静默:Agent 会误以为已送达。
   *
   * @param target 通知目标,其配置必须含非空白的 {@code url}
   * @param content 要送出的内容,空字符串原样发送,不能为 null
   * @throws IllegalArgumentException 目标、配置、地址或内容缺失
   */
  @Override
  public void send(NotifyTarget target, String content) {
    String url = resolveUrl(target);
    if (content == null) {
      throw new IllegalArgumentException("通知内容不能为空引用(null)");
    }
    restClient
        .post()
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("content", content))
        .retrieve()
        .toBodilessEntity();
  }

  /**
   * 从通知目标取出 webhook 地址,缺失即报错。
   *
   * @param target 通知目标
   * @return 非空白的地址
   * @throws IllegalArgumentException 目标、配置为 null 或地址缺失、空白
   */
  private static String resolveUrl(NotifyTarget target) {
    if (target == null || target.config() == null) {
      throw new IllegalArgumentException("通知目标或其配置不能为空引用(null)");
    }
    String url = target.config().get(URL_KEY);
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("webhook 通知目标缺少配置项 url");
    }
    return url;
  }
}
