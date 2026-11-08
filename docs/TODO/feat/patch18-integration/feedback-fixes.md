---
fork: https://github.com/3316891527/Operit
status: 源码修正完成，仓库工作流与设备复测结果待回填
---
# 补丁 18 首轮体验反馈修正

用户反馈未授权存储显示 0 B、未安装环境仍出现 Linux 入口、后台终端画面丢失、在用插件删除仍有按压动效且未弹警告，以及工具流式文本暴露计时 meta、耗时持续为 0。修正继续进入 PR #87，目标为 dev。

## 文件属性和 Linux 入口

LocalWorkspacePropertySource 在 stat 和遍历前通过 WorkspacePropertyAccess 检查共享存储授权及实际可读性。应用私有目录不要求共享存储授权，SAF 和远端继续使用自己的来源权限。无权限时不启动递归；子目录读取失败或子项大小未知时总大小为未知，真实空项仍为 0 B。

FileBrowser 依据 OfflineResources.ubuntuInstalled 的安装完成标记建立 Linux 快捷入口，恢复页面时重新检查。不把下载归档视为已经安装，也不为显示入口启动终端。无可用本地环境时不请求 Linux 初始路径。

## 终端后台和光标

CanvasTerminalView 临时脱离窗口只停渲染和清理按键按住状态，保留模拟器、会话和输入回调。完整释放由 AndroidView.onRelease 执行。AnsiTerminalEmulator 按逻辑行及字符位置重排当前与已保存光标，并保留光标所在空行和待换行状态，避免尺寸变化后回到行首。

## 插件删除

ResourceHubSheets 中的删除 TextButton 在资源使用中真正禁用。外层无涟漪点击只调用 OKToast.warning(...).show()，不弹删除确认；忙碌时连该说明入口也禁用。资源详情注册和注销当前面板窗口，警告可使用底部面板或宽屏 Dialog 的窗口层级。

## 工具计时

参考 [Operit2](https://cnb.cool/luojiaping/Operit2) 的 4b3a2299fc04a8f33f6000ea749b1bb1cb9a2fed，位置为 apps/flutter/app/lib/ui/features/chat/components/part/ThinkToolsXmlNodeGrouper.dart 的分组状态。该实现使用 UI Stopwatch、每秒刷新和内存完成缓存，不向正文注入计时标签。本次采用正文分离的原则，并保留本项目历史持久化要求。

统一执行器发布 ToolCallExecution 类型化事件；一次回答持有独立 ToolTimingState，跨批次按原始调用位置关联。标题订阅独立状态并按单调时钟每秒刷新，不等待正文追加。完成、取消和拒绝分别保存其真实终态，结果顺序仍由 OrderedToolResults 控制。

完成记录保存到已有 sections JSON 内 tool_call.execution，不增加数据库表或列。正文渲染、复制、搜索及模型请求继续使用原工具标记，不序列化 execution。定稿、取消和单条重新生成固定快照并移除运行态引用，切换回答版本只使用该版本保存的数据。旧候选的本地计时 Protocol 在存储读取边界清理，其它供应商 Protocol 保留。

旧方案的标签由应用生成，标准模型历史准备原本会移除它们；同轮工具结果由原结果队列构造。因此界面显示 meta 不能直接说明真实 API 计费 Token 增加。新方案从生成源头不再向文本流写计时；没有进行真实请求或供应商账单的前后对照。

## 验证与复测

计时回归源码共 16 项，覆盖无正文时推进、同名并行归属、执行区间并集、取消和拒绝、跨批次位置，以及正文隔离、数据库、回答版本和 JSON 备份。终端新增 5 项尺寸及光标回归源码。源码尚未本地执行，由仓库 JVM 工作流验证。

[完整操作与预期](regression.md) 保留原 132 项并新增 18 项，共 150 项；[结果表](results.md) 单独填写，旧候选失败已备注，新候选设备结果待复测。构建与 JVM 测试派发后记录受理回执，不把受理写作通过。

[DONE] 源码和回归文档已完成；编译、JVM 与设备结果待实际回执。
