package io.oryxos.core.profile;

import java.util.List;
import java.util.Map;

/**
 * 一个 Agent 的完整声明(配置即 Agent)。字段一次建全,后续各节按需取用。
 *
 * <p>列表字段缺省为空列表,避免调用方到处判空。
 */
public record Profile(
    String name,
    String description,
    Identity identity,
    ProviderRef provider,
    List<String> tools,
    List<String> skills,
    List<String> mcpServers,
    List<String> channels,
    List<String> notifyChannels,
    List<Map<String, Object>> schedules,
    List<String> bootstrap,
    Settings settings) {

  public Profile {
    tools = tools == null ? List.of() : List.copyOf(tools);
    skills = skills == null ? List.of() : List.copyOf(skills);
    mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
    channels = channels == null ? List.of() : List.copyOf(channels);
    notifyChannels = notifyChannels == null ? List.of() : List.copyOf(notifyChannels);
    schedules = schedules == null ? List.of() : List.copyOf(schedules);
    bootstrap = bootstrap == null ? List.of() : List.copyOf(bootstrap);
    identity = identity == null ? new Identity(null, null) : identity;
    settings = settings == null ? new Settings(null, null) : settings;
  }

  /** Agent 身份:名称与身份提示词。 */
  public record Identity(String agentName, String prompt) {}

  /** 该 Agent 用哪个 provider、哪个 model、什么温度;provider 名必须能在全局层找到。 */
  public record ProviderRef(String name, String model, Double temperature) {}

  /** 运行设置。 */
  public record Settings(Integer maxIterations, Integer maxHistoryTurns) {}
}
