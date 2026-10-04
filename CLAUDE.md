# CLAUDE.md

OryxOS:分布式 AI Agent OS(运行 Agent 的底座)。JDK 21 + Spring Boot 3.5 的 Maven 多模块单体,打包为单个 fat JAR。当前处于阶段一:单机运行时内核。完整背景见 `docs/`。

## 常用命令

```bash
./mvnw clean verify                  # 测试 + 全部代码检查(Maven Wrapper 固定 3.9.16)
./mvnw spotless:apply                # 自动修复 Google 格式
./mvnw -pl oryxos-core -am verify    # 只构建某个模块及其依赖
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar [--spring.profiles.active=prod]
./mvnw verify -Psecurity             # OWASP Dependency-Check,需环境变量 NVD_API_KEY(CI 执行)
git config core.hooksPath .githooks  # 启用 pre-commit
```

`verify` 依次执行 Spotless → Checkstyle(`config/checkstyle.xml`)→ PMD + 阿里 P3C(`config/pmd-ruleset.xml`)→ SpotBugs + Find Security Bugs(`config/spotbugs-exclude.xml`),任一失败即阻断。

端口:业务 `8080`(`ORYXOS_PORT`),Actuator 独立管理端口 `8081`(`ORYXOS_MANAGEMENT_PORT`,默认只监听 `127.0.0.1`,由 `ORYXOS_MANAGEMENT_ADDRESS` 控制)。SQLite 路径 `ORYXOS_DB_PATH`(默认 `./oryxos.db`)。敏感配置一律走环境变量,不入库。

## 模块

`oryxos-core`(核心抽象与接口:`OryxTool`、`Session`、`Profile`、`ReActLoop`、`ToolExecutor`、`AgentService` 等,错误码与业务异常)、`oryxos-provider`(LLM Provider)、`oryxos-memory`(记忆)、`oryxos-tool`(内置 Tool、MCP Client、`ToolRegistry`、`SandboxChecker`)、`oryxos-storage`(SQLite + JPA)、`oryxos-web`(统一响应体、全局异常、traceId、OpenAPI)、`oryxos-channel-cli`、`oryxos-cli`(Picocli)、`oryxos-boot`(启动,产出 fat JAR)。

provider / memory / tool 等业务模块多为待开发骨架,按 `docs/TechnicalSolution.md` 第 10 章的职责划分实现。新增 Channel / Tool 只加新模块,不改 core;模块间通过接口解耦。

## 不可违反的架构原则(来自 docs/TechnicalSolution.md,constitution 级)

1. **自实现 ReAct loop**,不使用 Spring AI 的 Agent 抽象。
2. **Spring AI 只用一半**:仅用 Provider 抽象、协议转换、`@Tool` schema 生成。**必须禁用 Spring AI 自动 tool 执行**,否则 tool 会被调两次;调度完全由 `ReActLoop` + `ToolExecutor` 控制。这是最容易写错的一条。
3. **`OryxTool` 抽象统一所有 Tool**(内置、`@Tool` Bean、MCP),`ReActLoop` 不感知 Tool 来源。`ToolResult` 含成功标识、内容、错误信息、是否可重试。
4. **同步阻塞 + 虚拟线程**,Spring MVC,不引入响应式。
5. **Sandbox 用应用层 Path / Pattern 白名单**(`SandboxChecker`:文件路径、Shell 命令首 token、HTTP 域名),失败抛 `SandboxViolationException`。不要使用 `SecurityManager`(JDK 21 已不可用)。
6. **持久化**:SQLite + Spring Data JPA;长期记忆用 `MEMORY.md` 文件 + 关键词检索(向量检索留待后续);`tool_invocations` 与 `llm_calls` 审计表从第一天就写入。
7. **配置即 Agent**:Agent 由 Profile YAML(`.oryxos/profiles/`)定义;`SKILL.md` 是注入 system prompt 的指令模板,由 `ContextLoader` 加载,不属于 Tool 体系。
8. **分阶段克制**:只做运行时内核最小完备集;多租户、SSO、完整审计、Tool Policy、子进程/Docker 沙箱、SSE 流式、分布式均属后续阶段,不要提前实现。
9. 实例无状态、状态外置;安全是地基:凭证不落地、最小权限、全链路可审计。

## 开发约定

- 代码风格 Google 格式 + 阿里 P3C;注释使用中文。SpotBugs 排除项必须写明理由。
- 依赖版本集中在根 `pom.xml` 的 properties。多个版本被刻意固定以消除 CVE(Tomcat、log4j、swagger-ui、spring-ai-alibaba security-fix 等),升级前确认不会回退;被抑制的 CVE 在 `config/dependency-check-suppressions.xml` 中附理由与过期日。
- 开发流程:主体阶段按 Spec-Kit(constitution → specify → plan → tasks → implement)按 user story 拆分,每个 story 完成后须有可演示 Demo;增量阶段用手动提示词。不允许 AI 自行修改 constitution。详见 `docs/AiProgrammingGuide.md`。
- 提交信息沿用 `type(scope): 中文描述` 风格,如 `fix(security): ...`。
- `.idea/` 不入库。

## 文档索引

- `docs/oryxos.md` 项目总览与设计原则
- `docs/IndustryResearch.md` 业界调研
- `docs/DemandAnalysis.md` 需求文档
- `docs/TechnicalSolution.md` 技术方案(架构、7 个关键决策、工程结构、数据模型)
- `docs/AiProgrammingGuide.md` AI 编程指南
