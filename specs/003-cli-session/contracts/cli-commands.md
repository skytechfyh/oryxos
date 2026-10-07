# Contract: 命令行(`oryxos`)

根命令 `oryxos`,Picocli 实现,12 个子命令。**轻**=不启动 Spring;**重**=启动 Spring。

| 命令 | 类型 | 行为 | 退出码 |
|---|---|---|---|
| `init` | 轻 | 创建 `.oryxos/` 目录结构(§8.1)并写默认模板与默认 Profile;已存在则不覆盖 | 0 |
| `status` | 轻 | 打印工作区是否存在、Profile 数量、数据库文件是否存在 | 0 |
| `chat [--profile <name>]` | 重(`web-application-type=none`) | 交互循环;`--profile` 默认 `default`;`/quit` 退出;Profile 不存在时报错退出 | 正常 0;Profile 不存在 1 |
| `serve [--spring.* ...]` | 重 | 启动 Web Service 骨架并阻塞;`--spring.*` 参数透传 | 0 |
| `gateway` | 重 | 启动并阻塞的守护骨架(不含 IM 通道) | 0 |
| `profile list` | 轻 | 列出 `.oryxos/profiles/` 下的 YAML 文件名 | 0 |
| `profile create <name>` | 轻 | 写最小 Profile 模板;已存在则报错 | 成功 0 / 已存在 1 |
| `profile show <name>` | 轻 | 打印该 Profile YAML 原文;不存在则报错 | 成功 0 / 不存在 1 |
| `profile delete <name>` | 轻 | 删除该文件;不存在则报错 | 成功 0 / 不存在 1 |
| `provider list` | 轻 | 打印 `oryxos.providers` 的 `name` 与 `base-url`(不打印 key) | 0 |
| `tool list` | 轻 | 打印"尚无已注册工具(完整注册表由第20节交付)"占位 | 0 |
| `session list` | 轻 | JDBC 只读列出会话:`session_id`、`profile_name`、`channel`、`user_id`、`status`、`last_active_at`;无库/无表时打印"暂无会话" | 0 |

## 通用约定

- 每个命令均自带 `--help`(Picocli `mixinStandardHelpOptions`)。
- 顶层无子命令时打印帮助。
- `profile` 的 `<name>` 仅允许 `[A-Za-z0-9_-]+`,否则报错(防路径穿越);写文件处留 24 节 `SandboxChecker` 接线位注释。
- 用户可见输出走注入的 `PrintStream`,日志走 SLF4J。
- 错误以清晰中文提示输出到 stderr,不吞异常。
- `chat` 的 `/quit` 判断是 CLI 唯一的自有逻辑;输入流结束(EOF)视同退出。
