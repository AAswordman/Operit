---
fork: https://github.com/3316891527/Operit
branch: feat/phone-agent-local-execution
---
# 09 能力对齐与观察成本

按第一阶段和第三阶段推进。子代理职责和观察模式选择接口保持当前方案。

- 对齐中英文提示词和实际动作，只列 Launch、Tap、Type、Swipe、Back、Home、Wait、Take_over 和 finish。坐标动作要求当前截图，节点动作要求当前节点观察
- 复用会话内 XML parser factory，仍为每次读取创建独立 parser；删除 Sequence Wait 的重复预读取；动作前新鲜目标校验、动作后读回、接管检查和派发确认继续保留
- 分别记录全部节点读取、远程 XML 获取、解析、摘要、截图准备及按观察模式拆分的模型等待；总时长包含预热和清理
- 增加显式启用的同屏设备基准，交替采集节点和截图，包含首个样本、失败及完整样本分布，不调用模型、不发送输入
- 添加契约、读取次数与性能统计回归。默认设备 CI 不自动执行需要手动准备前台页面的基准

先静态检查和准备回归代码。本地不执行构建测试；真实设备和完整任务对照尚未完成时不宣布速度提升。


## 同屏观察基准

这是设备 instrumentation，不是本地 JVM 性能模拟。先安装匹配当前代码的应用和 androidTest APK，授予无障碍及所选截图通道所需权限，在主屏手动停留于目标 App 的稳定页面。执行期间不要点击或切页，不要让其他 PhoneAgent 任务并发运行。

```sh
adb shell am instrument -w \
  -e class com.ai.assistance.operit.core.tools.agent.PhoneObservationBenchmarkTest \
  -e phone_observation_benchmark true \
  -e phone_benchmark_rounds 12 \
  -e phone_benchmark_package com.example.app \
  -e phone_benchmark_task 搜索关键词 \
  com.ai.assistance.operit.test/androidx.test.runner.AndroidJUnitRunner
```

示例包名应替换为实际测试页的包名；不同构建变体的测试包名也应按已安装 APK 调整。报告为目标应用私有 files/phone-observation-benchmark.json，可通过可调试构建的 run-as 或日志标签 PhoneObservationBenchmark 取得。

节点样本包含完整远程层级读取、解析、摘要；截图样本使用生产 ActionHandler，包含隐藏浮层等待、实际截图、压缩与图片注册。采集交替执行，首对样本单列保留，热样本给出中位数和 P95，失败样本不作为快速成功读取。每张基准图片采集后按自身 ID 删除，不清空其他图片。报告没有页面文字、XML 或图片数据。

这只能回答“准备一次可用观察是否更快”。不能用它证明完整任务、模型判断或准确率更好。完整任务对照应固定设备、App、起始页、模型、网络和目标，分别记录任务完成率、总时长、模型调用数、节点读取数及观察模式切换次数。失败任务单独列出，不能只比较成功样本的平均耗时。当前不新增观察模式配置入口。

## 计时口径

node_read_ms 覆盖观察及动作前后所有驱动读树，hierarchy_ms 包含远程 XML 获取和必要绑定，parse_ms 包含解析与快照构造；summary_ms 是送往模型的节点摘要准备。screenshot_ms 包含生产截图准备；node_model_ms 和 image_model_ms 按本轮选定观察模式统计完整模型响应等待，模型失败也计时。total_ms 包含本次运行的预热、循环、暂停和正常清理，不应与这些互相重叠的分项相加。最终结果没有可用图片或节点的请求也可能进入对应模式统计，需结合错误日志解释。


## 本轮状态

[DONE] 已实现能力对齐、短提示词、Wait 去重读取、会话内 parser factory 复用及分项计时。已增加 4 项 JVM 回归代码和 1 项显式设备基准。源码动作集合与中英文指令表核对一致，git diff --check 通过。

待验证：编译、JVM 回归执行、真机同屏采集和完整任务对照。远程构建与测试结果在 PR #148 中跟踪；源码检查不等于测试通过，真实速度提升仍待设备测量。
