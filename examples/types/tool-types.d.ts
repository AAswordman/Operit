/**
 * Tool name type definitions for Assistance Package Tools
 * 
 * This file defines the available tool names and maps them to their result types.
 */

import {
    DirectoryListingData, FileContentData, BinaryFileContentData, FileOperationData, FileExistsData,
    FindFilesResultData, FileInfoData, FileConversionResultData, FileFormatConversionsResultData,
    HttpResponseData, VisitWebResultData,
    SleepResultData, SystemSettingData, AppOperationData, AppListData,
    DeviceInfoResultData, NotificationData, LocationData, BluetoothStateData, BluetoothBondedDevicesData,
    BluetoothScanResultData, BluetoothSessionData, BluetoothTransferData, BluetoothReadData,
    BluetoothBleServicesData, BluetoothBleNotificationData,
    UIPageResultData, UIActionResultData, CombinedOperationResultData, AutomationExecutionResultData,
    CalculationResultData, FFmpegResultData, ADBResultData, IntentResultData, TerminalCommandResultData, HiddenTerminalCommandResultData, TerminalSessionScreenResultData, MusicPlaybackResultData,
    FilePartContentData, FileApplyResultData, WorkflowListResultData, WorkflowResultData, WorkflowDetailResultData,
    ChatServiceStartResultData, ChatCreationResultData, ChatListResultData, ChatFindResultData, CurrentChatRuntimeStateResultData, GlobalChatRuntimeStateResultData,
    ChatSwitchResultData, ChatTitleUpdateResultData, ChatDeleteResultData, MessageSendResultData, MemoryQueryResultData, MemoryLinkResultData, MemoryLinkQueryResultData, GrepResultData,
    ChatMessagesResultData, CharacterCardListResultData,
    EnvironmentVariableReadResultData, EnvironmentVariableWriteResultData,
    SandboxPackageResultItem, SandboxPackagesResultData, SandboxPackageUpdateResultData,
    McpRestartLogPluginResultItem, McpRestartWithLogsResultData,
    SpeechServicesConfigResultData, SpeechServicesUpdateResultData,
    SandboxScriptExecutionResultData,
    ModelConfigsResultData, ModelConfigCreateResultData, ModelConfigUpdateResultData, ModelConfigDeleteResultData,
    FunctionModelConfigsResultData, FunctionModelConfigResultData, FunctionModelBindingResultData, ModelConfigConnectionTestResultData
} from './results';

/**
 * Maps tool names to their result data types
 */
export interface ToolResultMap {
    // File operations
    'list_files': DirectoryListingData;
    'read_file': FileContentData;
    'read_file_part': FilePartContentData;
    'read_file_full': FileContentData;
    'read_file_binary': BinaryFileContentData;

    'write_file': FileOperationData;
    'delete_file': FileOperationData;
    'file_exists': FileExistsData;
    'move_file': FileOperationData;
    'copy_file': FileOperationData;
    'make_directory': FileOperationData;
    'find_files': FindFilesResultData;
    'grep_code': GrepResultData;
    'grep_context': GrepResultData;
    'file_info': FileInfoData;
    'zip_files': FileOperationData;
    'unzip_files': FileOperationData;
    'open_file': FileOperationData;
    'share_file': FileOperationData;
    'download_file': FileOperationData;
    'apply_file': FileApplyResultData;
    'create_file': FileApplyResultData;
    'edit_file': FileApplyResultData;

    // Network operations
    'http_request': HttpResponseData;
    'visit_web': VisitWebResultData;
    'browser_click': string;
    'browser_close': string;
    'browser_close_all': string;
    'browser_console_messages': string;
    'browser_drag': string;
    'browser_evaluate': string;
    'browser_file_upload': string;
    'browser_fill_form': string;
    'browser_handle_dialog': string;
    'browser_hover': string;
    'browser_navigate': string;
    'browser_navigate_back': string;
    'browser_network_requests': string;
    'browser_press_key': string;
    'browser_resize': string;
    'browser_run_code': string;
    'browser_select_option': string;
    'browser_wait_for': string;
    'browser_snapshot': string;
    'browser_take_screenshot': string;
    'browser_type': string;
    'browser_tabs': string;
    'multipart_request': HttpResponseData;
    'manage_cookies': HttpResponseData;

    // System operations
    'sleep': SleepResultData;
    'get_system_setting': SystemSettingData;
    'modify_system_setting': SystemSettingData;
    'toast': string;
    'send_notification': string;
    'install_app': AppOperationData;
    'uninstall_app': AppOperationData;
    'list_installed_apps': AppListData;
    'start_app': AppOperationData;
    'stop_app': AppOperationData;
    'device_info': DeviceInfoResultData;
    'get_notifications': NotificationData;
    'get_device_location': LocationData;
    'request_bluetooth_permission': string;
    'get_bluetooth_state': BluetoothStateData;
    'request_enable_bluetooth': string;
    'list_bluetooth_bonded_devices': BluetoothBondedDevicesData;
    'scan_bluetooth_devices': BluetoothScanResultData;
    'bluetooth_connect': BluetoothSessionData;
    'bluetooth_listen': BluetoothSessionData;
    'bluetooth_accept': BluetoothSessionData;
    'bluetooth_send': BluetoothTransferData;
    'bluetooth_read': BluetoothReadData;
    'bluetooth_send_and_read': BluetoothReadData;
    'bluetooth_close': string;
    'bluetooth_ble_connect': BluetoothSessionData;
    'bluetooth_ble_discover_services': BluetoothBleServicesData;
    'bluetooth_ble_read_characteristic': BluetoothReadData;
    'bluetooth_ble_write_characteristic': BluetoothTransferData;
    'bluetooth_ble_write_and_read_characteristic': BluetoothReadData;
    'bluetooth_ble_subscribe_characteristic': BluetoothTransferData;
    'bluetooth_ble_read_notifications': BluetoothBleNotificationData;
    'read_environment_variable': EnvironmentVariableReadResultData;
    'write_environment_variable': EnvironmentVariableWriteResultData;
    'list_sandbox_packages': SandboxPackagesResultData;
    'set_sandbox_package_enabled': SandboxPackageUpdateResultData;
    'execute_sandbox_script_direct': SandboxScriptExecutionResultData;
    'restart_mcp_with_logs': McpRestartWithLogsResultData;
    'get_speech_services_config': SpeechServicesConfigResultData;
    'set_speech_services_config': SpeechServicesUpdateResultData;
    'list_model_configs': ModelConfigsResultData;
    'create_model_config': ModelConfigCreateResultData;
    'update_model_config': ModelConfigUpdateResultData;
    'delete_model_config': ModelConfigDeleteResultData;
    'list_function_model_configs': FunctionModelConfigsResultData;
    'get_function_model_config': FunctionModelConfigResultData;
    'set_function_model_config': FunctionModelBindingResultData;
    'test_model_config_connection': ModelConfigConnectionTestResultData;
    'trigger_tasker_event': string;

    // UI operations
    'get_page_info': UIPageResultData;
    'capture_screenshot': string;
    'click_element': UIActionResultData;
    'tap': UIActionResultData;
    'set_input_text': UIActionResultData;
    'press_key': UIActionResultData;
    'swipe': UIActionResultData;
    'combined_operation': CombinedOperationResultData;
    'run_ui_subagent': AutomationExecutionResultData;

    // Calculator operations
    'calculate': CalculationResultData;

    // Package operations
    'use_package': string;
    'query_memory': MemoryQueryResultData;

    // FFmpeg operations
    'ffmpeg_execute': FFmpegResultData;
    'ffmpeg_info': FFmpegResultData;
    'ffmpeg_convert': FFmpegResultData;

    // ADB operations
    'execute_shell': ADBResultData;

    // Intent operations
    'execute_intent': IntentResultData;
    'send_broadcast': IntentResultData;

    // Terminal operations
    'execute_terminal': TerminalCommandResultData;
    'execute_in_terminal_session_streaming': TerminalCommandResultData;
    'execute_hidden_terminal_command': HiddenTerminalCommandResultData;
    'get_terminal_session_screen': TerminalSessionScreenResultData;

    // Music playback operations
    'music_play': MusicPlaybackResultData;
    'music_play_queue': MusicPlaybackResultData;
    'music_pause': MusicPlaybackResultData;
    'music_resume': MusicPlaybackResultData;
    'music_stop': MusicPlaybackResultData;
    'music_seek': MusicPlaybackResultData;
    'music_set_volume': MusicPlaybackResultData;
    'music_status': MusicPlaybackResultData;

    // Workflow operations
    'get_all_workflows': WorkflowListResultData;
    'create_workflow': WorkflowDetailResultData;
    'get_workflow': WorkflowDetailResultData;
    'update_workflow': WorkflowDetailResultData;
    'patch_workflow': WorkflowDetailResultData;
    'delete_workflow': string;
    'trigger_workflow': string;

    // Chat Manager operations
    'start_chat_service': ChatServiceStartResultData;
    'create_new_chat': ChatCreationResultData;
    'list_chats': ChatListResultData;
    'find_chat': ChatFindResultData;
    'get_current_chat_runtime_state': CurrentChatRuntimeStateResultData;
    'get_global_chat_runtime_state': GlobalChatRuntimeStateResultData;
    'switch_chat': ChatSwitchResultData;
    'update_chat_title': ChatTitleUpdateResultData;
    'delete_chat': ChatDeleteResultData;

    'send_message_to_ai': MessageSendResultData;
    'send_message_to_ai_streaming': MessageSendResultData;
    'list_character_cards': CharacterCardListResultData;
    'get_chat_messages': ChatMessagesResultData;
    'get_chat_messages_range': ChatMessagesResultData;

    // Memory operations
    'link_memories': MemoryLinkResultData;
    'query_memory_links': MemoryLinkQueryResultData;
} 
