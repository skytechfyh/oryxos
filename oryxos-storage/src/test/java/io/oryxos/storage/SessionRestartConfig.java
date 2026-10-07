package io.oryxos.storage;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 模拟重启用的独立最小配置。
 *
 * <p>单独成类而不是嵌套进测试类:嵌套的配置会被测试切片一并识别,与切片自带配置叠加。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("io.oryxos.storage")
@EnableJpaRepositories("io.oryxos.storage")
@Import(JpaSessionManager.class)
class SessionRestartConfig {}
