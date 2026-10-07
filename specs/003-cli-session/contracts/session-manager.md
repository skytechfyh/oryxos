# Contract: SessionManager(`io.oryxos.core.session`)

第17节契约只有 `save`,**该签名保持不变**;本节只新增两个方法(课件"对外三个方法")。

```java
public interface SessionManager {

  /** 按三元组获取或创建会话(幂等)。session_id 的拼接只在实现内部发生。 */
  Session getOrCreate(String channel, String user, String profileName);

  /** 按会话标识查找,不存在返回 empty。 */
  Optional<Session> get(String sessionId);

  /** 持久化会话(含累积的对话历史)。—— 第17节已有,签名不变 */
  void save(Session session);
}
```

## 行为约束

| 方法 | 约束 |
|---|---|
| `getOrCreate` | 同三元组返回同一 `id()`;任一项不同则 `id()` 不同;任一项空白 → `BizException(BAD_REQUEST)`;新建时 `status=active`、`messages_json=[]`、时间戳为当前时刻 |
| `get` | 不存在 → `Optional.empty()`;存在则历史完整还原 |
| `save` | 写回 `messages_json`、刷新 `last_active_at`;失败上抛不吞 |

## 调用方约定

所有入口(CLI 传 `"cli"`、Web 传 `"web"`、定时传 `"scheduler"`)只提供三元组,**不得**自行拼接 `session_id`。
