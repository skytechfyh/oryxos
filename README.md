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
./mvnw clean verify                   # 测试 + 全部代码检查(Maven Wrapper 固定 3.9.16)
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar
java -jar ... --spring.profiles.active=prod   # JSON 日志
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
