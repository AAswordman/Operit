---
fork: https://github.com/AAswordman/Operit
---

# 自动朗读串行化播放

## 原状

自动朗读路径上，MessageProcessingDelegate 的流式收集器每凑满一句（TtsSegmenter 按句末标点或 50 字切分）就调用一次 ChatViewModel.speakMessage，而 speakMessage 每次都通过 viewModelScope.launch 起一个全新的播放协程，speechPlaybackJob 引用被反复覆盖、旧协程不取消继续运行。一次流式回复会产生几十个并发播放协程。

协程体内对 SimpleVoiceProvider（系统 TTS）的 speak 调用经 withContext(Dispatchers.IO) 分发后由线程池并发执行：锁外的 ensureVoiceAndLocaleReady（遍历 voices、setLanguage，耗时较长）与 synchronized(queueLock) 内的提交顺序取决于线程调度，句子进入系统 TTS 引擎队列的顺序与 flush 顺序不一致，造成自动朗读跳句、乱序、部分句子被插队或丢失。手动朗读是单协程顺序提交，不受影响。

队列型 Provider（QueuedTtsPlayback：HttpVoiceProvider、Doubao；SiliconFlowVoiceProvider 同构队列）的 speak 在播放完成后才返回，依赖并发协程实现"当前句播放时下一句预合成"的流水线。

## 意图

在 ChatViewModel 层将所有朗读请求收敛为单一串行消费者：请求先进 Channel，由常驻协程按入队顺序逐条处理，保证 provider.speak 的调用顺序与 flush 顺序一致。为避免队列型 Provider 因串行等待播放完成而出现句间合成空窗，将其 speak 的返回时机提前到"合成完成、已进入播放队列"，保留流水线。

## 作用域

- ChatViewModel：speakMessage 改为入队，新增 SpeakRequest Channel 与常驻消费者协程，原协程体迁入 processSpeakRequest；interrupt 请求取消当前子协程并清空积压；stopSpeaking 同步清空通道
- QueuedTtsPlayback：prepare 循环在合成成功后即 completion.complete(true)，播放循环照旧串行播放
- SiliconFlowVoiceProvider：同构队列同改
- MiniMax/Mimo/VITS/OpenAI 系为互斥模型，不改动，实现时复核播放时序

## 验收

- 自动朗读日志中 autoRead[cut] 序列与 speech[segmentSpeak] 序列、SimpleVoiceProvider queue[speak.submitted] 序列单调一致
- 朗读中发送新消息，旧朗读立即停止且旧轮积压句子不再插播
- 暂停后继续，句子按序恢复，不并发涌出
- 手动朗读行为不变
- 队列型 Provider 句间无可感知合成空窗

## 实施记录

### 步骤 1：ChatViewModel 朗读请求串行化 [DONE]

- 新增 SpeakRequest（message/interrupt/generation）、speakRequests Channel(UNLIMITED)、speakRequestGeneration（AtomicLong）
- speakMessage 只入队；interrupt=true 时提升代数、取消当前播放协程、清空积压
- 新增 startSpeakRequestConsumer 常驻消费者，逐条以子协程执行 processSpeakRequest（原 speakMessage 协程体原样迁移）并 join
- 消费者循环与 processSpeakRequest 入口各做一次代数校验：前者省去无效子协程，后者覆盖 Main.immediate 下"子协程体内联启动先于引用赋值"的时间窗，与 QueuedTtsPlayback 在合成、播放两处校验 isCurrent 同一模式
- stopSpeaking 先提升代数再取消与清空
- 涉及文件：app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/ChatViewModel.kt

### 步骤 2：队列型 Provider 的 speak 返回时机提前 [DONE]

- QueuedTtsPlayback 与 SiliconFlowVoiceProvider（同构队列）：prepare 循环在合成成功后先 completion.complete(true) 再入 playbackQueue，调用方不再等播放结束；播放循环不再回传 completion，播放阶段异常只记日志
- playPreparedRequest 由返回 Boolean 改为 Unit，结果不再有消费方
- MiniMax/Mimo 委托 HttpVoiceProvider（内部即 QueuedTtsPlayback），随本改动一并生效，无需单独修改；悬浮窗 SpeechInteractionManager 为 fire-and-forget 调用，不受返回时机变化影响
- 涉及文件：app/src/main/java/com/ai/assistance/operit/api/voice/QueuedTtsPlayback.kt、SiliconFlowVoiceProvider.kt

### 步骤 3：朗读清理链对齐复制功能的过滤 [DONE]

- processSpeakRequest 清理链在 TtsCleaner 之后、cleanContentForWaifu 之前插入 cleanMessageContentForCopy（复制消息同款过滤）
- 修复朗读链读出思考链的问题：ChatUtils.removeThinkingContent 区分大小写且不允许标签属性，`<Think>`、`<think >` 等变体漏删；复制链的 ChatMarkupRegex.thinkTag 忽略大小写并允许属性
- 同时补齐：OpenAI Responses 协议 meta、附件与工作区标签、内部 image:/媒体链接的整块移除
- 有意不修改 ChatUtils.removeThinkingContent：其被提示词构建链路共用（AIMessageManager、各 Provider），改正则有影响 LLM 输入的全局风险
- 有意不引入 markdownToPlainTextForCopy：AST 纯文本会保留代码块内容文本，与朗读删除代码块的目标冲突，markdown 剥离仍由 cleanContentForWaifu 负责
- 涉及文件：app/src/main/java/com/ai/assistance/operit/ui/features/chat/viewmodel/ChatViewModel.kt

### 已知边界

- 播放阶段（非合成阶段）的失败不再触发"朗读失败" toast，仅记日志；现状下播放阶段错误本就只记日志，无实际回退
- 悬浮窗朗读路径存在同类的并发提交模式（每次 speak 起独立协程），其修复不在本次作用域

