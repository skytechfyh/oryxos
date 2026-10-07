# Feature Specification: CLI 命令行入口与会话层(第18节)

**Feature Branch**: `003-lesson18-cli`

**Created**: 2026-10-07

**Status**: Draft

**Input**: 第18节需求:CLI 命令行入口与会话层——OryxOS 的终端入口,以及所有入口共用的会话持久化地基。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 同一身份始终落到同一个会话,且历史不丢 (Priority: P1)

任何入口(终端、Web、定时)只需报出"渠道 + 用户 + Agent 名"这三项身份信息,就能拿到属于这个身份的会话:第一次拿是新建,之后每次拿都是同一个。不同渠道、不同用户、不同 Agent 的会话彼此隔离、互不串话。会话里累积的整段对话历史会被保存下来,进程重启后再取,历史仍然完整。

**Why this priority**: 会话层是所有入口共用的地基。多轮对话靠"同一身份同一会话"串联;如果各入口对身份的拼接口径不一致,同一个人会出现两条互不相认的历史,事后最难查。所以这一条必须先钉死。

**Independent Test**: 不需要任何命令行,只用会话管理本身:对同一身份连续获取两次、换一项身份再获取一次、保存带历史的会话后"重启"再读,逐项断言。

**Acceptance Scenarios**:

1. **Given** 尚无任何会话, **When** 用同一身份(渠道 cli、用户 wang、Agent default)连续获取两次, **Then** 两次得到的会话标识相同,且库里只有一条记录。
2. **Given** 已存在 cli/wang/default 的会话, **When** 分别改变渠道、用户或 Agent 名中的任意一项再获取, **Then** 每次都得到与原会话不同的新会话。
3. **Given** 一个会话已累积多轮消息(用户消息、模型响应、工具结果)并已保存, **When** 重新读取该会话, **Then** 全部消息按原顺序完整还原。
4. **Given** 会话已保存, **When** 模拟进程重启(重新建立运行环境后再查), **Then** 该会话及其历史仍在。
5. **Given** 任一入口, **When** 它需要会话, **Then** 它只提供三项身份信息,会话标识由会话管理统一生成,入口代码中不存在自行拼接标识的逻辑。

---

### User Story 2 - 在终端里和 Agent 多轮对话 (Priority: P1)

开发者在终端启动交互对话,可用选项指定与哪个 Agent 对话,不指定则使用默认 Agent。每输入一行,Agent 处理后在屏幕上打印最终回复,然后等待下一行;输入 `/quit` 正常退出。下次以相同身份再进入,之前的对话历史还在,可以接着聊。

**Why this priority**: 这是 Provider 与 ReAct 之后第一个"看得见摸得着"的体验,是 Demo 一对话版的入口。

**Independent Test**: 用模拟的 Agent 引擎与脚本化输入:喂入若干行加 `/quit`,断言每行都交给引擎、每个回复都被打印、`/quit` 不交给引擎且循环结束。

**Acceptance Scenarios**:

1. **Given** 终端对话已启动, **When** 用户输入一行文本, **Then** 该文本连同当前会话交给 Agent 引擎处理,返回的最终回复被打印到屏幕,随后继续等待输入。
2. **Given** 对话进行中, **When** 用户输入 `/quit`(首尾空白忽略), **Then** 命令正常结束,`/quit` 本身不会交给引擎。
3. **Given** 未指定 Agent, **When** 启动对话, **Then** 使用名为 default 的 Agent;**Given** 用选项指定了 Agent 名, **Then** 使用所指定的 Agent。
4. **Given** 输入流结束(如管道输入读完), **When** 再无可读行, **Then** 命令正常结束而不是抛出未处理异常。
5. **Given** 引擎处理某一行时失败, **When** 错误发生, **Then** 向用户打印清晰的错误提示(不吞掉错误),且不会破坏已保存的历史。

---

### User Story 3 - 12 个子命令与"轻重分流" (Priority: P2)

整个程序只有一个命令行入口,下面注册 12 个子命令:`init`、`status`、`chat`、`serve`、`gateway`、`profile list/create/show/delete`、`provider list`、`tool list`、`session list`。每个子命令都能查看用法与帮助。命令按"要不要调模型 / 跑引擎"分两类:不需要的(如 `init`、`profile list`)直接读写文件、秒回,不启动完整运行环境;需要的(`chat`、`serve`、`gateway`)才启动完整运行环境。

**Why this priority**: 它让查看类命令保持秒级响应,同时给后续的 Web Service(第26节)、守护进程等运行模式留好统一入口。排在会话与对话之后,是因为它们的核心价值不依赖这层分流。

**Independent Test**: 子命令注册、帮助信息与启动分流属于进程级行为,留给人工验收;自动化只覆盖会话层与 chat 的读—转交—打印逻辑。

**Acceptance Scenarios**:

1. **Given** 已打包的程序, **When** 查看顶层帮助, **Then** 列出全部 12 个子命令。
2. **Given** 任一子命令, **When** 带上帮助选项, **Then** 显示该命令的用法说明。
3. **Given** `init` 或 `profile list`, **When** 执行, **Then** 不启动完整运行环境,秒级返回。
4. **Given** `chat`、`serve` 或 `gateway`, **When** 执行, **Then** 才启动完整运行环境;启动日志中发现的数据仓库接口数量大于 0,审计数据能正常写入。
5. **Given** 已存在若干会话, **When** 执行 `session list`, **Then** 列出这些会话(至少含标识、Agent 名、渠道、用户、状态)。
6. **Given** 三种运行模式(chat / serve / gateway), **When** 在它们之间切换, **Then** 共用同一份 Profile 配置与同一套会话存储,数据不丢。

---

### Edge Cases

- 并发或重复地用同一身份"获取或创建"时,不得产生重复会话(同一身份至多一条)。
- 身份三项中含有分隔符类字符(如用户名里带 `:` 或 `/`)时,不同身份不得因拼接而碰撞成同一标识。
- 身份三项为空或仅含空白时,明确拒绝并给出清晰报错,而不是创建出不可识别的会话。
- 按标识查找一个不存在的会话时,返回"未找到"的明确结果,不抛出难以理解的内部异常。
- 保存时对话历史为空,或某条消息内容为空、含特殊字符与换行时,回读后仍与保存前一致。
- 指定的 Agent 名不存在时,`chat` 给出清晰报错并退出,不进入无法工作的对话循环。
- 重命令启动时若存储模块未被扫描到(仓库接口数为 0),应当在启动阶段暴露问题,而不是等到第一次写审计才失败。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供统一的命令行入口程序,并注册 12 个子命令:`init`、`status`、`chat`、`serve`、`gateway`、`profile list`、`profile create`、`profile show`、`profile delete`、`provider list`、`tool list`、`session list`;每个子命令的用法与帮助信息可查看。
- **FR-002**: `chat` 命令 MUST 在终端循环执行"读一行输入 → 交给 Agent 引擎处理 → 打印最终回复",直到用户输入 `/quit`;MUST 支持通过选项指定 Agent,默认使用名为 default 的 Agent;命令本身 MUST NOT 做任何 Agent 逻辑判断,唯一自己判断的是退出指令。
- **FR-003**: 命令 MUST 按"是否需要调模型 / 跑引擎"分流:不需要的(如 `init`、`profile list`)直接读写文件、不启动完整运行环境;需要的(`chat`、`serve`、`gateway`)才启动完整运行环境。
- **FR-004**: 重命令启动完整运行环境时,系统 MUST 能扫描到存放会话与审计数据的存储模块,启动日志中发现的数据仓库接口数量 MUST 大于 0,审计数据 MUST 能正常写入。
- **FR-005**: 会话管理 MUST 对外提供三个能力:按"渠道 + 用户 + Agent 名"获取或创建会话;按会话标识查找会话;保存会话。
- **FR-006**: 会话标识 MUST 只在会话管理内部按"渠道 + 用户 + Agent 名"唯一生成;所有入口 MUST 只提供三项身份信息,MUST NOT 自行拼接标识。
- **FR-007**: 同一身份多次"获取或创建" MUST 返回同一个会话(幂等);渠道、用户、Agent 名任一不同 MUST 得到不同会话。
- **FR-008**: 会话 MUST 持久化:元数据(标识、Agent 名、渠道、用户、状态、创建时间、最后活跃时间、归档时间)与整段对话历史(整体序列化存于一处,不按条拆分)MUST 落库;历史回读后消息 MUST 完整、顺序一致;进程重启后历史 MUST 仍在。会话表结构 MUST 由手工维护的建表脚本创建,不依赖自动迁移。
- **FR-009**: `session list` 命令 MUST 能列出已有会话。
- **FR-010**: 会话状态 MUST 取值为"活跃"或"已归档"之一;新建会话为活跃;每次保存 MUST 刷新最后活跃时间。
- **FR-011**: 保存会话与记录审计 MUST 遵循既有约定:本节新增的任何失败路径 MUST NOT 被吞掉,必须上抛或落日志。

### Key Entities *(include if feature involves data)*

- **会话(Session)**: 一次持续对话的全部状态。关键属性:会话标识(由渠道 + 用户 + Agent 名唯一决定)、所属 Agent 名、接入渠道、用户标识、状态(活跃 / 已归档)、整段对话历史、创建时间、最后活跃时间、归档时间。
- **身份三元组**: 渠道 + 用户 + Agent 名,是各入口向会话管理表明"我是谁、要找哪个 Agent"的唯一方式。
- **子命令**: 命令行入口下的一个可执行动作,分"轻"(不启动完整运行环境)与"重"(需要)两类。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 对同一身份重复获取会话任意多次,得到的会话始终是同一个;对 N 个互不相同的身份,恰好得到 N 个互不相同的会话(隔离正确率 100%)。
- **SC-002**: 一个含多轮消息的会话保存后,无论是立即回读还是模拟重启后回读,消息条数、顺序与内容与保存前 100% 一致。
- **SC-003**: 开发者能在终端完成一次不少于 3 轮的连续对话并用 `/quit` 正常退出,全程无需手工处理会话标识。
- **SC-004**: 查看类轻命令(`init`、`profile list`)在一般开发机上 1 秒内返回;重命令才承担完整运行环境的启动耗时。
- **SC-005**: 12 个子命令 100% 可被调用,且各自的帮助信息可查看。
- **SC-006**: 重命令启动日志中发现的数据仓库接口数量大于 0,且一次对话产生的审计记录能被查到(审计写入成功率 100%)。
- **SC-007**: 课件验收 harness(`SessionManagerTest`、`SessionRepositoryTest`)全绿,且前序各节测试无回归。

## Assumptions

- 第16节(Profile / ProfileLoader / ProfileRegistry、ProviderService、`llm_calls`)与第17节(AgentService、ReActLoop、ToolExecutor、`tool_invocations`,以及 core 中已有的 Session / SessionManager 最小契约)均已交付;本节在其上**新增**能力,不改动其现有方法签名。
- 会话持久化沿用 SQLite + Spring Data JPA;对话历史整体序列化为 JSON 存于一列,核心阶段不做按条拆表。
- 会话归档的自动流转策略、会话清理不在本节范围:状态字段与归档时间字段只做存储,不做自动归档。
- `serve` 的 Web Service 细节留到第26节;本节 `serve` 只负责"以重命令方式启动完整运行环境"的入口骨架。`gateway` 同理,只做入口骨架,不实现 IM 通道。
- CLI 是消息进出的壳,不包含 Agent 智能;Agent 逻辑全部在已有的引擎中。参数解析交给成熟的命令行框架,不手写。
- 命令分流与 `--help` 属于进程级行为,不做自动化进程级测试,留人工验收;自动化只覆盖会话层与 chat 的读—转交—打印逻辑。
- 当前阶段的"当前用户"取操作系统登录用户名;多用户、SSO 属于后续阶段。
- 凭证一律走环境变量,不落明文。
