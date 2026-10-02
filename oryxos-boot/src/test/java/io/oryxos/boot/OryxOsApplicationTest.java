package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@AutoConfigureObservability
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:sqlite::memory:")
class OryxOsApplicationTest {

  @Autowired private TestRestTemplate rest;

  @LocalManagementPort private int managementPort;

  private String management(String path) {
    return "http://127.0.0.1:" + managementPort + path;
  }

  @Test
  void healthIsUp() {
    ResponseEntity<String> resp = rest.getForEntity(management("/actuator/health"), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).contains("\"UP\"");
  }

  @Test
  void prometheusExposesMetrics() {
    ResponseEntity<String> resp =
        rest.getForEntity(management("/actuator/prometheus"), String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).contains("jvm_memory_used_bytes");
  }

  @Test
  void actuatorIsNotExposedOnBusinessPort() {
    assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(rest.getForEntity("/actuator/prometheus", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void swaggerUiAndApiDocsAvailable() {
    assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void traceIdHeaderIsReturned() {
    ResponseEntity<String> resp = rest.getForEntity("/api/v1/not-exist", String.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getHeaders().getFirst("X-Trace-Id")).isNotBlank();
  }
}
