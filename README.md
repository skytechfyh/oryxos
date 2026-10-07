# OryxOS：分布式 AI Agent OS

运行各种 Agent 的底座。一份配置定义一个 Agent,一个底座运行一群 Agent,私有部署,数据不出域。原生对接 MCP 与 A2A 开放协议。长期目标是成为 Apache 顶级项目(协议 Apache 2.0,生态 oryx-labs)。

> Agent runtime 让一个 Agent 跑起来,Agent OS 让一群 Agent 被运行和管理起来。OryxOS 做的是后者。

JDK 21 + Spring Boot 3.5 的企业级单体,Maven 多模块,打包为单个 fat JAR。

## 五大核心能力

| 能力 | 说明 |
| --- | --- |
| 对接 LLM | Provider 抽象(基于 Spring AI Alibaba),Agent 不感知厂商,运行时切换,支持本地推理 |
| ReAct 循环 | 自实现推理引擎,不套外部 Agent 框架;禁用 Spring AI 自动 tool 执行,由 `ReActLoop` + `ToolExecutor` 掌控 |
| 记忆 | 会话记忆 + 长期记忆(`MEMORY.md` 文件 + 关键词检索),预留向量检索升级 |
| 工具体系 | 内置文件 / Shell / HTTP / 记忆工具;三档扩展:`SKILL.md` + 复用 MCP(零代码)、自写 MCP server、Java `@Tool` Bean |
| 对外服务 | REST API 对外暴露,任何语言可集成 |

## 核心特性

- **配置即 Agent**:一份 Profile 配置定义一个 Agent,多个 Agent 同实例并存
- **安全隔离**:文件路径、Shell 命令、HTTP 域名白名单校验(`SandboxChecker`),凭证走环境变量,工具调用与 LLM 调用审计落库
- **对接开放标准**:工具用 MCP、协作用 A2A、技能用 SKILL.md
- **无状态可扩展**:实例无状态、状态外置,为分布式留好路
- **技术栈**:JDK 21(虚拟线程)、Spring Boot 3.x、Spring MVC、SQLite + Spring Data JPA、Picocli、SnakeYAML、Micrometer + Prometheus

## 路线图

1. **阶段一(当前)单机运行时内核**:五大能力跑通,配置即 Agent、多 Agent 并存、REST 接入、对接 MCP
2. **阶段二(规划)底座分布式**:节点无状态化、状态外置、多副本部署
3. **阶段三(愿景)跨节点 Agent 协作**:Agent 通信底座,对接 A2A
4. **横向能力**:多租户、SSO、完整审计、工具策略、可观测、Web 管理

## 模块

| 模块 | 职责 |
| --- | --- |
| `oryxos-core` | 核心抽象:`OryxTool`、`Session`、`Profile`、`ContextLoader`、`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`;统一错误码与业务异常 |
| `oryxos-provider` | 能力一:`ProviderService`、Function Calling 适配、provider 名到 `ChatModel` 的显式映射(待开发) |
| `oryxos-memory` | 能力三:`MemoryService`、`LongTermMemory`、`save_memory` / `recall_memory`(待开发) |
| `oryxos-tool` | 能力四:内置 Tool、`McpClientService`、`McpToolAdapter`、`ToolRegistry`、`SandboxChecker`(待开发) |
| `oryxos-storage` | SQLite + Spring Data JPA;`tool_invocations`、`llm_calls` 审计表 |
| `oryxos-web` | 能力五:统一响应体、全局异常处理、traceId 过滤器、OpenAPI、Agent 接口 |
| `oryxos-cli` / `channel-cli` | Picocli 命令行入口 / CLI 渠道(`oryxos chat`) |
| `oryxos-boot` | 启动模块,产出 fat JAR |

模块间通过接口解耦;新增 Channel 或 Tool 只加新模块,不改 core。

## 文档

| 文档 | 内容 |
| --- | --- |
| [`docs/oryxos.md`](docs/oryxos.md) | 项目总览:定位、愿景、设计原则 |
| [`docs/IndustryResearch.md`](docs/IndustryResearch.md) | 业界调研 |
| [`docs/DemandAnalysis.md`](docs/DemandAnalysis.md) | 需求文档 |
| [`docs/TechnicalSolution.md`](docs/TechnicalSolution.md) | 技术方案:7 个关键决策、架构、数据持久化、工程结构 |
| [`docs/AiProgrammingGuide.md`](docs/AiProgrammingGuide.md) | AI 编程指南:Spec-Kit + Claude Code 的实施方式 |

## 构建与运行

```bash
./mvnw clean verify                   # 测试 + 全部代码检查(Maven Wrapper 固定 3.9.16)
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar serve
java -jar ... serve --spring.profiles.active=prod   # JSON 日志
java -jar ... --help                  # 12 个子命令:init / status / chat / serve / gateway / profile / provider / tool / session
```

| 地址 | 说明 |
| --- | --- |
| `http://localhost:8080/swagger-ui.html` | API 文档(业务端口) |
| `http://127.0.0.1:8081/actuator/health` | 健康检查(管理端口) |
| `http://127.0.0.1:8081/actuator/prometheus` | Prometheus 指标(管理端口) |

Actuator 使用独立管理端口,且默认只监听 `127.0.0.1`,业务端口上访问不到。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ORYXOS_PORT` | `8080` | 业务端口 |
| `ORYXOS_MANAGEMENT_PORT` | `8081` | Actuator 管理端口 |
| `ORYXOS_MANAGEMENT_ADDRESS` | `127.0.0.1` | 管理端口监听地址。容器部署、需要 Prometheus 跨主机抓取时设为 `0.0.0.0`,并用网络策略限制来源 |
| `ORYXOS_DB_PATH` | `./oryxos.db` | SQLite 文件路径 |敏感配置一律走环境变量,不入库。

## 代码规范与安全检查

`mvn verify` 按顺序执行,任一失败即阻断:

1. **Spotless** — Google 格式。自动修复:`./mvnw spotless:apply`
2. **Checkstyle** — `config/checkstyle.xml`(基于 google_checks,去掉不适合中文注释的两条)
3. **PMD + 阿里 P3C** — `config/pmd-ruleset.xml`
4. **SpotBugs + Find Security Bugs** — 排除项见 `config/spotbugs-exclude.xml`(每条须写理由)
5. **OWASP Dependency-Check** — 需 `-Psecurity`,由 CI 执行;**必须**配置环境变量 `NVD_API_KEY`(免费申请),CI 中放入仓库 Secrets

启用 pre-commit:`git config core.hooksPath .githooks`

IDE:`.idea/` 不入库,用 IntelliJ IDEA 直接打开根目录 `pom.xml` 导入即可。
