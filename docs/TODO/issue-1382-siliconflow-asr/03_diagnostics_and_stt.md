# 配置诊断与 STT 字段校正

## 旧实现

- 设置页没有任何地方解释“`/audio/transcriptions` 是转写端点，不能当聊天端点”。
- 模型配置测试遇到转写端点时，只会把聊天 JSON 打过去得到空白 400。
- `OpenAISttProvider` 无条件发送 `response_format=json`，并在有语言代码时发送 `language`。

## 新实现

- `ModelApiSettingsSection` 在端点输入框下方增加常驻提示：当补全后的端点命中 `/audio/transcriptions`
  时，显示“这是语音转写接口，只能接收 multipart 录音文件，聊天请用 `/v1/chat/completions`”；
- 模型配置页的聊天测试（`ModelConfigConnectionTester`）遇到转写端点时，CHAT 项直接给出同样的
  可操作诊断，不再发送一次注定失败的聊天请求；
- 语音服务 → 语音识别（OpenAI STT）的模型输入框在硅基流动 host 下显示可用模型提示
  （`FunAudioLLM/SenseVoiceSmall`、`TeleAI/TeleSpeechASR`）；
- `OpenAISttProvider` 按服务商发送字段：
  - 硅基流动（端点 host 为 `api.siliconflow.cn` 或 `api.siliconflow.com`）：只发 `file`、`model`；
  - 其他 OpenAI 兼容服务：保持 `language`（可选）与 `response_format=json`；
- 文案覆盖全部 locale 文件，保持既有翻译结构。

## 约束

- 诊断只针对明确的转写路径，不做基于模型名的猜测。
- 不改变已有 STT 档案存储结构与调用接口。
- 不修改 `ChatConfigReadiness`：它服务于聊天配置的通用就绪判断，端点类型提示放在端点输入处与测试结果中，
  避免把“转写端点”误判成聊天配置不合法而阻断保存。
