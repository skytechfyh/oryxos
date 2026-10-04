# Implementation Plan: ReAct 循环(第17节)

**Branch**: `002-lesson17-react-loop` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-react-loop/spec.md`

## Summary

在 `oryxos-core` 自实现 ReAct 循环:`ReActLoop` 只做调度(转圈、判停、累积),`PromptBuilder` 拼四部分提示,`ToolExecutor` 是工具执行的唯一入口并写审计,`AgentService.process` 是三种触发源的统一入口并用 `ProfileContext`(ThreadLocal)传递当前 Profile,`ContextLoader` 每次现读 Bootstrap/Skill 文件。`tool_invocations` 的实体、Repository、手工建表脚本落在 `oryxos-storage`。

**模块解耦(软门禁已获用户确认,方案 A)**:core 不能依赖 provider/storage(会循环),因此 core 增加对 `spring-ai-model` 的依赖(仅用 `Prompt`/`ChatResponse`/`Message`/`AssistantMessage.ToolCall` 数据类型,不用 Agent 抽象),并新增两个接口:`ChatGateway`(由 `ProviderService` 实现,第16节签名不动)与 `ToolInvocationRecorder`(由 storage 实现)。其余缺口(`Session`、`SessionManager`、`ToolRegistry`、`ToolResult`、`OryxTool.execute`、可选的长期记忆接口)均为 core 内最小占位,第18/20/22节补实现且不破坏签名。

## Technical Context

**Language/Version**: Java 21(虚拟线程,同步阻塞)

**Primary Dependencies**: Spring Boot 3.5.16;Spring AI **1.1.8**(BOM 已锁定;core 新增 `spring-ai-model`,见 research R1,无新版本、无新 CVE 评估);slf4j(经 spring-ai-model 传递,core 用于 WARN 日志);core 测试域新增 `spring-boot-starter-test`(test scope,BOM 管理)

**Storage**: SQLite;`tool_invocations` 追加到 `oryxos-storage/src/main/resources/schema.sql`(手工建表,`ddl-auto: none`),并清理 `db/audit-tables.sql` 里的预留注释

**Testing**: JUnit 5 + Mockito + AssertJ;`ToolInvocationRepositoryTest` 复用 `LlmCallRepositoryTest` 的切片写法;ReAct 相关五类全为纯单测(mock `ChatGateway`/工具,`@TempDir` 做文件);无集成测试新增(Demo 一真模型走人工项)

**Target Platform**: 单机 fat JAR(JVM)

**Project Type**: Maven 多模块单体

**Performance Goals**: N/A

**Constraints**: 禁用 Spring AI 自动工具执行(第16节 `ProviderService` 已关,本节不得新增任何 `ChatClient`/自动执行路径);无 Reactor/CompletableFuture/自建线程池;P3C/PMD/SpotBugs 全绿;避开 Java 18+ 增强 switch `default ->` 写法;每个方法含 private/构造器/测试方法带中文 Javadoc,类带中文类注释

**Scale/Scope**: 新增约 6 个核心类 + 约 8 个占位/接口/记录 + 1 实体 + 1 Repository + 1 Recorder 实现 + 5~6 个测试类

## Constitution Check

| 原则 | 结论 |
|---|---|
| I 自实现 ReAct(NON-NEGOTIABLE) | 通过:`ReActLoop` 自写 for 循环;不用 `ChatClient`/Agent 抽象;工具只由 `ToolExecutor` 执行 |
| II Spring AI 仅协议转换(NON-NEGOTIABLE) | 通过:core 仅引用其数据类型;调用经 `ChatGateway`→`ProviderService`(`internalToolExecutionEnabled=false`);无新增调用路径 |
| III Provider 显式映射 | 不涉及(第16节已保证) |
| IV SKILL.md 由 ContextLoader 加载 | 通过:`ContextLoader` 在 core,Skill 不进 `ToolRegistry` |
| V 审计 Day One(NON-NEGOTIABLE) | 通过:`ToolExecutor` 成败都经 `ToolInvocationRecorder` 写 `tool_invocations`;模型调用审计沿用第16节 |
| VI 安全地基 | 通过(带说明):`SandboxChecker` 在第24节交付,且位于 `oryxos-tool`(core 看不到),本节在 `ToolExecutor` 执行前留注释位"24 节接线:Sandbox.enforce";无凭证落地 |
| VII 同步 + 虚拟线程 | 通过:全部同步调用;`ThreadLocal` 用 try/finally 清理 |
| VIII 配置即 Agent/无自动迁移 | 通过:读 Profile 字段;`schema.sql` 手工建表 |

**Gate 结论**:无违规,无需 Complexity Tracking。Post-Design 复核:同上。

> H4① 说明:本节 `ToolExecutor` 是工具涉外 IO 的入口,Sandbox 未就位,留调用位。

## Project Structure

### Documentation (this feature)

```text
specs/002-react-loop/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/core-interfaces.md
├── checklists/requirements.md
└── tasks.md            # /speckit-tasks 产出
```

### Source Code (repository root)

```text
oryxos-core/
├── pom.xml                                 # +spring-ai-model;+spring-boot-starter-test(test)
├── src/main/java/io/oryxos/core/
│   ├── agent/
│   │   ├── AgentService.java               # 统一入口:ProfileContext + ReActLoop + 持久化
│   │   ├── ProfileContext.java             # ThreadLocal<Profile>,set/current/clear
│   │   ├── ReActLoop.java                  # 纯调度
│   │   ├── PromptBuilder.java              # 四部分拼装 + 历史截断 + 日期时间
│   │   ├── ToolExecutor.java               # 唯一工具执行入口 + 审计
│   │   ├── ContextLoader.java              # Bootstrap + SKILL.md,不缓存
│   │   ├── ChatGateway.java                # 接口(provider 实现)
│   │   ├── ToolInvocationRecorder.java     # 接口(storage 实现)
│   │   └── MemoryContextProvider.java      # 可选接口(22 节实现)
│   ├── session/
│   │   ├── Session.java                    # 最小接口(18 节实现)
│   │   └── SessionManager.java             # 最小接口(18 节实现)
│   └── tool/
│       ├── OryxTool.java                   # +execute(String)
│       ├── ToolResult.java                 # record
│       └── ToolRegistry.java               # 最小接口(20 节实现)
└── src/test/java/io/oryxos/core/agent/
    ├── ReActLoopTest.java  PromptBuilderTest.java  ToolExecutorTest.java
    ├── AgentServiceTest.java  ContextLoaderTest.java
    └── (测试夹具:FakeSession 等,仅 test 源集)

oryxos-provider/  ProviderService implements ChatGateway(仅加 implements + @Override)
oryxos-storage/   ToolInvocation.java  ToolInvocationRepository.java
                  ToolInvocationAuditor.java(实现 ToolInvocationRecorder)
                  schema.sql(+tool_invocations)  ToolInvocationRepositoryTest.java
oryxos-boot/      装配 AgentService/ReActLoop/PromptBuilder/ToolExecutor/ContextLoader Bean
                  (无 Session/ToolRegistry 实现前,Bean 以 ObjectProvider/条件方式装配,见 research R5)
```

**Structure Decision**: 沿用第16节的包与模块习惯;core 新增 `agent`、`session` 两个包,`tool` 包沿用。`ToolCall` 直接使用 Spring AI 的 `AssistantMessage.ToolCall`,不新建类型。

## Complexity Tracking

无违规,不填。
