---
issue: https://github.com/AAswordman/Operit/issues/1332
fork: https://github.com/yoruuuchan/Operit
branch: fix/issue-1332-voice-floating-focus
base: upstream/dev
scope: 语音浮窗多轮收音与软键盘唤起
---

# 语音浮窗第二轮收音与软键盘失效

## 原本状况

侧键唤起语音助手后，浮窗进入全屏语音态。第一轮语音输入正常识别并得到 AI 回复，之后继续说话应用完全没有反应，点击键盘图标也弹不出软键盘。

Issue 作者把原因定位在 `SpeechInteractionManager.startListening()` 开头的 `!hasFocus` 短路，认为首轮结束后焦点被 TTS 播报或 AI 回复夺走。

这个定位不成立。`hasFocus` 全仓库只有三处写入：

- `SpeechInteractionManager.requestFocus()` 置 `true`
- `SpeechInteractionManager.releaseFocus()` 置 `false`
- `releaseFocus()` 只被 `FloatingFullscreenModeViewModel.cleanup()` 调用

`resetState()` 不碰它。所以 `hasFocus` 不可能在一轮识别结束后自己变成 `false`，`!hasFocus` 短路在语音态里根本不可达。更关键的是，软键盘那条路径 `BottomControlBar` 的 `focusRequester.requestFocus()` 加 `keyboardController?.show()` 完全不读 `hasFocus`，这个短路解释不了「键盘弹不出来」。

两个症状需要分别找成因，不能共用一个错误结论。

## 意图

让浮窗语音会话在整个生命周期内保持合法、连续的收音资格，并让 AI 回合结束后的恢复收音成为确定性状态流转。同时清理 `SpeechInteractionManager` 里对焦点的错误建模。

不写任何兜底、降级或重试阶梯。找到真实成因后直接修正状态机。

## 期待结果

- 浮窗可连续完成多轮语音输入与 AI 回复
- TTS 播报结束后继续收音
- 点击键盘按钮可以正常输入
- 用户主动关闭浮窗、切换输入方式或暂停监听时状态正确收敛

## 作用域

改动集中在这几处：

- `ui/floating/voice/SpeechInteractionManager.kt` 焦点建模与收音启动
- `ui/floating/ui/fullscreen/viewmodel/FloatingFullscreenModeViewModel.kt` AI 回合恢复状态机
- `api/chat/AIForegroundService.kt` 麦克风前台资格的获取与释放
- `services/FloatingChatService.kt` 浮窗服务的前台服务类型

不改动 STT 与 TTS provider 的协议实现，不改动唤醒词匹配逻辑。

## 步骤

1. `01-microphone-foreground-ownership.md` 麦克风前台资格的归属与生命周期
2. `02-ai-turn-resume-state-machine.md` AI 回合后恢复收音的状态机
3. `03-focus-model-cleanup.md` 焦点建模清理与软键盘入口
4. `04-state-transition-tests.md` 状态流转测试与验证范围

## PR

向 `AAswordman/Operit:dev` 提交，关联 `Fixes #1332`。

本机没有荣耀 Magic9 或 Android 17 真机，也没有可用模拟器，多轮真机验证无法执行。PR 里如实标注实际验证范围，不把未验证项写成已验证。
