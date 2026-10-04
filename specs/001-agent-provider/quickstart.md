# Quickstart: 验证第 16 节

前置:JDK 21,联网(首次解析 Spring AI 1.1.2)。

```bash
./mvnw -pl oryxos-provider -am dependency:tree | grep -i "spring-ai"   # 依赖可解析
./mvnw -pl oryxos-core,oryxos-storage,oryxos-provider -am test          # 单测全绿(不碰网络)
./mvnw clean verify                                                     # 全部门禁
DEEPSEEK_API_KEY=xxx ./mvnw test -pl oryxos-provider -am -Dexcluded.test.groups= -Dgroups=integration -Dsurefire.failIfNoSpecifiedTests=false   # 手动冒烟,真 key
grep -rn "sk-" --include='*.java' --include='*.yaml' --include='*.sql' .      # 应无命中
```

预期:
- ProviderServiceTest 三个关键回归方法通过;
- LlmCallRepositoryTest 用 `schema.sql` 建表且 `success`/`error_message` 列存在;
- 冒烟后 `llm_calls` 多一条 `success=1`。
