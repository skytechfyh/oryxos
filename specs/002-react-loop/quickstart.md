# Quickstart: 验证第17节

## 自动化验证(harness)

```bash
./mvnw -pl oryxos-core,oryxos-storage -am test                 # 五个 ReAct 测试类 + Repository 测试
./mvnw -pl oryxos-core -am test -Dtest=ReActLoopTest#模型一直要调工具_转满最大轮数强制停
./mvnw -pl oryxos-core -am test -Dtest=AgentServiceTest#处理中抛异常_ProfileContext也必须被清掉
./mvnw clean verify                                            # 完成的定义:全绿(含 Spotless/Checkstyle/PMD/SpotBugs)
```

预期:全部通过;`tool_invocations` 成功/失败各一条。

## 人工验证(harness 不覆盖)

1. 第18/20 节就绪后:用真模型跑 Demo 一对话版——问天气,Agent 调 `http_get`、拿到数据、给出穿搭建议。
2. Code review 确认循环自实现,未使用 `ChatClient`/框架 Agent 封装。

详见 [contracts](contracts/core-interfaces.md)、[data-model](data-model.md)。

## 集成冒烟(真模型,手动跑)

`ReActSmokeIT`(provider 模块,`@Tag("integration")`,CI 默认跳过)用真 DeepSeek + 离线假天气工具跑一次多轮 ReAct,核对 `tool_invocations` 与 `llm_calls` 都有记录。key 只从环境变量读取,文件里无明文:

```bash
export DEEPSEEK_API_KEY=...        # 勿用 -D 传,系统属性会进 surefire 报告
./mvnw -pl oryxos-provider -am test -Dtest=ReActSmokeIT -Dgroups=integration -Dexcluded.test.groups= -Dsurefire.failIfNoSpecifiedTests=false
```

未设置 `DEEPSEEK_API_KEY` 时该测试自动跳过。
