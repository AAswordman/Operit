# API 文档：`software_settings.d.ts`

`software_settings.d.ts` 描述的是 `Tools.SoftwareSettings` 命名空间。它负责读写软件运行配置，而不是普通业务工具调用。

## 作用

当前定义覆盖四类能力：

- 环境变量管理。
- 沙盒包启停、直接执行脚本与 MCP 启动日志。
- 语音服务与模型配置管理。
- 角色卡配置管理与 Tavern JSON 导入/导出。

## 运行时入口

```ts
Tools.SoftwareSettings
```

## 环境变量相关

### `readEnvironmentVariable(key)`

```ts
readEnvironmentVariable(key: string): Promise<EnvironmentVariableReadResultData>
```

读取当前环境变量配置。运行时会去除 `key` 首尾空白；空 key 返回失败。结果用 `exists` 区分不存在与存在，`value` 不存在时为 `null`。读取异常时仍返回带 `exists: false` 的结果数据，同时外层调用失败。

### `writeEnvironmentVariable(key, value?)`

```ts
writeEnvironmentVariable(key: string, value?: string): Promise<EnvironmentVariableWriteResultData>
```

写入环境变量；JS facade 把缺省的 `value` 转为空字符串。运行时去除 key 和待写值的首尾空白；空值或全空白会删除变量。`requestedValue` 保留原始传入文本，`value` 是操作后的当前值，`cleared` 表示是否执行清除。

## 沙盒包、脚本与 MCP

### `listSandboxPackages()`

```ts
listSandboxPackages(): Promise<SandboxPackagesResultData>
```

列出当前沙盒包及启用状态。`manageMode` 当前对内置包返回 `toggle_only`，对外部包返回 `file_and_toggle`；加载失败明细通过 `packageLoadErrors` 返回。

### `setSandboxPackageEnabled(packageName, enabled)`

```ts
setSandboxPackageEnabled(packageName: string, enabled: boolean | string | number): Promise<SandboxPackageUpdateResultData>
```

启用或禁用某个沙盒包。虽然声明接受 `boolean | string | number`，当前 JS facade 会先执行 `!!enabled`：请传布尔值；例如字符串 `'false'` 在 JS 中仍会转换为 `true`。未知包名或更新后状态与请求不一致时，外层调用失败并保留状态明细。

### `executeSandboxScriptDirect(options?)`

```ts
executeSandboxScriptDirect(options?: Partial<SandboxScriptDirectOptions>): Promise<SandboxScriptExecutionResultData>
```

直接执行 `source_path` 指向的脚本文件，或把 `source_code` 作为顶层脚本执行，两者必须且只能提供一个。`params_json` 缺省/空白时使用 `{}`，否则必须是 JSON object；`env_file_path` 可选，`script_label` 缺省为 `sandbox_script`。`wait_ms` 缺省为 `15000`；无法解析为整数时也使用该默认值，小于 `1000` 的整数按 `1000` 处理。文件不存在、参数 JSON 无效或两个 source 同时存在/都缺失时，返回 `success: false` 的结构化执行结果；成功与否也会由 `events` 和可选 `error`/`result` 描述。

### `restartMcpWithLogs(timeoutMs?)`

```ts
restartMcpWithLogs(timeoutMs?: number | string): Promise<McpRestartWithLogsResultData>
```

重启 MCP 启动流程，并收集每个插件的启动日志。输入缺省为 `120000` ms，并限制到 `5000..600000`；超时或至少一个插件启动失败时外层调用失败，但仍可能返回已收集的插件状态和日志。完整状态字段见[公共结果类型](./results.md)。

## 语音服务相关

### `getSpeechServicesConfig()`

```ts
getSpeechServicesConfig(): Promise<SpeechServicesConfigResultData>
```

读取当前 TTS 与 STT 档案配置。TTS 与 STT 档案分别读取；运行时还序列化声明未列出的 `ttsVitsPackageConfig`，详见[公共结果类型](./results.md)。

### `setSpeechServicesConfig(updates?)`

```ts
setSpeechServicesConfig(updates?: Partial<SpeechServicesUpdateOptions>): Promise<SpeechServicesUpdateResultData>
```

更新当前 TTS/STT 档案。只提交要变更的字段；未提供字段保持当前值。若没有传入任何支持的更新字段，调用失败。创建、切换和删除档案仍需在语音服务设置页完成。成功结果的 `changedFields` 是传入字段名，更新后会重置 TTS/STT service 实例。当前结果里的 `sttApiKeySet` 误从 TTS key 计算，不能用它判断 STT key 是否已配置。

可更新字段包括：

- TTS 服务与 HTTP 配置：`tts_service_type`、`tts_url_template`、`tts_api_key`、`tts_headers`、`tts_http_method`、`tts_request_body`、`tts_content_type`、`tts_locale`、`tts_voice_id`、`tts_model_name`
- TTS VITS/Piper：`tts_vits_package_path`、`tts_vits_speaker_id`、`tts_vits_options`
- TTS 后处理：`tts_response_pipeline`、`tts_cleaner_regexs`、`tts_speech_rate`、`tts_pitch`
- STT：`stt_service_type`、`stt_endpoint_url`、`stt_api_key`、`stt_model_name`

JS facade 会把 `tts_headers` 对象、`tts_response_pipeline` 数组和 `tts_cleaner_regexs` 数组编码为 JSON 字符串。运行时也接受对应的 JSON 字符串：headers 与 VITS options 必须是对象，cleaner regexs 与 response pipeline 必须是数组。显式传入空 headers/options/cleaner 数组会清空相应配置；空的 `tts_response_pipeline` 会清除管线，让 `HTTP_TTS` provider 直接把首个响应体作为音频处理。未传这些字段才是保留现值。HTTP method 仅接受 `GET`/`POST`；TTS/STT service type 按名称不区分大小写匹配。运行时把 STT 别名 `SHERPA_MNN` 映射为 `SHERPA_NCNN`。

### `testTtsPlayback(text, options?)`

```ts
testTtsPlayback(text: string, options?: Partial<TtsPlaybackTestOptions>): Promise<SpeechServicesTtsPlaybackTestResultData>
```

使用当前 TTS 配置播放一次测试文本。空白文本失败；`interrupt` 默认 `true`，`speech_rate` 与 `pitch` 未传时采用当前 TTS profile 值。该调用会重置并重新初始化 voice service，然后触发播放；初始化未成功或播放未启动时外层调用失败，结果数据仍包含 `initialized`、`playbackTriggered` 与可选错误诊断。此测试会实际发起语音播放。

## 模型配置相关

### `listModelConfigs()`

```ts
listModelConfigs(): Promise<ModelConfigsResultData>
```

列出所有模型配置以及当前函数绑定关系。

### `createModelConfig(options?)`

```ts
createModelConfig(options?: Partial<ModelConfigUpdateOptions> & { name?: string }): Promise<ModelConfigCreateResultData>
```

创建模型配置，返回配置快照和 `changedFields`。

### `updateModelConfig(configId, updates?)`

```ts
updateModelConfig(configId: string, updates?: Partial<ModelConfigUpdateOptions>): Promise<ModelConfigUpdateResultData>
```

更新指定模型配置，返回配置快照、变更字段和受影响的函数类型。

### `deleteModelConfig(configId)`

```ts
deleteModelConfig(configId: string): Promise<ModelConfigDeleteResultData>
```

删除指定模型配置并返回受影响函数与 fallback 配置 ID。

### `listFunctionModelConfigs()`

```ts
listFunctionModelConfigs(): Promise<FunctionModelConfigsResultData>
```

查看函数类型与模型配置的绑定关系。

### `getFunctionModelConfig(functionType)`

```ts
getFunctionModelConfig(functionType: string): Promise<FunctionModelConfigResultData>
```

查看某个函数类型当前绑定的模型配置。

### `setFunctionModelConfig(functionType, configId, modelIndex?)`

```ts
setFunctionModelConfig(functionType: string, configId: string, modelIndex?: number | string): Promise<FunctionModelBindingResultData>
```

把某个函数类型绑定到指定模型配置，并返回请求索引、实际索引和选择的模型。

### `testModelConfigConnection(configId, modelIndex?)`

```ts
testModelConfigConnection(configId: string, modelIndex?: number | string): Promise<ModelConfigConnectionTestResultData>
```

对某个模型配置执行连接测试。返回值区分连通与能力验证：

- `success`：本次测试没有硬失败。多模态请求返回了内容但没有命中探针时，仍可能为 `true`。
- `verified`：所有请求的测试项都已验证通过。
- `tests[].outcome`：单项结果，取值为 `passed`、`unverified` 或 `failed`。

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

同一组字段如 `temperature`、`top_p`、`max_tokens` 等带有对应的 `..._enabled` 布尔控制项时，应按类型定义一起传递。`custom_parameters`、`custom_headers` 可传 JSON 字符串，也可传对象；JS facade 会把对象编码成 JSON 字符串。

### 模型配置写入的取值约定

新建配置时，`name` 会先去除首尾空白；缺失或空白时使用 `New Model Config`。新配置的 provider 默认是 `OPENAI_GENERIC`。这只说明新建配置的默认值，不代表固定 ID `default` 的配置也使用相同 provider。创建流程先持久化基础配置，再解析其余选项；若选项校验失败，调用会返回失败，但基础配置可能已留在配置列表。

创建时的 `name` 单独处理，不计入 `changedFields`；其余写入字段只在调用方提供时才更新并列入 `changedFields`。更新时同样只应用已提供字段，但 `updated` / `changedFields` 不比较值是否与原值不同：提供一个字段即会将它列入 `changedFields`，即使新旧值相同。更新未提供任何可写字段时仍可成功，`updated` 为 `false`、`changedFields` 为空。所有字符串字段先去除首尾空白；整数和浮点字段解析失败会报错。布尔字段忽略大小写，接受 `1/true/yes/y/on` 和 `0/false/no/n/off`。

| 字段 | 运行时处理 |
| --- | --- |
| `api_provider_type` | 去除首尾空白且不可为空；已知枚举名按不区分大小写匹配。未知非空 provider ID 会映射到 `OTHER` 枚举，同时保留输入 ID。 |
| `max_tokens`、`top_k` | 分别限制为至少 `1`、至少 `0`。 |
| `context_length`、`max_context_length` | 限制为至少 `1`。 |
| `summary_token_threshold`、`summary_message_count_threshold` | 分别限制在 `0..1`、至少 `1`。 |
| `mnn_thread_count`、`llama_thread_count`、`llama_context_size`、`llama_batch_size`、`llama_ubatch_size` | 均限制为至少 `1`。 |
| `llama_gpu_layers` | 限制为至少 `0`；最终值不大于 `0` 时，`llama_offload_kqv` 会被强制设为 `false`。 |
| `request_limit_per_minute`、`max_concurrent_requests` | 均限制为至少 `0`。 |
| `temperature`、`top_p`、惩罚项及 `mnn_forward_type` | 解析为对应数值类型；此写入路径不额外钳制其范围。 |

`custom_parameters` 必须是 JSON 数组，`custom_headers` 必须是 JSON 对象；校验后仍以 JSON 字符串保存。空白 `custom_parameters` 会保存为 `[]`，空白 `custom_headers` 会保存为 `{}`。未传字段则保留原值。JS facade 会先把对象编码为 JSON 字符串。

当前 `ModelConfigUpdateOptions` 未声明以下实现可接受的附加字段：`llama_batch_size`、`llama_ubatch_size`、`llama_use_mmap`、`llama_flash_attention`、`llama_kv_unified`、`llama_offload_kqv`。这是声明与运行时的覆盖差异。

删除配置时，固定 ID `default` 会被拒绝；删除其他存在的配置会将引用它的函数绑定改回默认配置 ID，并把模型索引重置为 `0`。更新和删除都会尝试刷新受影响函数对应的服务；刷新异常会被捕获，不改变工具调用结果。

函数类型按枚举名忽略大小写匹配。`model_index` 缺失时使用 `0`，非整数文本会报错，负数会先钳制为 `0`；超出模型列表范围的索引也会落到 `0`。绑定结果分别返回请求索引和实际采用索引，实际索引会被保存。

连接测试会实际发起模型请求：`CHAT` 始终执行；`TOOL_CALL`、`IMAGE`、`AUDIO`、`VIDEO` 分别受对应配置开关控制，关闭的探针不会出现在结果列表中。连接测试不重试，也不记录 token 用量。`success` 表示没有失败探针；`verified` 表示至少有一个探针且所有已执行探针均通过，因此未启用的能力不会被验证。


## 角色卡配置相关

| 方法签名 | 行为 |
| --- | --- |
| `listCharacterCards(): Promise<CharacterCardsResultData>` | 列出完整角色卡配置及当前激活 ID。 |
| `getCharacterCard(characterCardId: string): Promise<CharacterCardResultData>` | 读取指定角色卡。 |
| `createCharacterCard(options: CharacterCardWriteOptions & { name: string }): Promise<CharacterCardCreateResultData>` | 新建角色卡；运行时要求 `name` 非空。 |
| `updateCharacterCard(characterCardId: string, updates: CharacterCardWriteOptions): Promise<CharacterCardUpdateResultData>` | 更新提供的字段；至少提供一个更新字段。 |
| `deleteCharacterCard(characterCardId: string): Promise<CharacterCardDeleteResultData>` | 删除角色卡；默认角色卡不能删除。 |
| `setActiveCharacterCard(characterCardId: string): Promise<CharacterCardActivationResultData>` | 激活现有角色卡。 |
| `clearActiveCharacterCard(): Promise<CharacterCardActivationResultData>` | 清除当前激活选择，返回的 `activeCharacterCardId` 为 `null`。 |
| `importCharacterCardFromTavernJson(tavernJson: string): Promise<CharacterCardImportResultData>` | 从 Tavern JSON 导入角色卡。 |
| `exportCharacterCardToTavernJson(characterCardId: string): Promise<CharacterCardExportResultData>` | 将角色卡导出为 Tavern JSON 字符串。 |

`CharacterCardWriteOptions` 的运行时写入规则如下：

- `name` 会 trim，创建时必填且不能是空白；更新时若提供也不能是空白。其他文本字段（`description`、`character_setting`、`opening_statement`、`other_content_chat`、`other_content_voice`、`advanced_custom_prompt`、`marks`）不做 trim 或非空校验；传 `""` 会清空，未传则保留原值。
- `attached_tag_ids` 和四个工具 allowlist 字段必须解码为 JSON 字符串数组。JS facade 会把数组序列化成 JSON；字符串输入需本身是合法 JSON 数组，元素必须全部为字符串。每项会 trim，空项移除，重复项去重并保留首次出现顺序；传 `[]` 会清空该列表。JSON 非法或元素不是字符串时，整次写入失败。
- `chat_model_binding_mode` 仅接受 `FOLLOW_GLOBAL`、`FIXED_CONFIG`；`memory_profile_binding_mode` 仅接受 `FOLLOW_GLOBAL`、`FIXED_PROFILE`。输入会 trim 并忽略大小写，保存为大写规范值；其他值会报错。未传时不改现有绑定模式。
- `chat_model_config_id`、`memory_profile_id` 会 trim；空白值会移除对应 ID，非空值保存为 trim 后的字符串。`chat_model_index` 必须能解析为整数且不小于 0；负数和非整数都会报错。这里的写入校验不验证 ID 是否存在，也不根据模型列表长度限制索引。
- `tool_access_enabled` 接受 trim 后不区分大小写的 `1/true/yes/y/on` 和 `0/false/no/n/off`；其他值会报错。各 allowlist 是分字段合并的：只更新本次提供的字段，未提供字段保留原配置。`enabled` 为 `false` 时不会开放额外工具；若归一后整个配置等于默认值，存储层会移除对应配置项，读取时等效于默认禁用配置。
- 创建时未提供的字段使用 `CharacterCard` 默认值：空文本、空标签列表、`FOLLOW_GLOBAL` 绑定、模型索引 `0`、空绑定 ID，以及禁用且为空的工具访问配置。若角色卡被标记为默认，或当前尚无激活卡，管理器会将该卡设为当前激活卡。
- 更新要求至少提供一个可写字段；仅有目标 ID 不够。`changedFields` 表示本次提供并通过解析的字段，不比较新旧值，因此相同值也会列入。解析失败返回失败结果，不执行持久化更新；创建/更新成功后返回重新读取的角色卡和当前激活 ID。

工具访问配置只有在 `tool_access_enabled` 开启时才覆盖全局可见性：内置工具必须同时允许于全局配置和角色卡 allowlist；`package_proxy` 还要求至少存在一个已允许的包、Skill 或 MCP 服务。外部来源须同时属于当前可用来源和角色卡对应 allowlist。关闭自定义工具访问时，解析器沿用全局工具可见性及当前全局外部来源。

`CharacterCardWriteOptions` 的静态类型把这些字段标为可选，但 JSON 数组字符串的元素类型、模式枚举和索引边界由运行时校验；写入器不会验证绑定 ID 的存在性，也不会校验 `chat_model_index` 是否落在配置模型列表范围内。

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
