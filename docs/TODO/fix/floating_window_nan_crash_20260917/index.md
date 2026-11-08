---
fork: https://github.com/AAswordman/Operit
date: 2026-09-17
source: 用户邮件反馈（悬浮窗崩溃 + 重roll切换卡住）
---

# 悬浮窗 NaN 崩溃修复

## 原本状况

用户反馈（1.12.1+3/+4 起出现，+7 仍在）：

- 悬浮窗一旦启动，应用立即崩溃并无限重启，主界面完全无法进入；只有关闭
  "在其他应用上层显示" 权限后才能使用应用。
- 崩溃栈：`IllegalArgumentException: Cannot round NaN value`，位于
  `FloatingChatWindowScreen.kt:194`（`FloatingChatWindowContent` 的 measure 块，
  `viewModel.windowState.width/height.toPx().roundToInt()`）。

## 已确认的代码问题与待确认原因

1. 数据链路：窗口尺寸/缩放以 float 形式持久化在 `floating_chat_prefs`
   （`FloatingWindowState.restoreState`）。目前没有设备偏好文件或复现证据证明其中包含 NaN/Infinity。
   Kotlin 的 `Float.coerceIn` 对 NaN 的两次边界比较恒为 false，NaN 原样穿透，
   进入 Compose measure 的 `roundToInt` 后抛出异常。
2. 放大机制：`FloatingChatService` 与主界面同进程（manifest 无 android:process），
   未捕获异常杀死整个进程 → 主界面也无法使用。`handleServiceCrash` 的
   "连崩 4 次自动停用" 保护使用内存计数器，每次崩溃进程即死、计数器归零，
   阈值永远达不到 → 无限崩溃重启循环。用户只能手动关闭悬浮窗权限自救。
3. 若状态已包含 NaN，原来的 `saveState` 会原样回写，清理缓存也不会清除
   shared_prefs；这是一条可能的持续传播路径，尚不能据此确定首次 NaN 的来源。

## 本次修改（作用域）

1. `services/floating/FloatingWindowState.kt`
   - `restoreState()` 读取 `window_width` / `window_height` / `window_scale` /
     `last_window_scale` 时校验 `isFinite()`，非法值明确报错，由服务记录并终止启动。
     `saveState()` 同样拒绝写入非有限值。不会自动改写已存在的非法配置。
   - 读写共用不小于最小窗口尺寸的上限，避免小屏下 `coerceIn` 区间倒置。[DONE]
2. `services/FloatingChatService.kt`
   - 崩溃计数改为持久化（`crash_count` + `last_crash_time`），使既有的
     连续崩溃间隔不超过 60 秒、累计 4 次则停用悬浮窗服务的机制跨进程重启生效。
     计数、时间和停用标记在调用默认异常处理器前一次同步提交，写入失败记录日志。
   - 停用或初始化失败后，启动回调直接停止服务并返回 `START_NOT_STICKY`，
     避免访问未初始化的对象；销毁时按初始化状态清理并解除异常处理器。[DONE]
3. `ui/features/chat/viewmodel/FloatingWindowDelegate.kt`
   - 用户在主界面显式进入悬浮窗（`toggleFloatingMode` / `launchInMode`）时
     清除停用标记、计数和时间戳，作为该保护的手动恢复入口：显式操作即重新授权，
     若同一崩溃仍在，服务会再次连崩 4 次后重新停用。[DONE]
   - 记录主动绑定的结果，窗口显示失败或收到空 Binder 时解除绑定并清除悬浮窗模式状态，
     使初始化失败的服务能够完成停止，用户后续操作能够重新发起启动。[DONE]

## 未处理 / 后续

- NaN 的首次生成原因仍需设备偏好数据或复现日志确认。本次不能声称已恢复
  所有受影响设备的悬浮窗；已存在的非法尺寸会使本次悬浮窗启动明确失败。
- 重 roll 后切换 AI 回答卡住（切页面再回来才恢复，且 2/3 个回复显示相同）：
  已排查 `selectMessageVariant → reloadCurrentChatDisplayHistory →
  CurrentChatWindowController` 链路与 `regenerateAiMessageVariant` 的变体编号，
  未在静态代码中定位到必然成因。两个疑点供后续跟进：
  - `ChatHistoryManager.addMessageVariant` 用 `variants.size + 1` 计算下一个
    variantIndex 并以 REPLACE 写入；若变体曾被删除，新 index 可能覆盖既有变体行，
    造成"两个变体内容相同"。
  - 显示窗口分页（`reloadCurrentChatDisplayHistory` 按 displayEndTimestamp 锚定）
    在变体切换后是否重取到了新内容，需要用户侧日志确认。
  需要用户提供该会话的导出数据或更多日志后再定位，不宜盲改。
