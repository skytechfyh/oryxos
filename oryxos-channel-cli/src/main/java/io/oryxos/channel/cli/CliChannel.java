package io.oryxos.channel.cli;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 终端对话通道:读 stdin、写 stdout,维护当前会话,每行输入交给引擎处理。
 *
 * <p>它是消息进出的门,不是干活的人:不思考、不调模型、不执行工具,CLI 唯一自己判断的逻辑是 {@code /quit}。输入输出流由构造器注入,
 * 便于测试;交互输出是产品功能而非日志,日志仍走 SLF4J。
 */
public class CliChannel {

  private static final Logger LOG = LoggerFactory.getLogger(CliChannel.class);

  /** 渠道名,会话身份三元组的第一项。 */
  private static final String CHANNEL = "cli";

  /** 退出指令。 */
  private static final String QUIT = "/quit";

  private final AgentService agentService;
  private final SessionManager sessionManager;
  private final ProfileRegistry profileRegistry;
  private final BufferedReader in;
  private final PrintStream out;
  private final PrintStream err;

  /**
   * 构造通道。
   *
   * @param agentService 引擎统一入口
   * @param sessionManager 会话管理
   * @param profileRegistry Profile 索引,用于启动前确认 Agent 存在
   * @param in 输入流(生产环境为 stdin)
   * @param out 标准输出(回复打印到这里)
   * @param err 错误输出(错误提示打印到这里)
   */
  public CliChannel(
      AgentService agentService,
      SessionManager sessionManager,
      ProfileRegistry profileRegistry,
      InputStream in,
      PrintStream out,
      PrintStream err) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.profileRegistry = profileRegistry;
    this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    this.out = out;
    this.err = err;
  }

  /**
   * 进入交互循环,直到用户输入 {@code /quit} 或输入流结束。
   *
   * @param profileName 要对话的 Agent(Profile)名
   * @param user 当前用户标识
   * @return 退出码:正常结束 0;Agent 不存在 1;读取输入失败 2
   */
  public int run(String profileName, String user) {
    if (profileRegistry.find(profileName).isEmpty()) {
      err.println("错误: Agent 不存在: " + profileName);
      return 1;
    }
    // 身份只给三元组,session_id 的拼接由 SessionManager 内部完成
    Session session = sessionManager.getOrCreate(CHANNEL, user, profileName);
    try {
      while (true) {
        out.print("> ");
        out.flush();
        String line = in.readLine();
        if (line == null || QUIT.equals(line.trim())) {
          return 0;
        }
        if (line.isBlank()) {
          continue;
        }
        handle(session, line);
      }
    } catch (IOException e) {
      LOG.error("读取终端输入失败", e);
      err.println("错误: 读取输入失败: " + e.getMessage());
      return 2;
    }
  }

  /**
   * 把一行输入交给引擎并打印回复。
   *
   * <p>引擎失败时向用户打印错误并记日志,然后让循环继续:一轮失败不应让整个对话退出,也不能吞掉错误。
   *
   * @param session 当前会话
   * @param line 用户输入
   */
  private void handle(Session session, String line) {
    try {
      out.println(agentService.process(session, line));
    } catch (RuntimeException e) {
      LOG.error("处理用户输入失败", e);
      err.println("错误: " + e.getMessage());
    }
  }
}
