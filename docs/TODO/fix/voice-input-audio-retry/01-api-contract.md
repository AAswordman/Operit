# 硅基流动音频接口契约

## 官方依据

[Chat Completions](https://docs.siliconflow.cn/docs/api/chat-completions-post) 的 `messages.content` 数组定义说明：Qwen3-Omni 系列支持 `audio_url` 与 `video_url`，建议音频和视频时长在 30 秒以内。音频内容部分定义为 `type: audio_url`，数据放在 `audio_url.url`，支持带 MIME 类型的 Base64 数据地址。该定义位于页面折叠的消息内容字段，页面顶层的文本摘录没有展开这一层。

[Audio Transcriptions](https://docs.siliconflow.cn/docs/api/audio-transcriptions-post) 是独立的 `POST /v1/audio/transcriptions` 接口，使用 `multipart/form-data` 上传 `file` 和 `model`，响应为 `{"text":"识别文字"}`。文档列出的识别模型为 `FunAudioLLM/SenseVoiceSmall` 和 `TeleAI/TeleSpeechASR`；这些识别模型不能作为对话接口的音频模型使用。

## 出站格式与代码修正

硅基流动的直接音频内容应为：

```json
{
  "type": "audio_url",
  "audio_url": {
    "url": "data:audio/wav;base64,<录音的 Base64 数据>"
  }
}
```

原有 `OpenAIProvider.buildContentField` 对所有兼容服务商统一构造 `input_audio`。`AIServiceFactory` 为硅基流动创建 `QwenAIProvider`，但它只为阿里云修改内部音频数据，没有替换硅基流动的内容类型，因此生成的请求不符合上述定义。

现在由 `buildAudioContentPart` 组装完整音频内容部分，`QwenAIProvider` 只在服务商为 `SILICONFLOW` 时生成 `audio_url`。直接音频对话与音频理解功能模型均经过这个入口；OpenAI 的原始 Base64 `input_audio`、阿里云的 Data URL `input_audio` 和视频内容格式继续使用各自的实现。

此修正要求所选模型具备音频输入能力。它不会让仅支持文字的模型获得音频能力，也不会更改持久化的能力开关或新增自动切换路径。

## 为什么后续请求能够识别

既有流程为：未解析录音先由允许直接音频输入的对话模型处理，失败后进入已配置的音频理解功能模型，最后由 STT 识别。报告摘录在 `input_audio` 被拒后已有 `来源=STT` 的完成记录，表明至少一次后续成功来自转写路径，不能把它视为同一 `input_audio` 请求第二次被服务端接受。

`VoiceAudioRequest.resolve` 在 STT 或音频理解完成后调用 `onResolved`。`MessageProcessingDelegate` 将该回调交给 `ChatHistoryDelegate.resolveVoiceMessage`，后者通过 `VoiceMessageMarkup.withResolvedAnalysis` 持久化转写文字和 `transcriptResolved=true`，随后才返回结果并继续文字对话。本 PR 让这类已识别消息在后续历史或手动重试时复用文字。

错误状态码外层虽为 503，业务内容却明确拒绝内容类型。重复发送相同字段不能修复协议；已有重试修正因此仍有必要。音频尝试通知的隔离则避免既有 STT 路径成功后仍留下已放弃尝试的错误卡片。

## STT 参数的证据边界

当前 OpenAI 兼容 STT 实现还发送 `language` 和 `response_format=json`。硅基流动上述页面没有列出这两个可选字段，但报告中的拒绝对象为对话内容类型 `input_audio`，且后续 STT 已完成，没有证据证明这两个字段导致本次故障。因此本次只修正已确认的直接音频内容格式，不改动共享 STT 上传参数。

接口定义和调用链已静态核对；本地没有调用付费音频接口、构建 APK 或运行测试。新增出站结构回归交由仓库 Android Tests 执行，真实识别效果以仓库检查与后续反馈为准。

[DONE] 接口字段核对与适配实现完成，工作流结果待确认。
