---
feature: siliconflow-asr-transcription
branch: fix/issue-1382-siliconflow-asr
fork_repository: https://github.com/yoruuuchan/Operit.git
status: in-progress
---

# 硅基流动 ASR 配置与调用修复

## 原本状况

硅基流动的语音转写接口是 `POST https://api.siliconflow.cn/v1/audio/transcriptions`，请求体必须是
`multipart/form-data`，官方 OpenAPI 只声明 `file` 与 `model` 两个字段，模型为
`FunAudioLLM/SenseVoiceSmall`、`TeleAI/TeleSpeechASR`，成功响应为 `{"text": "..."}`。

Operit 目前只有两条音频链路：

1. 聊天模型的“原生音频输入”：勾选「模型支持音频解析」（`enableDirectAudioProcessing`）后，把音频以
   `input_audio` content part 放进 `POST /v1/chat/completions` 的 JSON 请求体。
2. 语音服务里的 STT 档案：`OpenAISttProvider` 组装 `multipart/form-data` 请求。

“AI模型配置”里的「测试」同时执行 CHAT 与 AUDIO 两项，二者都调用 `AIService.sendMessage()`，
也就是按聊天接口发 JSON。用户在厂商硅基流动、端点填 `/v1/audio/transcriptions`、勾选
「模型支持音频解析」后：

- CHAT 测试把 chat/completions 的 JSON 打到只接受 multipart 的转写接口，HTTP 400；
- AUDIO 测试同样先发聊天 JSON，HTTP 400，界面只显示状态码与空错误体；
- 硅基流动的 ASR 模型本身不是聊天模型，无法通过 `/v1/chat/completions` 调用。

## 目标

- 模型配置测试能识别转写端点，按真实转写协议（multipart `file` + `model`）验证 ASR 模型；
- 「音频识别」功能使用转写端点时，真实请求同样走 multipart 转写，而不是聊天 JSON；
- 把转写端点配置成聊天端点时，给出明确、可操作的诊断，而不是空白 400；
- 非转写端点、其他厂商的聊天与原生音频行为保持不变。

## 作用域

- `ModelConfigConnectionTester`：按端点选择聊天探针或转写探针，AUDIO 项使用转写结果判定。
- 新增转写协议客户端（multipart 请求、响应解析、错误信息提取）。
- `ConversationService.analyzeAudioWithIntent`：`AUDIO_RECOGNITION` 指向转写端点时使用转写客户端。
- `ChatConfigReadiness` / 设置页：转写端点用于聊天配置时的诊断文案。
- `OpenAISttProvider`：硅基流动请求只发送官方声明字段。
- `app/src/test`：端点判定、multipart 字段、响应解析、错误信息与测试项选择的 JVM 测试。

## 验收

- 使用 `FunAudioLLM/SenseVoiceSmall` 与完整转写端点，模型配置测试的 AUDIO 项通过，并返回转写文本；
- 同一端点下 CHAT 项给出“该端点是语音转写接口，不是聊天接口”的明确诊断；
- 音频识别通过转写端点得到文本；
- 其他厂商/普通聊天端点行为不变；
- JVM 单元测试与仓库检查通过，并记录可复现的验证步骤。

## 步骤

1. [转写协议客户端与端点判定](./01_transcription_protocol.md) [DONE]
2. [模型测试与音频识别接入](./02_test_and_recognition.md) [DONE]
3. [配置诊断与 STT 字段校正](./03_diagnostics_and_stt.md) [DONE]
4. [测试与验证记录](./04_verification.md) [DONE]
