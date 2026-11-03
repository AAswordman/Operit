---
step: 4
scope: 状态流转测试与验证范围
---

# 状态流转测试与验证范围

## 测试目标

前三个步骤里唯一能脱离 Android 运行时验证的是恢复收音的状态机。它原先写在 ViewModel 内部的轮询循环里，无法单独测；把判定抽成顶层挂起函数 `awaitAiTurnFinished(turnActive: Flow<Boolean>)` 之后，可以直接用 `kotlinx-coroutines-test` 驱动。

测试文件 `app/src/test/java/com/ai/assistance/operit/ui/floating/ui/fullscreen/viewmodel/AiTurnResumeTest.kt`，覆盖六个流转：

- 空闲 → 忙碌 → 空闲：恢复
- 开始观察时回合已经忙碌：落回空闲后恢复
- 开始与结束挤在一起的短回合：仍然恢复（回归用例，旧采样实现会永久闩死）
- 同一轮里出现后续 busy：只认第一个下降沿
- 回合尚未开始：判定为未完成，不恢复，避免把 TTS 录进麦克风
- 回合仍在播报：判定为未完成，不恢复

函数返回「是否观测到完整回合」，因此「应当恢复」与「应当继续等待」都能用普通断言表达。最初写成「在后台作用域启动协程 + `advanceUntilIdle()` + 查标志位」，实测后台作用域协程并未被驱动，断言等于空转，已改为直接调用。

实际运行结果：`:app:testDebugUnitTest` 共 160 个测试套件、1349 个用例，0 失败 0 错误，6 个原有 skip；其中新增的 `AiTurnResumeTest` 6 个用例全部通过。

## 无法在本机验证的部分

本机没有荣耀 Magic9 或任何 Android 17 设备，`adb devices` 为空，也没有可用模拟器。以下必须真机确认，PR 里不得写成已验证：

- 侧键唤起后连续多轮语音输入与 AI 回复
- TTS 播报结束后自动恢复收音
- 波浪态点击头像退出后，底部输入胶囊能唤起软键盘
- 关闭浮窗、切换到窗口模式、暂停监听后状态收敛
- 应用在后台时，外部 HTTP / 广播 / intent 工作流拉起浮窗仍然成功（服务创建只带 dataSync）

麦克风前台类型的行为依赖 OEM 对 while-in-use 的实现细节：类型改在语音会话期间追加，只有浮窗 overlay 可见、没有 resumed Activity 时能否追加成功，只能靠真机观测。以下同时需要确认：

## 构建验证

三个本地二进制前置依赖不在仓库里，按 `docs/doc-src/dev-core/BUILDING.md` 准备：`terminal` 子模块、`app/libs/ffmpeg-kit-local.aar`、`app/src/main/jniLibs/arm64-v8a/liboperit_ripgrep.so`。三者都已被 gitignore 覆盖，不进提交。

`syncSttModelAssets` 会在 `preBuild` 阶段联网拉取 STT 模型并按 sha256 校验。

[DONE]
