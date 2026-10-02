---
name: oryxos-init
description: 初始化 OryxOS(或同类 JDK 21 + Spring Boot 3.x 企业级单体)的工程地基:Maven 多模块骨架、结构化日志、Actuator + Prometheus 监控、Spring MVC + 虚拟线程、springdoc OpenAPI、统一响应体与全局异常、Google 格式 + 阿里 P3C 规约、SpotBugs/Find Security Bugs/PMD/OWASP Dependency-Check 安全检查、CI 与 pre-commit。Use when 用户要「初始化项目 / 搭工程骨架 / 起脚手架 / 加日志监控 / 加开发规范 / 加代码安全检查」或提到 oryxos-init。
---

# OryxOS 项目初始化

一次性、标准化地装好"工程地基"。具体配置片段见 [REFERENCE.md](REFERENCE.md)。

## 边界

- 不实现五大核心能力(Provider / ReAct / Memory / Tool / Web),那是业务模块,走 Spec-Kit user story。
- 不硬编码密钥 / token / API key,一律 `${ENV_VAR}` 占位。
- 不替换已有业务代码,只新增基础设施与配置;已存在的文件先询问,不覆盖。

## 硬约束(OryxOS constitution)

- JDK 21、Spring Boot 3.x、Maven 多模块、单二进制(fat JAR)
- HTTP 层用 Spring MVC + 虚拟线程,不引入响应式
- 持久化 SQLite + Spring Data JPA,本 skill 只配数据源,不建业务表
- 预留审计表 `tool_invocations` / `llm_calls` 的建表脚本位置
- 合并前必须通过格式、编码规约、安全扫描

## 步骤(每步完成后 `git commit`,当前若非 git 仓库先询问是否 `git init`)

0. **确认参数**:groupId、根 artifactId、模块清单(默认 9 模块)、端口(默认 8080)、JDK(21)。
1. **Maven 多模块骨架**:父 pom(packaging=pom)+ `oryxos-core / provider / memory / tool / web / storage / boot / cli / channel-cli`;`oryxos-boot` 含 `main`(`scanBasePackages="io.oryxos"`),打 fat JAR。生成 Maven Wrapper(`mvn -N wrapper:wrapper -Dmaven=<最新 3.9.x>`),之后一律用 `./mvnw`。`.gitignore` 忽略整个 `.idea/` 与 `.claude/scheduled_tasks.lock`、`.claude/settings.local.json`。带点的模块名(如 `channel-cli`)包目录要建成嵌套 `channel/cli/`,不要写成 `channel.cli/`。
2. **版本管理**:父 pom 的 `dependencyManagement` / `pluginManagement` 锁定 Spring Boot BOM(用 `spring-boot-dependencies` import,不继承 starter-parent)、Spring AI Alibaba BOM、SQLite JDBC、Picocli、SnakeYAML、logstash-logback-encoder、springdoc。版本取实施时最新稳定版,**先用 `curl` 查 maven-metadata.xml 确认**。**必须显式锁定 compiler / surefire / jar / resources / install 插件版本**,否则 Maven 默认的 compiler 3.1 不认 `release=21`。
3. **日志**:`logback-spring.xml`,dev 彩色 console,prod(profile)JSON + MDC `traceId`;禁止 `System.out`。
4. **监控**:actuator + micrometer-registry-prometheus,暴露 health / info / prometheus / metrics。
5. **HTTP**:spring-boot-starter-web,`spring.threads.virtual.enabled=true`。
6. **API 规范**:springdoc;`oryxos-web` 内建 `ApiResponse<T>` 与 `GlobalExceptionHandler`(覆盖 400/404/500/503);REST 用 `/api/v1` 前缀、资源名词复数。
7. **开发规范**:Spotless + google-java-format(格式)、阿里 P3C 挂 PMD(编码规约,**PMD 须固定 6.x**)、Checkstyle + `.editorconfig`(兜底)。自有规则文件放根目录 `config/`。风格冲突以 google-java-format 为准。**踩坑与对策见 [REFERENCE.md](REFERENCE.md) 的「已知坑」。**
8. **安全检查**:SpotBugs + Find Security Bugs、OWASP Dependency-Check(放 `security` profile,由 CI 启用,设 `failBuildOnCVSS`)。排除项写进 `config/spotbugs-exclude.xml` 且必须注明理由,优先改代码而非排除。
9. **CI + pre-commit**:钩子放 `.githooks/pre-commit`,用 `git config core.hooksPath .githooks` 启用;GitHub Actions 跑 `./mvnw verify` 与 `-Psecurity`,任一失败即阻断;设置 `permissions: contents: read`,actions 用最新主版本(核对为 node24);NVD 库缓存用 `cache/restore` + `cache/save`(`if: always()`)。
10. **验证**:见下。

## 验证

- `mvn clean verify` 全绿,`mvn clean package` 产出 fat JAR
- `./mvnw -pl oryxos-boot spring-boot:run` 能启动
- `cd oryxos-core && ../mvnw verify` 能单独构建(验证 `config/` 路径解析)
- `/actuator/health` 为 UP,`/actuator/prometheus` 有指标,`/swagger-ui.html` 可打开
- **负向验证(必做,"全绿"不等于检查生效)**:分别写一个格式错误、一个 `Executors.newFixedThreadPool`(P3C)、一个 `MessageDigest.getInstance("MD5")`(Find Security Bugs),确认各自被对应检查拦下,验证后删除
- 故意提交不合规代码,确认 pre-commit 拦截

## 完成清单

- [ ] 9 模块骨架 + fat JAR
- [ ] 结构化日志含 traceId,无 `System.out`
- [ ] Actuator / Prometheus / 虚拟线程 / Swagger 可用
- [ ] `ApiResponse` + `GlobalExceptionHandler` 就位
- [ ] Spotless + P3C + Checkstyle + `.editorconfig` 生效(已做负向验证)
- [ ] SpotBugs + FSB + PMD + Dependency-Check 接入 `mvn verify`
- [ ] pre-commit + CI 跑通
- [ ] 无明文密钥
