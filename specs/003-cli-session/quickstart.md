# Quickstart: 第18节验证指南

## 自动化(harness 判卷)

```bash
./mvnw -pl oryxos-storage,oryxos-channel-cli,oryxos-cli,oryxos-boot -am test   # 本节测试
./mvnw clean verify                                                             # 全量门禁(含前序节回归)
```

预期:`SessionManagerTest`、`SessionRepositoryTest`、`CliChannelTest`、`OryxOsApplicationTest` 全绿;Spotless / Checkstyle / PMD+P3C / SpotBugs 通过。

## 人工清单(harness 不覆盖)

```bash
./mvnw -DskipTests package
JAR=oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar
time java -jar $JAR profile list        # 轻命令:1 秒内返回,日志里没有 Spring 启动
java -jar $JAR --help                   # 列出 12 个子命令;逐个 `<cmd> --help` 正常
java -jar $JAR session list             # 无库时"暂无会话"
java -jar $JAR chat --profile <name>    # 重命令:日志含 "Found N JPA repository interfaces" 且 N>0
                                        # 多轮对话,/quit 退出;再次进入历史仍在
java -jar $JAR serve                    # 起服务;`status` 在另一终端可用
```

注意:`chat` 真跑依赖 `ToolRegistry` Bean(第20节交付),见 plan「待确认事项 B」。

## 关键场景 ↔ 验证方式

| 场景 | 验证 |
|---|---|
| 同身份同会话、不同身份隔离 | `SessionManagerTest` |
| 历史往返与重启不丢 | `SessionRepositoryTest` |
| chat 读—转交—打印、`/quit` | `CliChannelTest` |
| JPA 扫描覆盖 storage | `OryxOsApplicationTest` + 人工看启动日志 |
| 轻命令秒回、重命令才启动 Spring、12 命令 `--help` | 人工 |
