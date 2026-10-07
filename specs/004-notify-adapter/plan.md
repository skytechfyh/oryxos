# Implementation Plan: Notify 出站通知能力(第19节)

**Branch**: `004-lesson19-notify` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-notify-adapter/spec.md`

## Summary

在 `oryxos-tool` 新增 `io.oryxos.tool.notify` 包:与渠道无关的出站接口 `NotifyChannelAdapter.send(NotifyTarget, String)`、`NotifyTarget(channelType, config)` 记录、以及核心阶段唯一实现 `WebhookNotifyAdapter`(`RestClient` 同步 POST `{"content": ...}` 到 `config["url"]`)。失败(缺 url、5xx、连接失败)一律抛异常,不吞。`NotifyTools` / Sandbox 接线 / `Profile.notifyChannels` 结构化均不在本节(用户已在软门禁确认,推迟到 24 节之后)。

## Technical Context

**Language/Version**: Java 21(虚拟线程,同步阻塞)

**Primary Dependencies**: Spring Boot 3.5.16(BOM 管理 `spring-web` 6.2.x);`oryxos-tool` 新增 `spring-web`(`RestClient`)、`spring-context`(`@Component`/`@Configuration`/`@Bean`;实测 `oryxos-tool` 里它仅 test scope 可见,`spring-web` 不带它;同属 Boot BOM 管理的 6.2.x 线,不引入新来源)与 test scope `com.squareup.okhttp3:mockwebserver:4.12.0`(Boot BOM 管理的 `okhttp3.version`,同时是 4.x 线 Maven Central 最新发布版)。HTTP 客户端底层用 JDK 自带 `HttpClient`(`JdkClientHttpRequestFactory`),不引入 okhttp 运行时依赖。

**Storage**: N/A(本节无持久化、无新表)

**Testing**: JUnit 5 + AssertJ(Boot starter-test 已由根 pom 注入)+ MockWebServer 做本地假 webhook;`WebhookNotifyAdapterTest` 默认跑,无 `@Tag("integration")` 用例;完成定义为 `./mvnw clean verify` 全绿

**Target Platform**: JVM,fat JAR 内运行

**Project Type**: Maven 多模块单体中的一个业务模块(`oryxos-tool`)

**Performance Goals**: 无量化指标(单次同步 POST)

**Constraints**: 连接超时 5s、读取超时 10s(有限,不无限阻塞);不做重试/签名/鉴权;不引入 Reactor / `CompletableFuture` / 自建线程池;所有方法含中文 Javadoc;避开 Java 18+ 增强 switch `default ->` 等 P3C/ASM 不识别的语法

**Scale/Scope**: 3 个 public 类型 + 1 个包内配置类 + 1 个测试类

## Constitution Check

| 原则 | 结论 |
|---|---|
| I/II 自实现 ReAct、Spring AI 只做协议转换并禁用自动 tool 执行 | 通过:本节不碰 ReAct 与 Spring AI;不引入任何 tool 调度路径 |
| III Provider 显式映射 | 不涉及 |
| IV Skill 不作为 Tool;`oryxos-tool` 单模块 | 通过:代码落在 `oryxos-tool`,不拆模块,不改 core |
| V 审计 Day One | 不涉及:`notify` 作为 Tool 的 `tool_invocations` 审计随 `NotifyTools` 经 `ToolExecutor` 统一落库,本节适配器非 Tool |
| VI 沙箱白名单 | 通过(有说明):`WebhookNotifyAdapter` 是被 `NotifyTools` 调用的底层发送器,域名白名单由调用方 `NotifyTools` 在 `send` 前 `enforce`(24 节接线);适配器类注释中注明此调用位,地址不硬编码、凭证走环境变量占位 |
| VII 同步 + 虚拟线程 | 通过:`RestClient` 同步阻塞,无异步原语 |
| VIII 配置即 Agent、无状态 | 通过:适配器无状态,地址来自目标配置 |

无违规,Complexity Tracking 无需填写。**Phase 1 设计后复核**:新增的包内 `NotifyConfiguration` 只提供 `RestClient` Bean,不违背以上任一条,仍通过。

## Project Structure

### Documentation (this feature)

```text
specs/004-notify-adapter/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── notify-channel-adapter.md
├── checklists/requirements.md
└── tasks.md             # 由 /speckit-tasks 生成
```

### Source Code (repository root)

```text
oryxos-tool/
├── pom.xml                                   # 改:+ spring-web;+ mockwebserver(test)
└── src/
    ├── main/java/io/oryxos/tool/notify/
    │   ├── NotifyChannelAdapter.java         # public 接口(课件交付物)
    │   ├── NotifyTarget.java                 # public record(课件交付物)
    │   ├── WebhookNotifyAdapter.java         # public @Component(课件交付物)
    │   └── NotifyConfiguration.java          # 包内(非 public)@Configuration,提供带超时的 RestClient Bean
    └── test/java/io/oryxos/tool/notify/
        └── WebhookNotifyAdapterTest.java     # 课件 harness 第一批 + spec 边界用例
```

**Structure Decision**: 全部落在 `oryxos-tool` 的 `io.oryxos.tool.notify`,与 TechnicalSolution §10 和课件一致。`NotifyConfiguration` 不在课件交付物清单,但课件让 `WebhookNotifyAdapter` 构造器注入 `RestClient`,而工程里目前没有该 Bean(后端 Boot 只提供 `RestClient.Builder`),必须有处装配;放进 `oryxos-boot` 会让 notify 的运行前提散落到启动模块,放在 `oryxos-tool` 包内且声明为包内可见类,避免新增对外概念,Bean 名 `notifyRestClient`,适配器构造器参数用 `@Qualifier("notifyRestClient")` 绑定(签名类型不变),避免以后 `HttpTools` 等再提供 `RestClient` 时歧义。因 `OryxOsApplication` 以 `scanBasePackages = "io.oryxos"` 扫描,无需额外注册。

## Complexity Tracking

无违规。
