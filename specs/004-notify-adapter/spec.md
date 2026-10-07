# Feature Specification: Notify 出站通知能力(第19节)

**Feature Branch**: `004-lesson19-notify`

**Created**: 2026-10-07

**Status**: Draft

**Input**: 第19节需求:Notify 模块——Agent 主动把结果送出去的出站通知能力(与入站 Channel 对称)。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 把一条内容推送到配置好的 webhook (Priority: P1)

业务方为 Agent 配置了一个团队群的 webhook 地址。Agent 产出结果后,调用统一的出站通知能力,把一段文本以 JSON 形式 POST 到该地址,文本放在 `content` 字段里。群里随即能看到这条消息。

**Why this priority**: 这是 Notify 存在的全部理由——定时、日报类场景到点自动触发,没有人在等响应,结果必须主动送出去。没有这一条,后续 25 节定时模块和 31 节 Demo 没有出口。

**Independent Test**: 在本地起一个假 webhook 服务,构造通知目标(类型为 webhook,配置里带该服务地址),调用发送;断言假服务收到一次 POST,请求体 JSON 含 `content` 且值与发送内容一致。

**Acceptance Scenarios**:

1. **Given** 一个指向本地假 webhook 的通知目标, **When** 发送内容"hello", **Then** 假服务恰好收到一次 POST,请求体 JSON 的 `content` 字段为"hello"。
2. **Given** 请求体, **When** 检查请求头, **Then** 内容类型为 JSON。

---

### User Story 2 - 推送地址只来自目标配置 (Priority: P1)

推送地址是运行时配置,不是代码里的常量。同一个出站通知实现,面对两个配置了不同地址的通知目标,必须各自发到各自的地址。

**Why this priority**: 地址写死会让"每个 Agent 推到各自的群"这一核心用法不成立,也会把凭证类信息固化进代码。

**Independent Test**: 起两个本地假服务(不同端口),分别用指向它们的两个目标各发一次,断言各自只收到属于自己的那一次。

**Acceptance Scenarios**:

1. **Given** 两个地址不同的通知目标, **When** 分别发送, **Then** 每个假服务只收到发往自己地址的请求。
2. **Given** 代码库, **When** 搜索, **Then** 不存在硬编码的推送地址。

---

### User Story 3 - 目标故障时调用方明确得知失败 (Priority: P1)

webhook 服务端返回 5xx(或其他失败状态)时,发送必须以异常形式向上抛出,调用方据此知道"没送达";不得静默吞掉,更不得返回看似成功的结果。

**Why this priority**: 静默失败会让 Agent 以为已经通知到人,而实际没有——对"没人在等响应"的定时场景尤其危险。

**Independent Test**: 假服务对请求返回 500,断言发送调用抛出异常。

**Acceptance Scenarios**:

1. **Given** 假 webhook 返回 5xx, **When** 发送, **Then** 抛出异常,不静默返回。
2. **Given** 地址不可达(连接失败), **When** 发送, **Then** 同样抛出异常。

---

### User Story 4 - 接口中立,可扩展新渠道 (Priority: P2)

出站通知接口只表达"把一条内容送到某个通知目标"这一意图,签名里不出现任何某一渠道特有的概念。将来接入企业微信官方 SDK 等专用渠道,只新增一个实现,不改接口、不改调用方。

**Why this priority**: 这是"接口先行"的设计价值;核心阶段只有一档实现,中立性无法靠运行行为测出,属于设计自查项。

**Independent Test**: 人工自查——假设换成专用渠道实现,发送方法的签名是否需要改动(应不需要)。

**Acceptance Scenarios**:

1. **Given** 出站通知接口, **When** 审视其签名与通知目标结构, **Then** 其中只有"渠道类型 + 一份渠道配置 + 内容",无任何某一渠道特有的词。

---

### Edge Cases

- 通知目标的配置里缺少地址时,发送必须明确报错,不得向空地址或默认地址发请求。
- 内容含引号、换行、中文、emoji 等特殊字符时,服务端收到的 `content` 与发送前完全一致(JSON 转义正确)。
- 内容为空字符串时,行为须确定且不静默出错(按原样发送或明确拒绝,二者择一并在 plan 中固定)。
- 目标响应慢或挂起时不得无限阻塞(超时策略在 plan 中固定;本节不做重试)。
- 通知目标的配置映射为空或为 null 时,明确报错而非空指针式崩溃。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供与具体渠道无关的出站通知接口,表达"把一段内容送到某个通知目标"的意图;接口签名中 MUST NOT 出现任何某一渠道特有的概念。
- **FR-002**: 通知目标 MUST 由"渠道类型"和"一份渠道配置(键值对)"组成;配置的具体含义 MUST 由各实现自行解释,接口不解释。
- **FR-003**: 核心阶段 MUST 提供一档通用 webhook 实现:向目标配置中的地址发送 POST 请求,请求体为 JSON,携带 `content` 字段。
- **FR-004**: 推送地址 MUST 只来自通知目标的配置,MUST NOT 硬编码。
- **FR-005**: 目标返回 5xx 等失败状态、或连接失败时,发送 MUST 以异常向上抛出,MUST NOT 被静默吞掉。
- **FR-006**: 目标配置缺少地址或配置为空时,发送 MUST 明确报错,MUST NOT 发出请求。
- **FR-007**: 本节 MUST NOT 引入重试、签名、鉴权,也 MUST NOT 对接任何某一家 IM 的专用 API。
- **FR-008**: 本节 MUST NOT 创建 notify 内置 Tool(`NotifyTools`)及其测试、MUST NOT 修改 `Profile.notifyChannels` 的类型与 `ProfileContext`、MUST NOT 创建任何 Sandbox 相关类型;这些随 24 节之后的 `NotifyTools` 一并完成。

### Key Entities *(include if feature involves data)*

- **出站通知接口**: 把内容送到某个通知目标的统一抽象,与入站 Channel 对称。
- **通知目标**: 一次推送的去向,由渠道类型与渠道配置两部分构成,配置如何解释由实现决定。
- **webhook 实现**: 核心阶段唯一的实现,把内容以 JSON POST 到配置中的地址。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 对一个有效的 webhook 目标发送一条内容,目标恰好收到 1 次请求,且 `content` 与发送内容 100% 一致(含特殊字符)。
- **SC-002**: 对地址不同的多个目标,每个目标只收到发往自己的请求(串发率 0%)。
- **SC-003**: 目标返回 5xx 或不可达时,调用方 100% 能得到异常(静默失败率 0%)。
- **SC-004**: 课件验收 harness 第一批测试(`WebhookNotifyAdapterTest`)全绿,且前序各节测试无回归;全部测试仅依赖本地假服务,不依赖外网。
- **SC-005**: 人工项通过:对接真实 webhook 群里确实收到消息;接口中立性自查结论为"换成专用渠道实现无需改发送签名"。

## Assumptions

- 第16~18节已交付;本节只在 `oryxos-tool` 模块新增 notify 相关代码,不改动前序节公共接口(Profile、ProfileContext 等)。
- `NotifyTools`、其测试、Profile `notifyChannels` 结构化、Sandbox 接线均推迟到 24 节之后(课件"实现顺序说明"与评审结论一致),本节交付范围仅为接口、通知目标、webhook 实现与 `WebhookNotifyAdapterTest`。
- 域名白名单校验由后续 Sandbox 节在 `NotifyTools` 调用链上接入;本节 webhook 实现自身不做白名单检查,留调用位说明即可。
- webhook 实现所需的 HTTP 客户端由 Spring 容器提供一个 Bean;核心阶段使用同步阻塞调用。
- 测试使用本地假 HTTP 服务,不依赖外网;真实 webhook 验证属于人工项。
- 凭证类地址一律走环境变量占位,不落明文。
