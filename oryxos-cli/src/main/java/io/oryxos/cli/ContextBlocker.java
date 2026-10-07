package io.oryxos.cli;

import java.util.concurrent.CountDownLatch;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

/**
 * 让常驻命令(serve、gateway)阻塞到容器关闭。
 *
 * <p>入口 {@code main} 在命令返回后会退出进程,所以常驻命令不能直接返回。这里用同步的 {@link CountDownLatch} 等待 {@link
 * ContextClosedEvent},不自建线程、不引入异步模型。
 */
final class ContextBlocker {

  /** 工具类,禁止实例化。 */
  private ContextBlocker() {}

  /**
   * 注册关闭钩子并阻塞,直到容器关闭。
   *
   * @param context 已启动的容器
   * @throws InterruptedException 等待被中断
   */
  static void awaitClose(ConfigurableApplicationContext context) throws InterruptedException {
    CountDownLatch closed = new CountDownLatch(1);
    context.addApplicationListener(
        event -> {
          if (event instanceof ContextClosedEvent) {
            closed.countDown();
          }
        });
    // 收到 SIGTERM/Ctrl-C 时优雅关闭容器,从而放开下面的等待
    context.registerShutdownHook();
    closed.await();
  }
}
