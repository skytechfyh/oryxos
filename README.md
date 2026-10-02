# OryxOS

JDK 21 + Spring Boot 3.5 的企业级单体,Maven 多模块,打包为单个 fat JAR。

## 模块

| 模块 | 职责 |
| --- | --- |
| `oryxos-core` | 领域模型、统一错误码与业务异常 |
| `oryxos-provider` / `memory` / `tool` | 业务能力模块(待按 Spec-Kit 开发) |
| `oryxos-storage` | SQLite + Spring Data JPA,含审计表脚本预留位 |
| `oryxos-web` | 统一响应体、全局异常处理、traceId 过滤器、OpenAPI |
| `oryxos-cli` / `channel-cli` | 命令行入口与渠道 |
| `oryxos-boot` | 启动模块,产出 fat JAR |

## 构建与运行

```bash
mvn clean verify                      # 测试 + 全部代码检查
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar
java -jar ... --spring.profiles.active=prod   # JSON 日志
```

| 地址 | 说明 |
| --- | --- |
| `/actuator/health` | 健康检查 |
| `/actuator/prometheus` | Prometheus 指标 |
| `/swagger-ui.html` | API 文档 |

环境变量:`ORYXOS_PORT`(默认 8080)、`ORYXOS_DB_PATH`(默认 `./oryxos.db`)。敏感配置一律走环境变量,不入库。

## 代码规范与安全检查

`mvn verify` 按顺序执行,任一失败即阻断:

1. **Spotless** — Google 格式。自动修复:`mvn spotless:apply`
2. **Checkstyle** — `config/checkstyle.xml`(基于 google_checks,去掉不适合中文注释的两条)
3. **PMD + 阿里 P3C** — `config/pmd-ruleset.xml`
4. **SpotBugs + Find Security Bugs** — 排除项见 `config/spotbugs-exclude.xml`(每条须写理由)
5. **OWASP Dependency-Check** — 需 `-Psecurity`,由 CI 执行;**必须**配置环境变量 `NVD_API_KEY`(免费申请),CI 中放入仓库 Secrets

启用 pre-commit:`git config core.hooksPath .githooks`
