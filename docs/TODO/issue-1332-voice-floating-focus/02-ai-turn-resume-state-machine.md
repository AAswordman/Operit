---
step: 2
scope: AI 回合后恢复收音的状态机
---

# AI 回合后恢复收音改成事件驱动

## 旧实现

`FloatingFullscreenModeViewModel.awaitAiTurnAndResumeVoiceCapture()` 用一个每 120ms 走一圈的循环等 AI 回合结束，并且要求先观测到 `busy == true` 才接受 `busy == false`：

```kotlin
delay(120)
var observedAiBusy = false
while (isActive && isWaveActive && shouldResumeVoiceCaptureAfterAiTurn) {
    val busy = isAiBusyOrSpeaking()
    if (busy) observedAiBusy = true
    if (observedAiBusy && !busy) { /* 清标志并恢复收音 */ }
    delay(120)
}
```

这个「必须先 busy」的前提是采样式状态机里最脆的一环。回合的开始与结束一旦落在同一个采样间隔内，`busy == true` 永远不会被观测到，循环就一直转，两个标志永久停在 `true`：

- `isVoiceCapturePausedForAi` 停在 `true`，语音态一直显示 AI 加载效果，麦克风不再恢复
- `shouldInterceptCenterAvatarClick()` 因为 `isVoiceCapturePausedForAi` 为真而持续拦截头像点击，用户点头像只会重启收音，退不出波浪模式
- 波浪模式下 `isBottomBarVisible` 为假，底部输入胶囊根本不参与组合，软键盘唯一入口消失

发送被拒（`isLoading` 已为真、消息为空）、快速失败、错误态立即收尾都会产生这种「短回合」。

## 意图修正

恢复收音的判据必须是回合信号本身，而不是对回合信号的定时采样。同时用户的主动手势不能被内部标志劫持。

## 新实现

把「等回合结束」抽成不依赖 Android 的纯挂起函数 `awaitAiTurnFinished(turnActive: Flow<Boolean>): Boolean`，语义保持「必须先看到 active 才承认结束」，返回是否观测到完整回合：

```kotlin
internal suspend fun awaitAiTurnFinished(turnActive: Flow<Boolean>): Boolean {
    var observedActive = false
    var finished = false
    turnActive
        .onEach { active ->
            if (active) observedActive = true
            else if (observedActive) finished = true
        }
        .takeWhile { active -> active || !observedActive }
        .collect { }
    return finished
}
```

「回合进行中」由两个响应式来源合成，不再有采样窗口：

- `snapshotFlow { isAiBusy() }`，追踪 Compose 快照状态（`inputProcessingState` 与消息流）
- `speechManager.voiceService.speakingStateFlow`，追踪 TTS 播报状态

`onCenterAvatarClick` 的拦截判据改为只认 `isAiBusyOrSpeaking()`。原先的 `isVoiceCapturePausedForAi` 是「等待中的意图」标志，一旦与真实状态脱节就会把用户永久锁在语音态。用户手势优先于内部标志。

## 结果

正常回合：先 false（回合未开始）→ true（忙碌）→ false（结束），走完即恢复收音。短回合：流式观察的窗口远小于 120ms 采样间隔，同样恢复。回合从未启动时保持等待，但用户随时可以点头像退出语音态，底部胶囊和软键盘入口不再被锁死。

`snapshotFlow` 与 `StateFlow` 在实现上都会合并极短时间内的连续翻转，测试用 `flowOf` 同步发出全部值，不能证明生产流不存在合并。这里的边界只影响「这一次是否自动恢复」，不再影响「用户能否退出语音态」：头像点击的拦截判据已经不再读 `isVoiceCapturePausedForAi`，极端情况下漏掉一次下降沿也只会让用户手动重进语音态。

[DONE]
