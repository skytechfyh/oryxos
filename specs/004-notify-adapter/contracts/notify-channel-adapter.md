# Contract: NotifyChannelAdapter

```java
package io.oryxos.tool.notify;

public interface NotifyChannelAdapter {
  void send(NotifyTarget target, String content);
}

public record NotifyTarget(String channelType, Map<String, String> config) {}
```

## 行为契约
- 成功:正常返回;失败:抛运行时异常,**不吞**。
- 接口签名无任何渠道特有词;新增渠道只新增实现类。

## WebhookNotifyAdapter(核心阶段唯一实现)
- 请求:`POST {config["url"]}`,`Content-Type: application/json`,body `{"content": "<content>"}`。
- 地址只取自 `target.config().get("url")`。
- 错误映射:
  | 情形 | 异常 |
  |---|---|
  | `target` / `config` 为 null、`url` 缺失或空白、`content` 为 null | `IllegalArgumentException`(不发请求) |
  | 目标返回 4xx/5xx | `RestClientResponseException`(上抛) |
  | 连接失败/超时 | `ResourceAccessException`(上抛) |
- 构造器:`WebhookNotifyAdapter(@Qualifier("notifyRestClient") RestClient restClient)`。
- 沙箱调用位:域名白名单由 `NotifyTools`(24 节之后)在 `send` 前 `enforce`。
