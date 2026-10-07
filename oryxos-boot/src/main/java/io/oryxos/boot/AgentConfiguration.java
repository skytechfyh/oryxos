package io.oryxos.boot;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.agent.ChatGateway;
import io.oryxos.core.agent.ContextLoader;
import io.oryxos.core.agent.MemoryContextProvider;
import io.oryxos.core.agent.PromptBuilder;
import io.oryxos.core.agent.ReActLoop;
import io.oryxos.core.agent.ToolExecutor;
import io.oryxos.core.agent.ToolInvocationRecorder;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.SessionManager;
import io.oryxos.core.tool.ToolRegistry;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * 启动接线:把 ReAct 相关组件装配成可用的 {@link AgentService}。
 *
 * <p>整个配置类是懒加载的:{@link ToolRegistry} 的实现由第 20 节交付,在此之前容器里没有这个 Bean({@link SessionManager} 已由第 18 节的
 * {@code JpaSessionManager} 提供)。懒加载让应用照常启动,只有真正有人取 AgentService 时才解析依赖;第 20 节落地后无需改这里。 不用
 * {@code @ConditionalOnBean},因为它对 Bean 注册顺序敏感,模块增多后不可靠。
 */
@Lazy
@Configuration
public class AgentConfiguration {

  /** 工作区根目录约定,见 TechnicalSolution §8.1。 */
  private static final Path WORKSPACE = Path.of(".oryxos");

  /**
   * 上下文加载器,每次读文件、不缓存。
   *
   * @return ContextLoader
   */
  @Bean
  public ContextLoader contextLoader() {
    return new ContextLoader(WORKSPACE);
  }

  /**
   * 提示组装器;长期记忆提供者由第 22 节交付,没有则传 null 并跳过该部分。
   *
   * @param contextLoader 上下文加载器
   * @param toolRegistry 工具注册表
   * @param memoryProvider 长期记忆提供者(可选)
   * @return PromptBuilder
   */
  @Bean
  public PromptBuilder promptBuilder(
      ContextLoader contextLoader,
      ToolRegistry toolRegistry,
      ObjectProvider<MemoryContextProvider> memoryProvider) {
    return new PromptBuilder(
        contextLoader, toolRegistry, memoryProvider.getIfAvailable(), Clock.systemDefaultZone());
  }

  /**
   * 工具执行器,工具执行的唯一入口。
   *
   * @param toolRegistry 工具注册表
   * @param recorder 审计写入口,由 storage 实现
   * @return ToolExecutor
   */
  @Bean
  public ToolExecutor toolExecutor(ToolRegistry toolRegistry, ToolInvocationRecorder recorder) {
    return new ToolExecutor(toolRegistry, recorder);
  }

  /**
   * ReAct 循环。
   *
   * @param promptBuilder 提示组装器
   * @param chatGateway 模型调用出口,由 provider 的 ProviderService 实现
   * @param toolExecutor 工具执行器
   * @return ReActLoop
   */
  @Bean
  public ReActLoop reActLoop(
      PromptBuilder promptBuilder, ChatGateway chatGateway, ToolExecutor toolExecutor) {
    return new ReActLoop(promptBuilder, chatGateway, toolExecutor);
  }

  /**
   * 三种触发源共用的统一处理入口。
   *
   * @param profileRegistry Profile 索引
   * @param reActLoop ReAct 循环
   * @param sessionManager 会话管理
   * @return AgentService
   */
  @Bean
  public AgentService agentService(
      ProfileRegistry profileRegistry, ReActLoop reActLoop, SessionManager sessionManager) {
    return new AgentService(profileRegistry, reActLoop, sessionManager);
  }
}
