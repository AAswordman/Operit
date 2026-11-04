---
fork: https://github.com/luojiaping/Operit
status: unit-tested
---

# 重试事件收尾与 Waifu 分句回滚

[返回 TODO 说明](../README.md)

## 原状

Waifu 分句只消费原始文本，已显示气泡和打字队列不随 ROLLBACK 修正。最终总文本虽经过修订，旧分句仍可能保留。

EnhancedAIService 在 provider 文本结束时取消事件转发协程，队列中的尾部事件可能尚未到达外层 eventChannel；本地 tracker 的重放无法补齐外层响应所需的事件。

## 修复范围

1. 提取并复用事件转发收尾逻辑。文本结束或异常时停止常驻订阅，按顺序转发尚未处理的历史事件，再完成本轮；保持轮次偏移、异常与取消语义，不等待事件通道关闭。
2. Waifu 使用修订后的全文生成分句快照。打字队列以最新快照为目标，撤回失效尾段并清除旧待发内容，保留未受回滚影响的前缀。
3. 消息 UI 与持久化消费分句追加/保留前缀事件。内部删除不经过会取消当前回合的用户历史修改入口。
4. 保持现有 HTTP/SSE 和工具消息字段。已发出的 append-only delta 无法撤回，最终完整响应仍是权威结果。

## 单测

- 快速生产者与尚未排空的最后一个 ROLLBACK
- 多轮偏移、异常结束和取消时的订阅清理
- 已显示旧句后的回滚、旧打字队列清除、正确前缀保留
- 回滚早于/晚于相关文本 chunk、连续重试与最终事件重放
- 普通分句顺序与打字延迟保持，持续追加不会反复重置同一句的计时
- 消息记录同步撤回、取消不继续发队列中的旧内容

新增 25 个用例直接使用生产逻辑；分句解析通过可注入函数隔离 Android JNI，保护块测试输入原生解析器的事件帧。重试状态机、队列、消息同步与保护块收尾均不以 native 库缺失为由跳过。

## 约束与结果

本次保持浏览器修复、运行依赖和外部接口字段不变。测试记录包含新增用例与相关既有回归，按 JUnit 报告分别记录执行和跳过数量。

### 验证命令

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ai.assistance.operit.util.WaifuStreamUpdatesTest' \
  --tests 'com.ai.assistance.operit.util.WaifuRenderableSnapshotTest' \
  --tests 'com.ai.assistance.operit.util.stream.RevisionEventRelayTest' \
  --tests 'com.ai.assistance.operit.util.stream.RevisionAppliedTextTest' \
  --tests 'com.ai.assistance.operit.util.stream.TextStreamRevisionTrackerTest' \
  --tests 'com.ai.assistance.operit.api.chat.llmprovider.ProviderRetryRollbackTest' \
  --tests 'com.ai.assistance.operit.util.WaifuMessageProcessorTest' \
  --tests 'com.ai.assistance.operit.services.core.MessageProcessingDelegateTest' \
  --tests 'com.ai.assistance.operit.integrations.http.ExternalChatRequestBodyReaderTest' \
  --tests 'com.ai.assistance.operit.integrations.http.ExternalChatHttpIngressTest' \
  --no-daemon --console=plain
```

本机验证使用 Windows 原生终端调用 `gradlew.bat`。Git Bash 启动的 JDK Selector 管道无法连接；标准 NDK 27 目录仅存在未完成安装记录，因此通过本地临时 init 脚本将 app 与 Android library 模块的测试配置统一设为已安装的 NDK 28.2.13676358。临时文件位于 `.gradle/retry-unit/`，未修改仓库的 Gradle 配置。

### 验证结果（2026-11-04）

主应用与全部单测源码编译通过。相同筛选范围复跑得到 `BUILD SUCCESSFUL`（3m 33s）；最终 JUnit XML 共记录 78 条用例，69 条通过、9 条跳过、0 条失败、0 条错误。

| 测试类 | 通过 | 跳过 |
| --- | ---: | ---: |
| WaifuStreamUpdatesTest（新增） | 13 | 0 |
| WaifuRenderableSnapshotTest（新增） | 5 | 0 |
| RevisionEventRelayTest（新增） | 7 | 0 |
| WaifuMessageProcessorTest | 16 | 9 |
| RevisionAppliedTextTest | 6 | 0 |
| TextStreamRevisionTrackerTest | 7 | 0 |
| ProviderRetryRollbackTest | 2 | 0 |
| MessageProcessingDelegateTest | 4 | 0 |
| ExternalChatRequestBodyReaderTest | 6 | 0 |
| ExternalChatHttpIngressTest | 3 | 0 |
| 合计 | 69 | 9 |

新增 25 条用例全部执行并通过。9 条跳过项均为既有 Waifu JNI 集成用例：宿主 JVM 缺少 `streamnative`，由现有 `requireNativeStreamSplitter()` 触发 JUnit assumption，没有计入通过数量。完整 Android JNI 分句渲染没有在本次 JVM 运行中执行；保护块事件处理、分句快照修订、打字队列和消息身份同步由新增用例独立验证。

首轮执行有 4 条异常身份断言失败：协程堆栈恢复复制了异常，`assertSame` 无法表达跨挂起点的异常传播。已改为同时检查异常类型、消息以及因果链中的原始异常，保留对异常未被吞掉或替换的验证，之后复跑全部筛选用例通过。

测试自动生成的 ObjectBox 模型格式与备份改动已清理；数据库模型保持原样。

上述结果来自提交整理前的本地集成版本。GitHub 导出时已逐文件核对浏览器与重试修复的实现和相关回归用例一致，整理后未重复执行构建或测试。

[DONE]
