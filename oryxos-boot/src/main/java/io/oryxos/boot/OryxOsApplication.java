package io.oryxos.boot;

import io.oryxos.cli.OryxOsCli;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * OryxOS 启动入口。
 *
 * <p>实体与 Repository 位于 io.oryxos.storage,不在启动类所在包下,默认不会被 JPA 扫描,因此显式声明。
 *
 * <p>{@code main} 把参数交给 {@link OryxOsCli}:轻命令不启动 Spring,重命令(chat、serve、gateway)才通过下面注入的函数启动容器。 起
 * Web Service 现在要写 {@code serve} 子命令。
 */
@SpringBootApplication(scanBasePackages = "io.oryxos")
@EntityScan("io.oryxos.storage")
@EnableJpaRepositories("io.oryxos.storage")
public class OryxOsApplication {

  /**
   * 进程入口,委托给命令行。
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    System.exit(OryxOsCli.run(args, a -> SpringApplication.run(OryxOsApplication.class, a)));
  }
}
