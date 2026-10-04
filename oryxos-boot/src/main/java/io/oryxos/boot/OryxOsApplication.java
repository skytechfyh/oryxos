package io.oryxos.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * OryxOS 启动入口。
 *
 * <p>实体与 Repository 位于 io.oryxos.storage,不在启动类所在包下,默认不会被 JPA 扫描,因此显式声明。
 */
@SpringBootApplication(scanBasePackages = "io.oryxos")
@EntityScan("io.oryxos.storage")
@EnableJpaRepositories("io.oryxos.storage")
public class OryxOsApplication {

  public static void main(String[] args) {
    SpringApplication.run(OryxOsApplication.class, args);
  }
}
