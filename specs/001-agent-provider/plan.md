# Implementation Plan: Agent Provider 与 Profile 加载

**Branch**: `001-lesson16-agent-provider` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-agent-provider/spec.md`

## Summary

在 `oryxos-provider` 交付薄薄一层 `ProviderService`:`chat(sessionId, Profile, Prompt)` 按 Profile 的 provider 名从**显式 `Map<String, ChatModel>`** 取模型,调用 `chatModel.call(prompt)`,**关闭 Spring AI 自动工具执行**,成败都经 `LlmCallAuditor` 写入 `llm_calls`。Profile 的 YAML 解析、校验与内存索引落在 `oryxos-core`;`llm_calls` 实体、Repository 与手工建表脚本落在 `oryxos-storage`。模型来源:全局层 `oryxos.providers` 声明,按 name + base-url + api-key 逐个手动构建 OpenAI 兼容 `ChatModel`(deepseek/kimi 均为 OpenAI 兼容协议)。

## Technical Context

**Language/Version**: Java 21(虚拟线程,同步阻塞)

**Primary Dependencies**: Spring Boot 3.5.16;Spring AI **1.1.2**(需新增 `spring-ai-bom` 导入与 `spring-ai-starter-model-openai`,见 research R1,**软门禁已获用户确认方案**);Spring AI Alibaba BOM 1.1.2.4-security-fix(已锁定,其中无 provider starter);SnakeYAML 2.4(已在 BOM);Spring Data JPA + sqlite-jdbc + hibernate-community-dialects(storage 已有)

**Storage**: SQLite;`llm_calls` 由 `schema.sql` 手工建表,`ddl-auto: none`(根 application.yaml 已是 none)

**Testing**: JUnit 5 + Mockito + AssertJ(spring-boot-starter-test);`@DataJpaTest` 风格切片跑 LlmCallRepositoryTest;`ProviderSmokeIT` 打 `@Tag("integration")`,默认不跑

**Target Platform**: 单机 fat JAR(JVM)

**Project Type**: Maven 多模块单体

**Performance Goals**: N/A(一次调用一次请求,不重试)

**Constraints**: 禁用 Spring AI 自动工具执行与 eager 模型自动装配;无 Reactor/CompletableFuture/自建线程池;P3C/PMD/SpotBugs 全绿;避开 Java 18+ 增强 switch `default ->` 写法

**Scale/Scope**: 本节 ~12 个主类 + 5 个测试类

## Constitution Check

| 原则 | 结论 |
|---|---|
| I 自实现 ReAct | 通过:本节不涉及循环,Provider 只发一次调用 |
| II Spring AI 仅协议转换(NON-NEGOTIABLE) | 通过:`internalToolExecutionEnabled=false`;排除 `OpenAiAutoConfiguration` 等自动装配,手动构建 `ChatModel`;调用 `chatModel.call(new Prompt(...))`;有回归测试钉死 |
| III Provider 显式映射 | 通过:`ProviderService` 构造入参即 `Map<String, ChatModel>`,不注入 `List<ChatModel>` |
| IV SKILL.md | 不涉及 |
| V 审计 Day One(NON-NEGOTIABLE) | 通过:`llm_calls` 成败都写;`tool_invocations` 属第 17 节 |
| VI 安全地基 | 通过:key 只来自环境变量占位;沙箱未就位,本节无涉外文件/Shell/HTTP 工具执行;Provider 自身对 LLM 的出站调用由 Spring AI 完成(留 24 节接线位说明见下) |
| VII 同步 + 虚拟线程 | 通过 |
| VIII 配置即 Agent / 无自动迁移 | 通过:Profile 即 YAML;`schema.sql` 手工建表 |

**Gate 结论**:无违规,Complexity Tracking 无需填写。Post-Design 复核:同上,无变化。

> H4① 说明:Provider 调 LLM 是对外 IO,但 `SandboxChecker` 在第 24 节才交付;本节在 `ProviderService.chat` 起始处留注释位"24 节接线:HTTP 域名白名单",不实现。

## Project Structure

### Documentation (this feature)

```text
specs/001-agent-provider/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/provider-api.md
├── checklists/requirements.md
└── tasks.md            # /speckit-tasks 产出
```

### Source Code (repository root)

```text
pom.xml                                   # 新增 spring-ai.version 属性 + spring-ai-bom import(R1)

oryxos-core/src/main/java/io/oryxos/core/
├── tool/OryxTool.java                    # 最小接口:name()/description()/getInputSchema()(决策1)
└── profile/
    ├── Profile.java                      # 全字段 record,含嵌套 ProviderRef/Identity/Settings
    ├── ProfileLoader.java                # 扫目录 + SafeConstructor 解析 + ${ENV} + provider 校验
    └── ProfileRegistry.java              # Map<String, Profile> 按 name 查
oryxos-core/src/main/java/io/oryxos/core/ErrorCode.java   # 视需要补 PROVIDER_NOT_FOUND(待核对既有枚举风格)
oryxos-core/src/test/java/io/oryxos/core/profile/ProfileLoaderTest.java

oryxos-provider/pom.xml                   # + spring-ai-starter-model-openai、+ spring-ai-model
oryxos-provider/src/main/java/io/oryxos/provider/
├── ProviderService.java                  # chat(sessionId, Profile, Prompt)
├── ProviderNotFoundException.java
├── ToolSchemaAdapter.java                # OryxTool -> Spring AI ToolDefinition,只翻译
├── LlmCallAuditor.java                   # record(...) -> LlmCallRepository
├── ProviderProperties.java               # oryxos.providers 全局层(name/base-url/api-key)
└── ProviderConfiguration.java            # 手动建表:跳过缺 key 的 provider,构建 ChatModel Map
oryxos-provider/src/test/java/io/oryxos/provider/
├── ProviderServiceTest.java
├── ToolSchemaAdapterTest.java
└── ProviderSmokeIT.java                  # @Tag("integration")

oryxos-storage/src/main/java/io/oryxos/storage/
├── LlmCall.java
└── LlmCallRepository.java
oryxos-storage/src/main/resources/schema.sql           # llm_calls 建表
oryxos-storage/src/test/java/io/oryxos/storage/LlmCallRepositoryTest.java

oryxos-boot/src/main/resources/application.yaml        # + oryxos.providers 全局层示例(env 占位)
oryxos-boot/src/main/resources/application.yaml        # + spring.autoconfigure.exclude(Spring AI 模型自动装配)
.oryxos/profiles/                                      # 示例 Profile(演示用,可选)
```

**Structure Decision**: 沿用现有 9 模块骨架,严格按课件落位表放置;`ProviderProperties`/`ProviderConfiguration`/`LlmCallAuditor`/`ProviderNotFoundException` 为课件伪代码或 §3.1/§3.2 隐含的必要承载,在 tasks 比对时单列说明。依赖方向:provider→core、storage→core、provider→storage(审计写入,仅依赖 `LlmCallRepository`,不依赖 JPA 细节)。

**注意依赖**:`LlmCallAuditor` 需调用 `LlmCallRepository`,需在 `oryxos-provider/pom.xml` 增加对 `oryxos-storage` 的依赖(现有 pom 只依赖 core)。属模块依赖新增,非第三方依赖,但已在此标注。
