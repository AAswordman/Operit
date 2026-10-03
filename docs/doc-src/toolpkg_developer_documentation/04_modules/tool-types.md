# API 文档：`tool-types.d.ts`

`tool-types.d.ts` 定义 `ToolResultMap`，把工具名映射到 `toolCall()` 的静态返回数据类型。它是 TypeScript 类型层的索引，不是运行时工具注册表，也不会新增、删除或检查工具能力。

## 类型层行为

`core.d.ts` 中的 `ToolReturnType<T>` 按工具名查找 `ToolResultMap[T]`：

```ts
export type ToolReturnType<T extends string> = T extends keyof import('./tool-types').ToolResultMap
    ? import('./tool-types').ToolResultMap[T]
    : any;
```

因此，工具名需要保持为已知字面量或已知键的联合类型，才能获得精确结果类型。普通的 `string` 变量、声明中没有的工具名和动态包工具会退化为 `any`。映射值是当前声明所描述的返回数据类型；`BaseResult` 的 `success`/`error` 包装契约由核心结果声明和运行时工具调用处理，不由 `ToolResultMap` 再包一层。

`ToolResultMap` 不描述参数。参数名称、归一化、默认值、权限、超时、流式事件和错误语义仍应查对应模块页及 `.d.ts`。

## 调用形式

```ts
const file = await toolCall('read_file_full', {
    path: '/sdcard/demo.txt'
});

file.content;
file.size;
```

`toolCall()` 还支持对象形式，以及带工具类型前缀的兼容形式：

```ts
const page = await toolCall({
    name: 'get_page_info'
});

const result = await toolCall('system', 'device_info', {});
```

带中间结果回调的调用，其 Promise 最终类型仍由工具名映射决定；中间事件类型要看具体模块声明：

```ts
const finalResult = await toolCall(
    'send_message_to_ai_streaming',
    { message: 'hello' },
    { onIntermediateResult: event => console.log(event) }
);
```

## 完整映射

以下清单与 `examples/types/tool-types.d.ts` 的 `ToolResultMap` 保持一致，共 151 个工具名映射。`string` 是 TypeScript 原始类型，不是未声明的 `StringResultData`。

### 文件操作

| 工具名 | 返回数据类型 |
| --- | --- |
| `list_files` | `DirectoryListingData` |
| `read_file` | `FileContentData` |
| `read_file_part` | `FilePartContentData` |
| `read_file_full` | `FileContentData` |
| `read_file_binary` | `BinaryFileContentData` |
| `write_file` | `FileOperationData` |
| `delete_file` | `FileOperationData` |
| `file_exists` | `FileExistsData` |
| `move_file` | `FileOperationData` |
| `copy_file` | `FileOperationData` |
| `make_directory` | `FileOperationData` |
| `find_files` | `FindFilesResultData` |
| `grep_code` | `GrepResultData` |
| `grep_context` | `GrepResultData` |
| `file_info` | `FileInfoData` |
| `zip_files` | `FileOperationData` |
| `unzip_files` | `FileOperationData` |
| `open_file` | `FileOperationData` |
| `share_file` | `FileOperationData` |
| `download_file` | `FileOperationData` |
| `apply_file` | `FileApplyResultData` |
| `create_file` | `FileApplyResultData` |
| `edit_file` | `FileApplyResultData` |

### 网络操作

| 工具名 | 返回数据类型 |
| --- | --- |
| `http_request` | `HttpResponseData` |
| `visit_web` | `VisitWebResultData` |
| `browser_click` | `string` |
| `browser_close` | `string` |
| `browser_close_all` | `string` |
| `browser_console_messages` | `string` |
| `browser_drag` | `string` |
| `browser_evaluate` | `string` |
| `browser_file_upload` | `string` |
| `browser_fill_form` | `string` |
| `browser_handle_dialog` | `string` |
| `browser_hover` | `string` |
| `browser_navigate` | `string` |
| `browser_navigate_back` | `string` |
| `browser_network_requests` | `string` |
| `browser_press_key` | `string` |
| `browser_resize` | `string` |
| `browser_run_code` | `string` |
| `browser_select_option` | `string` |
| `browser_wait_for` | `string` |
| `browser_snapshot` | `string` |
| `browser_take_screenshot` | `string` |
| `browser_type` | `string` |
| `browser_tabs` | `string` |
| `multipart_request` | `HttpResponseData` |
| `manage_cookies` | `HttpResponseData` |

`visit_web` 的访问和超时语义见[Network 参考](network.md)；浏览器会话操作的参数约束也见该页。`ToolResultMap` 只记录最终返回类型，不记录页面访问或浏览器会话状态。

### 系统操作

| 工具名 | 返回数据类型 |
| --- | --- |
| `sleep` | `SleepResultData` |
| `get_system_setting` | `SystemSettingData` |
| `modify_system_setting` | `SystemSettingData` |
| `toast` | `string` |
| `send_notification` | `string` |
| `install_app` | `AppOperationData` |
| `uninstall_app` | `AppOperationData` |
| `list_installed_apps` | `AppListData` |
| `start_app` | `AppOperationData` |
| `stop_app` | `AppOperationData` |
| `device_info` | `DeviceInfoResultData` |
| `get_notifications` | `NotificationData` |
| `get_device_location` | `LocationData` |
| `request_bluetooth_permission` | `string` |
| `get_bluetooth_state` | `BluetoothStateData` |
| `request_enable_bluetooth` | `string` |
| `list_bluetooth_bonded_devices` | `BluetoothBondedDevicesData` |
| `scan_bluetooth_devices` | `BluetoothScanResultData` |
| `bluetooth_connect` | `BluetoothSessionData` |
| `bluetooth_listen` | `BluetoothSessionData` |
| `bluetooth_accept` | `BluetoothSessionData` |
| `bluetooth_send` | `BluetoothTransferData` |
| `bluetooth_read` | `BluetoothReadData` |
| `bluetooth_send_and_read` | `BluetoothReadData` |
| `bluetooth_close` | `string` |
| `bluetooth_ble_connect` | `BluetoothSessionData` |
| `bluetooth_ble_discover_services` | `BluetoothBleServicesData` |
| `bluetooth_ble_read_characteristic` | `BluetoothReadData` |
| `bluetooth_ble_write_characteristic` | `BluetoothTransferData` |
| `bluetooth_ble_write_and_read_characteristic` | `BluetoothReadData` |
| `bluetooth_ble_subscribe_characteristic` | `BluetoothTransferData` |
| `bluetooth_ble_read_notifications` | `BluetoothBleNotificationData` |
| `read_environment_variable` | `EnvironmentVariableReadResultData` |
| `write_environment_variable` | `EnvironmentVariableWriteResultData` |
| `list_sandbox_packages` | `SandboxPackagesResultData` |
| `set_sandbox_package_enabled` | `SandboxPackageUpdateResultData` |
| `execute_sandbox_script_direct` | `SandboxScriptExecutionResultData` |
| `restart_mcp_with_logs` | `McpRestartWithLogsResultData` |
| `get_speech_services_config` | `SpeechServicesConfigResultData` |
| `set_speech_services_config` | `SpeechServicesUpdateResultData` |
| `list_model_configs` | `ModelConfigsResultData` |
| `create_model_config` | `ModelConfigCreateResultData` |
| `update_model_config` | `ModelConfigUpdateResultData` |
| `delete_model_config` | `ModelConfigDeleteResultData` |
| `list_function_model_configs` | `FunctionModelConfigsResultData` |
| `get_function_model_config` | `FunctionModelConfigResultData` |
| `set_function_model_config` | `FunctionModelBindingResultData` |
| `test_model_config_connection` | `ModelConfigConnectionTestResultData` |
| `trigger_tasker_event` | `string` |

### UI 操作

| 工具名 | 返回数据类型 |
| --- | --- |
| `get_page_info` | `UIPageResultData` |
| `capture_screenshot` | `string` |
| `click_element` | `UIActionResultData` |
| `tap` | `UIActionResultData` |
| `set_input_text` | `UIActionResultData` |
| `press_key` | `UIActionResultData` |
| `swipe` | `UIActionResultData` |
| `combined_operation` | `CombinedOperationResultData` |
| `run_ui_subagent` | `AutomationExecutionResultData` |

### 计算、包与记忆查询

| 工具名 | 返回数据类型 |
| --- | --- |
| `calculate` | `CalculationResultData` |
| `use_package` | `string` |
| `query_memory` | `MemoryQueryResultData` |

`use_package` 只映射宿主返回的包加载结果；被加载包提供的工具名不属于这份固定映射。Memory facade 的查询、写入和链接方法见[Memory 参考](memory.md)。

### FFmpeg

| 工具名 | 返回数据类型 |
| --- | --- |
| `ffmpeg_execute` | `FFmpegResultData` |
| `ffmpeg_info` | `FFmpegResultData` |
| `ffmpeg_convert` | `FFmpegResultData` |

### ADB、Intent 与 Terminal

| 工具名 | 返回数据类型 |
| --- | --- |
| `execute_shell` | `ADBResultData` |
| `execute_intent` | `IntentResultData` |
| `send_broadcast` | `IntentResultData` |
| `execute_terminal` | `TerminalCommandResultData` |
| `execute_in_terminal_session_streaming` | `TerminalCommandResultData` |
| `execute_hidden_terminal_command` | `HiddenTerminalCommandResultData` |
| `get_terminal_session_screen` | `TerminalSessionScreenResultData` |

`execute_terminal` 是声明中的历史名称；当前 facade 使用 `Tools.System.terminal` 下的会话工具，实际工具名和结果细节见[System 参考](system.md)。

### 音乐播放

| 工具名 | 返回数据类型 |
| --- | --- |
| `music_play` | `MusicPlaybackResultData` |
| `music_play_queue` | `MusicPlaybackResultData` |
| `music_pause` | `MusicPlaybackResultData` |
| `music_resume` | `MusicPlaybackResultData` |
| `music_stop` | `MusicPlaybackResultData` |
| `music_seek` | `MusicPlaybackResultData` |
| `music_set_volume` | `MusicPlaybackResultData` |
| `music_status` | `MusicPlaybackResultData` |

### 工作流

| 工具名 | 返回数据类型 |
| --- | --- |
| `get_all_workflows` | `WorkflowListResultData` |
| `create_workflow` | `WorkflowDetailResultData` |
| `get_workflow` | `WorkflowDetailResultData` |
| `update_workflow` | `WorkflowDetailResultData` |
| `patch_workflow` | `WorkflowDetailResultData` |
| `delete_workflow` | `string` |
| `trigger_workflow` | `string` |

### Chat Manager

| 工具名 | 返回数据类型 |
| --- | --- |
| `start_chat_service` | `ChatServiceStartResultData` |
| `create_new_chat` | `ChatCreationResultData` |
| `list_chats` | `ChatListResultData` |
| `find_chat` | `ChatFindResultData` |
| `agent_status` | `AgentStatusResultData` |
| `switch_chat` | `ChatSwitchResultData` |
| `update_chat_title` | `ChatTitleUpdateResultData` |
| `delete_chat` | `ChatDeleteResultData` |
| `send_message_to_ai` | `MessageSendResultData` |
| `send_message_to_ai_streaming` | `MessageSendResultData` |
| `call_chat_model` | `ChatCallResultData` |
| `list_character_cards` | `CharacterCardListResultData` |
| `get_chat_messages` | `ChatMessagesResultData` |
| `get_chat_messages_range` | `ChatMessagesResultData` |

### Memory 链接

| 工具名 | 返回数据类型 |
| --- | --- |
| `link_memories` | `MemoryLinkResultData` |
| `query_memory_links` | `MemoryLinkQueryResultData` |

## 声明与运行时的边界

本轮按 `ToolResultMap`、`JsTools.kt`、`ToolRegistration.kt` 和相关 facade 做了静态集合核对，结果如下：

- `ToolResultMap` 有 151 个键；其引用的结果数据类型均能在 `results.d.ts` 找到声明。
- `JsTools.kt` 中直接写出的 `toolCall("...")` 工具名有 174 个去重值。当前 facade 直接调用但没有进入 `ToolResultMap` 的名称包括：

```text
write_file_binary
get_app_usage_time
create_terminal_session
execute_in_terminal_session
close_terminal_session
input_in_terminal_session
test_tts_playback
list_character_cards_settings
get_character_card
create_character_card
update_character_card
delete_character_card
set_active_character_card
clear_active_character_card
import_character_card_from_tavern_json
export_character_card_to_tavern_json
long_press
get_memory_by_title
create_memory
update_memory
delete_memory
move_memory
update_memory_link
delete_memory_link
enable_workflow
disable_workflow
```

- `ToolRegistration.kt` 中还注册了但没有静态映射的内部或兼容入口，包括 `close_all_virtual_displays`、`update_user_profile`、`update_user_preferences`、`stop_chat_service` 和 `package_proxy`。`package_proxy` 的目标工具名是动态的，不能用一个固定结果类型覆盖。
- `combined_operation` 和 `execute_terminal` 在当前工作区只保留于声明及文档文本中，未找到对应的当前 `registerTool` 或 `toolCall` 实现。它们因此保留在本页的声明清单中，并作为兼容性差异记录，不应据此推断当前宿主一定提供这些入口。
- `read_file` 没有出现在 `JsTools.kt` 的直接调用集合中；当前 Files facade 使用 `read_file_full`，但宿主注册表和声明仍保留 `read_file`，所以它属于直接工具调用/兼容入口差异。
- `use_package` 后可加载的包工具、CLI 搜索和代理目标由运行时内容决定，固定的 `ToolResultMap` 不可能枚举这些名称。需要精确类型时，应为包自己的工具声明单独建立映射，或为动态工具保留显式结果类型。

这些差异记录的是当前声明与实现的状态，不在本页自动修改 `.d.ts` 或运行时代码。若要补齐缺失映射，应单独评估兼容性和每个工具的真实结果结构。

## 相关文件

- `examples/types/tool-types.d.ts`
- `examples/types/core.d.ts`
- `examples/types/results.d.ts`
- `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
- [公共结果类型](results.md)
- [Files 参考](files.md)
- [Network 参考](network.md)
- [System 参考](system.md)
- [UI 参考](ui.md)
- [Memory 参考](memory.md)
