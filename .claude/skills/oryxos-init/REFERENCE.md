# 配置参考

版本号、插件坐标、`google_checks.xml` 路径以实施时官方文档为准,下面只给骨架,用 `${...version}` 占位。

## application.yaml 基础配置

```yaml
server.port: 8080
spring.threads.virtual.enabled: true   # JDK 21 虚拟线程
management:
  endpoints.web.exposure.include: health,info,prometheus,metrics
  endpoint.health.probes.enabled: true
  metrics.tags.application: oryxos
```

## 统一响应与错误

- `ApiResponse<T>`:`code` / `message` / `data` / `timestamp`
- `GlobalExceptionHandler`(`@RestControllerAdvice`):错误体含 `errorCode` / `message` / `timestamp`

## Spotless(Google 格式)

```xml
<plugin>
  <groupId>com.diffplug.spotless</groupId>
  <artifactId>spotless-maven-plugin</artifactId>
  <version>${spotless.version}</version>
  <configuration>
    <java>
      <googleJavaFormat><style>GOOGLE</style></googleJavaFormat>
      <removeUnusedImports/>
      <importOrder/>
    </java>
  </configuration>
  <executions><execution><goals><goal>check</goal></goals></execution></executions>
</plugin>
```

## 阿里 P3C(挂在 PMD 上)

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-pmd-plugin</artifactId>
  <version>${pmd.version}</version>
  <configuration>
    <rulesets>
      <ruleset>rulesets/java/ali-pmd.xml</ruleset>
      <ruleset>rulesets/java/ali-concurrent.xml</ruleset>
      <ruleset>rulesets/java/ali-exception.xml</ruleset>
    </rulesets>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>com.alibaba.p3c</groupId>
      <artifactId>p3c-pmd</artifactId>
      <version>${p3c.version}</version>
    </dependency>
  </dependencies>
  <executions><execution><goals><goal>check</goal></goals></execution></executions>
</plugin>
```

分工:Google 管「长什么样」(格式),阿里管「怎么写才对」(命名、并发、异常、集合、日志、SQL)。建议开发者本地装「阿里巴巴 Java 编码规约」IDE 插件。

## SpotBugs + Find Security Bugs

```xml
<plugin>
  <groupId>com.github.spotbugs</groupId>
  <artifactId>spotbugs-maven-plugin</artifactId>
  <version>${spotbugs.version}</version>
  <configuration>
    <effort>Max</effort><threshold>Low</threshold>
    <plugins><plugin>
      <groupId>com.h3xstream.findsecbugs</groupId>
      <artifactId>findsecbugs-plugin</artifactId>
      <version>${findsecbugs.version}</version>
    </plugin></plugins>
  </configuration>
</plugin>
```

## OWASP Dependency-Check

使用 `org.owasp:dependency-check-maven`,配置 `failBuildOnCVSS`(如 7),扫描第三方依赖已知 CVE。

## CI 顺序

`spotless:check` → checkstyle → spotbugs → pmd → dependency-check,统一由 `mvn verify` 串起。
