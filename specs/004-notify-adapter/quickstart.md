# Quickstart: 验证 Notify 出站通知

## 前置
- JDK 21,项目根使用 `./mvnw`(Wrapper 固定 3.9.16)。

## 自动化验证
```bash
./mvnw -pl oryxos-tool -am test                 # 含 WebhookNotifyAdapterTest
./mvnw clean verify                              # 全量门禁:Spotless/Checkstyle/PMD+P3C/SpotBugs + 前序节回归
```
预期:`WebhookNotifyAdapterTest` 全绿(POST+content、URL 取自配置、5xx 上抛、缺 url 报错、特殊字符、连接失败、两目标不串发)。

## 依赖核对
```bash
./mvnw -pl oryxos-tool dependency:tree          # 确认 spring-web、mockwebserver 4.12.0 解析成功
```

## 人工项(harness 不覆盖)
1. 配置一个真实 webhook(如企业微信/飞书群机器人地址,经环境变量注入),用最小脚本或后续 `NotifyTools` 触发一次 `send`,确认群里收到消息。
2. 接口中立性自查:换成专用 SDK 实现时 `send(NotifyTarget, String)` 无需改签名。
