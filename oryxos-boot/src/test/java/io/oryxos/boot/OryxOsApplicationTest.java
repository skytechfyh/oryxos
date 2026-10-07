package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.session.SessionManager;
import io.oryxos.storage.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 启动冒烟:健康检查、监控、OpenAPI、traceId,以及 JPA 是否扫描到 storage 模块。 */
@AutoConfigureObservability
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:sqlite::memory:")
class OryxOsApplicationTest {

  @Autowired private TestRestTemplate rest;

  @Autowired private ApplicationContext context;

  @LocalManagementPort private int managementPort;

  /**
   * 拼出管理端口上的完整 URL。
   *
   * @param path 路径
   * @return URL
   */
  private String management(String path) {
    return "http://127.0.0.1:" + managementPort + path;
  }

  /** 健康检查在管理端口返回 UP。 */
  @Test
  void healthIsUp() {
    ResponseEntity<String> resp = rest.getForEntity(management("/actuator/health"), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).contains("\"UP\"");
  }

  /** Prometheus 端点暴露 JVM 指标。 */
  @Test
  void prometheusExposesMetrics() {
    ResponseEntity<String> resp =
        rest.getForEntity(management("/actuator/prometheus"), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).contains("jvm_memory_used_bytes");
  }

  /** Actuator 不暴露在业务端口。 */
  @Test
  void actuatorIsNotExposedOnBusinessPort() {
    assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(rest.getForEntity("/actuator/prometheus", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** OpenAPI 文档与 Swagger UI 可访问。 */
  @Test
  void swaggerUiAndApiDocsAvailable() {
    assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  /** 响应头带 traceId。 */
  @Test
  void traceIdHeaderIsReturned() {
    ResponseEntity<String> resp = rest.getForEntity("/api/v1/not-exist", String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getHeaders().getFirst("X-Trace-Id")).isNotBlank();
  }

  /**
   * 重命令启动后,存储模块必须被 JPA 扫描到(对应启动日志 "Found N JPA repository interfaces" 的 N&gt;0)。
   *
   * <p>{@code @EnableJpaRepositories} 与 {@code @EntityScan} 不跟随 {@code
   * scanBasePackages},漏声明时审计和会话都写不进去。
   */
  @Test
  void storageModuleIsScannedByJpa() {
    assertThat(context.getBeansOfType(SessionRepository.class)).hasSize(1);
    assertThat(context.getBean(SessionManager.class)).isNotNull();
  }
}
