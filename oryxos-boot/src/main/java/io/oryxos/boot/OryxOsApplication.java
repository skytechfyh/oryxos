package io.oryxos.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** OryxOS 启动入口。 */
@SpringBootApplication(scanBasePackages = "io.oryxos")
public class OryxOsApplication {

  public static void main(String[] args) {
    SpringApplication.run(OryxOsApplication.class, args);
  }
}
