# Data Model: Notify 出站通知能力

本节无持久化实体、无新表。仅两个内存类型:

## NotifyTarget(public record)
| 字段 | 类型 | 说明 |
|---|---|---|
| `channelType` | `String` | 渠道类型,如 `webhook`;接口层不解释,由实现按需判断 |
| `config` | `Map<String, String>` | 渠道配置;webhook 实现读取键 `url` |

**校验**:record 本身不加校验(字面量与课件保持一致),仅在紧凑构造器里对非 null 的 `config` 做 `Map.copyOf` 防御性拷贝(SpotBugs EI_EXPOSE_REP 要求,且使目标不可变);校验在 `WebhookNotifyAdapter.send` 内:`target`/`config` 为 null、`url` 缺失或空白 → `IllegalArgumentException`。

## 出站接口
`NotifyChannelAdapter#send(NotifyTarget target, String content)`:无返回值;成功即正常返回,失败抛异常(不返回布尔)。

无状态转换。
