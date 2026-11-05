---
fork: https://github.com/yoruuuchan/Operit
branch: fix/memory-autosave-reliability
status: verification-done
---

# 验证记录

## 单元测试

目标测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.ai.assistance.operit.api.chat.library.MemoryAutoSavePollingLoopTest" `
  --tests "com.ai.assistance.operit.data.model.MemoryAutoSaveCandidateTest" `
  --tests "com.ai.assistance.operit.api.chat.library.MemoryAutoSaveRunSummaryTest" `
  --tests "com.ai.assistance.operit.api.chat.library.MemoryAnalysisProtocolTest" `
  -x :app:syncSttModelAssets -x :app:syncMainAssets
```

结果：`BUILD SUCCESSFUL`。

| 测试类 | 用例数 | 失败 |
|---|---:|---:|
| `MemoryAutoSavePollingLoopTest` | 4 | 0 |
| `MemoryAutoSaveCandidateTest` | 22 | 0 |
| `MemoryAutoSaveRunSummaryTest` | 4 | 0 |
| `MemoryAnalysisProtocolTest` | 5 | 0 |

覆盖点：

- 轮询 tick 抛异常后继续执行下一轮；
- `CancellationException` 被原样抛出，不会交给错误回调；
- 取消 Job 后不再执行 tick；
- 启动恢复抛异常不会阻止后续 tick；
- 失败候选 1、2 次仍可重试，第 3 次进入 `dead_letter`；
- 取消恢复 pending 不消耗 attempt；
- PROCESSING 候选在进程恢复后回到 pending，并累计中断次数；
- 连续中断达到上限进入 `dead_letter`；
- 死信不再进入可重试集合；
- 一轮处理结果中失败优先于成功、成功优先于无需保存；
- `{}` 解析为空分析，普通操作数据解析为非空分析。

## 本地构建环境说明

工作树是独立 worktree，缺少被 `.gitignore` 忽略的本地构建产物。为运行测试，从共享克隆复制了：

- `app/src/main/jniLibs/arm64-v8a/liboperit_ripgrep.so`
- `app/libs/ffmpeg-kit-local.aar`
- `terminal` 子模块的独立 checkout（对应 gitlink `0458689`）

这些文件均被 `.gitignore` 忽略，不会进入提交。STT 模型资源下载因校验失败，测试时用 `-x :app:syncSttModelAssets -x :app:syncMainAssets` 跳过；该任务与本次改动无关。

## 本地化检查

```powershell
python ci/script/check_localizations.py --base f1bbcd80b927bdbda50d07483322a805b1bfe432 --candidate <working-tree>
```

结果：`Localization: errors=0 warnings=0`。新增的 7 个 key 在 zh 和已有的 7 个完整 locale 中均存在，占位符一致；`values-ja` 原本就缺少大量自动保存相关条目，本次沿用该现状，新增条目为 missing 而非格式错误。

## 未执行项

- 未在真机上实际杀进程验证恢复；恢复逻辑通过候选状态单测覆盖，调度器在启动和每轮扫描前调用同一恢复方法。
- 未下载/校验 STT 模型资源，未执行完整 APK 打包。
