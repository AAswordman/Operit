# API 文档：`software_settings.d.ts`

`software_settings.d.ts` 描述的是 `Tools.SoftwareSettings` 命名空间。它负责读写软件运行配置，而不是普通业务工具调用。

## 作用

当前定义覆盖三类能力：

- 环境变量管理。
- 沙盒包启停与 MCP 启动日志。
- 语音服务与模型配置管理。

## 运行时入口

```ts
Tools.SoftwareSettings
```

## 环境变量相关

### `readEnvironmentVariable(key)`

```ts
readEnvironmentVariable(key: string): Promise<StringResultData>
```

读取指定环境变量。

### `writeEnvironmentVariable(key, value?)`

```ts
writeEnvironmentVariable(key: string, value?: string): Promise<StringResultData>
```

写入环境变量；当 `value` 为空字符串时表示清空该变量。

## 沙盒与 MCP 相关

### `listSandboxPackages()`

列出当前沙盒包及启用状态。

### `setSandboxPackageEnabled(packageName, enabled)`

启用或禁用某个沙盒包。

### `restartMcpWithLogs(timeoutMs?)`

重启 MCP 启动流程，并收集每个插件的启动日志。

## 语音服务相关

### `getSpeechServicesConfig()`

读取当前 TTS / STT 档案的配置，返回 `SpeechServicesConfigResultData`。语音配置现在由独立的 TTS/STT 档案管理；该 API 为保持兼容，仍然读写当前档案，不改变原有字段。

### `setSpeechServicesConfig(updates?)`

更新当前 TTS / STT 档案，返回 `SpeechServicesUpdateResultData`。创建、切换和删除档案需要在语音服务设置页完成。

可更新字段包括：

- TTS：`tts_service_type`、`tts_url_template`、`tts_api_key`、`tts_headers`、`tts_http_method`、`tts_request_body`、`tts_content_type`、`tts_locale`、`tts_voice_id`、`tts_model_name`、`tts_response_pipeline`、`tts_cleaner_regexs`、`tts_speech_rate`、`tts_pitch`
- STT：`stt_service_type`、`stt_endpoint_url`、`stt_api_key`、`stt_model_name`

其中 `tts_response_pipeline` 仅用于 `HTTP_TTS`，值为 JSON 数组字符串。留空或传 `[]` 时保持旧行为，直接把首个响应体当作音频；填写后会按步骤执行响应处理。

## 模型配置相关

### `listModelConfigs()`

列出所有模型配置以及当前函数绑定关系。

### `createModelConfig(options?)`

创建模型配置，返回 `ModelConfigCreateResultData`。

### `updateModelConfig(configId, updates?)`

更新指定模型配置，返回 `ModelConfigUpdateResultData`。

### `deleteModelConfig(configId)`

删除模型配置，返回 `ModelConfigDeleteResultData`。

### `listFunctionModelConfigs()`

查看函数类型与模型配置的绑定关系。

### `getFunctionModelConfig(functionType)`

查看某个函数类型当前绑定的模型配置。

### `setFunctionModelConfig(functionType, configId, modelIndex?)`

把某个函数类型绑定到指定模型配置。

### `testModelConfigConnection(configId, modelIndex?)`

对某个模型配置执行连接测试，返回 `ModelConfigConnectionTestResultData`。

返回值区分连通与能力验证：

- `success`：本次测试没有硬失败。多模态请求返回了内容但没有命中探针时，仍可能为 `true`
- `verified`：所有请求的测试项都已验证通过
- `tests[].outcome`：单项结果，取值为 `passed`、`unverified` 或 `failed`

## 模型配置可更新字段

`ModelConfigUpdateOptions` 中可见的主要字段包括：

- 接口信息：`name`、`api_provider_type`、`api_endpoint`、`api_key`、`model_name`
- 采样参数：`max_tokens`、`temperature`、`top_p`、`top_k`
- 惩罚项：`presence_penalty`、`frequency_penalty`、`repetition_penalty`
- 上下文参数：`context_length`、`max_context_length`、`enable_max_context_mode`
- 摘要相关：`summary_token_threshold`、`enable_summary`、`enable_summary_by_message_count`、`summary_message_count_threshold`
- 多模态开关：`enable_direct_image_processing`、`enable_direct_audio_processing`、`enable_direct_video_processing`
- 扩展能力：`enable_google_search`、`enable_tool_call`
- 本地模型/并发控制：`mnn_forward_type`、`mnn_thread_count`、`llama_thread_count`、`llama_context_size`、`request_limit_per_minute`、`max_concurrent_requests`

同一组字段都带有对应的 `..._enabled` 布尔控制项时，应按类型定义一起传递。

## 示例

### 读取环境变量

```ts
const apiKey = await Tools.SoftwareSettings.readEnvironmentVariable('OPENAI_API_KEY');
console.log(apiKey.value);
```

### 更新语音服务配置

```ts
await Tools.SoftwareSettings.setSpeechServicesConfig({
  tts_service_type: 'SIMPLE_TTS',
  tts_locale: 'en-US',
  tts_voice_id: 'en-us-x-sfg#female_1-local'
});
```

### 测试模型配置连接

```ts
const result = await Tools.SoftwareSettings.testModelConfigConnection('config_123');
complete(result);
```

## 相关文件

- `examples/types/software_settings.d.ts`
- `examples/types/results.d.ts`
- `examples/types/index.d.ts`
