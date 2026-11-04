# 测试与验证记录

## 自动化测试

新增 `app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/AudioTranscriptionProtocolTest.kt`：

- 端点判定：`/v1/audio/transcriptions`（含大小写、查询参数、尾斜杠变体）为转写端点；
  `/v1/chat/completions`、`/v1`、普通聊天地址不是转写端点；
- 硅基流动 host 判定：`api.siliconflow.cn` / `api.siliconflow.com` 命中，其他 host 不命中；
- multipart 构造：`Content-Type` 为 `multipart/form-data`，字段顺序为 `file`、`model`；可选字段只在显式传入时出现；
- 响应解析：`{"text":"..."}` 取 `text`，纯文本原样返回；
- 错误体归一：硅基流动 `{"code":30014,"message":"..."}` 与 OpenAI `{"error":{"message":"..."}}`，空错误体返回 `null`。

`ChatConfigReadinessTest` 保持原样：端点类型诊断放在模型配置页的端点提示与连接测试结果中，
`ChatConfigReadiness` 仍只负责聊天配置的通用就绪判断，避免把“转写端点”当成配置错误而阻断保存。

## 本地验证记录（2026-10-23）

| 项目 | 命令 | 结果 |
| --- | --- | --- |
| 编译 | `.\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon` | BUILD SUCCESSFUL（仅存量 deprecation 警告） |
| 单元测试 | `.\gradlew.bat :app:testDebugUnitTest --tests "...AudioTranscriptionProtocolTest"` | BUILD SUCCESSFUL；`AudioTranscriptionProtocolTest` 12 项全部通过 |
| 本地化 | `python ci/script/check_localizations.py --base <upstream/dev> --candidate <HEAD>` | 见 PR 检查记录 |
| 仓库卫生 | `python ci/script/check_repo_hygiene.py --base <upstream/dev> --candidate <HEAD>` | 见 PR 检查记录 |
| Markdown | `python ci/script/check_markdown_links.py --base <upstream/dev> --candidate <HEAD>` | 见 PR 检查记录 |

## 上游行为核验（无真实 Key）

用假 Key `sk-invalid-test` 直接请求硅基流动，确认接口协议（2026-10-23）：

| 请求 | 结果 |
| --- | --- |
| `POST /v1/audio/transcriptions`，JSON body | `401 {"code":30014,"data":null,"message":"Token is invalid."}` |
| `POST /v1/audio/transcriptions`，multipart 仅 `file` + `model` | 同上 401 |
| `POST /v1/audio/transcriptions`，multipart 额外 `language` + `response_format` | 同上 401 |

鉴权先于参数校验，没有真实 Key 时无法在线区分 400 的具体原因；但官方 OpenAPI（`.issue-assets/openapi.yaml`）
明确 `AudioRequest.required = [model, file]`、`AudioResponse.required = [text]`，且
`ChatCompletionRequestUserMessageContentPart` 只有 `image_url` / `text`，没有 `input_audio`。
因此“把转写端点当聊天端点”必然 400，本次修复按该契约实现并用 JVM 测试保证请求形状。

## 手工验证步骤（需要真实硅基流动 Key）

1. 在「语音服务 → 语音识别」新建 OpenAI STT 配置：
   - URL：`https://api.siliconflow.cn/v1/audio/transcriptions`
   - API Key：真实 Key
   - 模型：`FunAudioLLM/SenseVoiceSmall`
2. 录音后确认返回转写文本（请求体只含 `file` 与 `model`）。
3. 在「AI模型配置」把端点设为同一转写地址，点击「测试连接」：
   - CHAT 项显示“当前端点是语音转写接口（/audio/transcriptions），不能作为聊天接口使用……”；
   - AUDIO 项用 `test/1.mp3` 发起真实 multipart 转写，返回文本包含测试音频的三位数字时判定通过。
4. 把端点改回 `https://api.siliconflow.cn/v1/chat/completions`，确认聊天与原生音频测试行为与修复前一致。
5. 在「功能配置 → 音频识别」绑定该转写配置，读取一个音频文件并触发识别，确认返回转写文本。

## 说明

- 本机没有 `SILICONFLOW_API_KEY`，无法执行需要真实 Key 的端到端转写；上述第 1-5 步需要在有 Key 的环境补跑。
- 所有改动只在明确命中 `/audio/transcriptions` 路径时生效，不按模型名猜测，不影响其他厂商与普通聊天配置。
