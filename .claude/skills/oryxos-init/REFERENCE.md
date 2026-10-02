# 配置参考

版本号、插件坐标以实施时官方文档为准,下面只给骨架,用 `${...version}` 占位。

## 已知坑(实测于 JDK 21 + Maven 3.8.8 + Spring Boot 3.5)

| 现象 | 原因 | 对策 |
| --- | --- | --- |
| 编译报「不再支持源选项 5」 | 未用 starter-parent,compiler 插件默认 3.1 | `pluginManagement` 锁定 compiler(3.14+)、surefire、jar、resources、install |
| spotbugs 报 requires Maven 3.8.9 | spotbugs-maven-plugin 4.10.x 要求 Maven ≥ 3.8.9 | 用 4.9.x,或加 Maven Wrapper |
| PMD 找不到 `rulesets/java/ali-pmd.xml` | p3c-pmd 2.1.1 里没有该文件 | 实际为 `ali-{comment,concurrent,constant,exception,flowcontrol,naming,oop,orm,other,set}.xml` |
| **P3C 一条规则都没跑,却显示通过** | PMD 6 的 ASM 读不了 Java 21 class(major 65),文件处理出错被默认忽略 | 配 `typeResolution=false` 且 **`skipPmdError=false`**;PMD 插件固定 3.21.2(PMD 6.55),p3c 基于 PMD 6,不能用 PMD 7 |
| P3C 要求每个类写 `@author` | `ClassMustHaveAuthorRule` | 在 `config/pmd-ruleset.xml` 中 `<exclude>`,作者由 git 记录 |
| Checkstyle 报 SummaryJavadoc | 只认 ASCII 句号,不认中文「。」 | 复制 `google_checks.xml` 到 `config/checkstyle.xml`,移除 `SummaryJavadoc` 与 `MissingJavadocMethod` |
| Find Security Bugs 报 `CRLF_INJECTION_LOGS` | prod 日志为 JSON,换行已转义 | `config/spotbugs-exclude.xml` 排除并写理由 |
| FSB 报 `HRS_REQUEST_PARAMETER_TO_HTTP_HEADER` | 请求头 traceId 回写响应头 | 先用白名单正则 `[A-Za-z0-9-]{1,64}` 校验,再排除并写理由 |
| `@SpringBootTest` 下 `/actuator/prometheus` 404 | 测试默认关闭指标导出 | 测试类加 `@AutoConfigureObservability` |
| 未知路径返回 500 | 缺 `NoResourceFoundException` 处理;405/415 同理 | 处理 404 与 `HttpRequestMethodNotSupported` 等框架 4xx,保留原状态码 |
| Dependency-Check 报 Invalid API Key | 13.x 强制要求 NVD API key | 申请免费 key,设环境变量 `NVD_API_KEY`;CI 放 Secrets |
| macOS `sed -i` 报 invalid command code | BSD sed 需要 `-i ''`,且不支持 `\n` 替换 | 用 Python 做多行替换 |

非 git 仓库时 `git commit` 需先 `git init`;提交作者缺失时用 `-c user.name/user.email`。

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

规则集写在 `config/pmd-ruleset.xml`(逐个 `<rule ref="rulesets/java/ali-xxx.xml"/>`,`ali-comment` 中排除 `ClassMustHaveAuthorRule`),插件只引用该文件。

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-pmd-plugin</artifactId>
  <version>${pmd-plugin.version}</version>
  <configuration>
    <targetJdk>17</targetJdk>
    <typeResolution>false</typeResolution>
    <skipPmdError>false</skipPmdError>
    <rulesets>
      <ruleset>${maven.multiModuleProjectDirectory}/config/pmd-ruleset.xml</ruleset>
    </rulesets>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>com.alibaba.p3c</groupId>
      <artifactId>p3c-pmd</artifactId>
      <version>${p3c.version}</version>
    </dependency>
    <!-- 显式固定 PMD 6 -->
    <dependency><groupId>net.sourceforge.pmd</groupId><artifactId>pmd-core</artifactId><version>${pmd.version}</version></dependency>
    <dependency><groupId>net.sourceforge.pmd</groupId><artifactId>pmd-java</artifactId><version>${pmd.version}</version></dependency>
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
    <excludeFilterFile>${maven.multiModuleProjectDirectory}/config/spotbugs-exclude.xml</excludeFilterFile>
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

使用 `org.owasp:dependency-check-maven` 的 `aggregate` 目标,放在 `security` profile 中,配置 `failBuildOnCVSS`(如 7)与 `nvdApiKeyEnvironmentVariable=NVD_API_KEY`。**13.x 必须提供 NVD API key,否则报 `Invalid API Key`**(本地未验证通过,需配 key 后确认)。首次需下载 NVD 库,耗时长,故不放进日常 `verify`,由 CI 用 `-Psecurity` 执行。

## CI 顺序

`spotless:check` → checkstyle → spotbugs → pmd → dependency-check,统一由 `mvn verify` 串起。
