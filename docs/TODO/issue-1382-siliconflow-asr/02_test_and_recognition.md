# 模型测试与音频识别接入

## 旧实现

- `ModelConfigConnectionTester.run()` 无条件用 `AIService.sendMessage()` 构建聊天请求；
  `enableDirectAudioProcessing` 为真时追加 AUDIO 项，但同样走聊天 JSON。
- `ConversationService.analyzeAudioWithIntent()` 通过 `AIService.sendMessage()` 把音频 `<link>` 与提示词
  发送给 `AUDIO_RECOGNITION` 配置的模型。

## 新实现

- 模型测试：
  - 端点判定为转写接口时，CHAT 项直接以明确诊断失败（该端点不是聊天接口），AUDIO 项改为读取
    `test/1.mp3`，用 `AudioTranscriptionProtocol` 发起真实 multipart 转写，并按 `MediaCapabilityProbe.matchesAudio`
    对转写文本判定；
  - 端点不是转写接口时保持原有聊天探针与 `input_audio` 行为。
- 音频识别：
  - `AUDIO_RECOGNITION` 指向转写端点时，读取音频文件字节，走 `AudioTranscriptionProtocol` 发起 multipart
    请求，返回转写文本；
  - 其他端点保持聊天 `<link>` 流程。
- 转写请求复用模型配置中的 API Key、模型名与自定义请求头；不引入新的配置项。

## 验收

- 端点 = `https://api.siliconflow.cn/v1/audio/transcriptions`、模型 = `FunAudioLLM/SenseVoiceSmall` 时，
  AUDIO 项得到真实转写文本并通过；
- 同一配置的 CHAT 项提示端点类型不匹配；
- 普通聊天端点（含原生音频聊天模型）的行为与之前一致。
