# Contracts: core 对外接口(第17节)

这些签名第18/20/22节补实现时**不得改动**。

```java
// io.oryxos.core.agent
public interface ChatGateway {
  ChatResponse chat(String sessionId, Profile profile, Prompt prompt, List<OryxTool> availableTools);
}                                   // ProviderService implements(第16节 4 参重载)

public interface ToolInvocationRecorder {
  void record(String sessionId, String toolName, String input,
              boolean success, String errorMessage, long durationMs);
}                                   // storage 的 ToolInvocationAuditor 实现;自身出错只记日志不外抛

public interface MemoryContextProvider {          // 可选;无 Bean 则跳过长期记忆
  Optional<String> longTermMemory(Profile profile);
}

public class ReActLoop     { public String run(Session s, String userMessage, Profile p); }
public class PromptBuilder { public Prompt build(Session s, Profile p);
                             public List<OryxTool> availableTools(Profile p); }
public class ToolExecutor  { public ToolResult execute(String sessionId, AssistantMessage.ToolCall call); }
public class AgentService  { public String process(Session s, String userMessage); }
public final class ProfileContext { static void set(Profile); static Profile current(); static void clear(); }
public class ContextLoader { public String load(Profile p); }   // 每次现读,不缓存

// io.oryxos.core.session
public interface Session {
  String id(); String profileName(); List<Message> messages();
  void append(String userMessage); void append(ChatResponse response);
  void appendToolResult(AssistantMessage.ToolCall call, ToolResult result);
}
public interface SessionManager { void save(Session session); }

// io.oryxos.core.tool
public record ToolResult(boolean success, String content, String errorMessage, boolean retryable) {}
public interface ToolRegistry { Optional<OryxTool> find(String name); List<OryxTool> forProfile(Profile p); }
public interface OryxTool { String name(); String description(); String getInputSchema();
                            ToolResult execute(String inputJson); }   // execute 为本节新增
```

**约定常量**:`MAX_ITERATIONS=10`、`MAX_HISTORY_TURNS=20`;耗尽提示文本含"达到最大轮数"。
