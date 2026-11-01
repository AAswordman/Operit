# 缺陷、崩溃与性能修复

- [硅基流动音频格式、请求重试与临时错误显示](voice-input-audio-retry/index.md)

修复既有行为、兼容性、崩溃和资源开销问题。

[返回 TODO 总览](../README.md)

## 计划索引

- [密钥池错误的实际请求范围](key-pool-error-scope/index.md)

- [工作区复制内存与文件操作进度](workspace-copy-streaming-progress/index.md)

- [流式气泡停更修复](streaming-bubble-freeze/index.md)
- [提示、插件配置入口与 Waifu 首次展示](ux-stream-ui-followup/index.md)
- [语音条 WebView 挂载焦点修复](toolpkg-webview-focus/index.md) · `toolpkg-webview-focus`

- [包管理四标签等宽铺满](package-tabs-full-width/index.md)

- [流式协议元数据隐藏](streaming-protocol-meta/index.md) · `streaming-protocol-meta`
- [模型上下文协议手动启动与元数据兼容](mcp-manual-start/index.md) · `mcp-manual-start`
- [界面与媒体交互修复](interface-media-polish/index.md) · `interface-media-polish`

- [Antigravity OAuth 修复、移除 Vertex AI 登录与指纹伪装](antigravity_oauth_repair_20260928/index.md) · `antigravity_oauth_repair_20260928`
- [API Key 首次引导修复](api_key_onboarding/index.md) · `api_key_onboarding`
- [Calculator Qualified API Repair](calculator_qualified_api_repair_20260809/index.md) · `calculator_qualified_api_repair_20260809`
- [聊天角色卡 UUID 绑定回退](character_card_uuid_binding_rollback_20260805/index.md) · `character_card_uuid_binding_rollback_20260805`
- [会话删除状态与锁定提示修复](chat-delete-state/index.md) · `chat-delete-state`
- [聊天历史加载与分块渲染性能](chat-history-render-performance/index.md) · `chat-history-render-performance`
- [长聊天卡片的单层滚动渲染](chat-single-scroll/index.md) · `chat-single-scroll`
- [会话累计 Token 统计恢复](chat-token-stat-restoration/index.md) · `chat-token-stat-restoration`
- [超大聊天消息读取修复](chat_large_message_io_20260806/index.md) · `chat_large_message_io_20260806`
- [Claude 工具结果配对修复](claude_tool_result_pairing_repair_20260907/index.md) · `claude_tool_result_pairing_repair_20260907`
- [Codex Auth Keystore Restore Crash](codex_auth_keystore_restore_20260904/index.md) · `codex_auth_keystore_restore_20260904`
- [崩溃报告自动换行开关反馈](crash-report-wrap-feedback/index.md)
- [DeepSeek Responses Reasoning Replay](deepseek_responses_reasoning_replay_20260907/index.md) · `deepseek_responses_reasoning_replay_20260907`
- [悬浮窗 NaN 崩溃修复](floating_window_nan_crash_20260917/index.md) · `floating_window_nan_crash_20260917`
- [Gemini tool history and thinking repair](gemini_tool_history_and_thinking_repair_20260907/index.md) · `gemini_tool_history_and_thinking_repair_20260907`
- [GitHub 登录浏览器选择](github_login_browser_choice_20260801/index.md) · `github_login_browser_choice_20260801`
- [GitHub OAuth Broker](github_oauth_broker/index.md) · `github_oauth_broker`
- [GitHub OAuth Completion Delivery](github_oauth_completion_delivery/index.md) · `github_oauth_completion_delivery`
- [中断回合统计修复](issue-741-interrupted-turn-stats/index.md) · `issue-741-interrupted-turn-stats`
- [Issue 853 待发送消息队列](issue-853-pending-message-queue/index.md) · `issue-853-pending-message-queue`
- [Issue 854 Android 16 前台通知](issue-854-android16-foreground-notification/index.md) · `issue-854-android16-foreground-notification`
- [Issue 858 MCP 工具参数完整性](issue-858-mcp-tool-argument-integrity/index.md) · `issue-858-mcp-tool-argument-integrity`
- [Issue 767 头像配置持久化修复](issue_767_avatar_config_persistence/index.md) · `issue_767_avatar_config_persistence`
- [Issue 782 Theme Editor Drafts](issue_782_theme_editor_drafts/index.md) · `issue_782_theme_editor_drafts`
- [Issue 782 Character Theme Persistence](issue_782_theme_persistence/index.md) · `issue_782_theme_persistence`
- [Issue 782 Theme Runtime Snapshot](issue_782_theme_runtime_snapshot/index.md) · `issue_782_theme_runtime_snapshot`
- [Issue 842 Gemini 思考参数传递](issue_842_gemini_thinking_config/index.md) · `issue_842_gemini_thinking_config`
- [QuickJS 执行上下文生命周期](js_engine_lifecycle_20260801/index.md) · `js_engine_lifecycle_20260801`
- [Kotlin 与 TypeScript 桥接接口对齐](kt_ts_bridge_alignment_20260809/index.md) · `kt_ts_bridge_alignment_20260809`
- [长会话发送前性能优化](long-history-send-cost/index.md) · `long-history-send-cost`
- [Incremental Private Publisher Projection](market_private_publisher_incremental_20260728/index.md) · `market_private_publisher_incremental_20260728`
- [Marketplace client-version compatibility](market_version_compatibility_20260805/index.md) · `market_version_compatibility_20260805`
- [市场版本范围下载状态](market_version_download_state/index.md) · `market_version_download_state`
- [记忆工作台选择器与默认空间删除状态](memory-workbench-selectors/index.md)
- [记忆检索相关性与诊断（#1320）](memory-search-1320/01-relevance-and-diagnostics.md) · `memory-search-1320`
- [额外信息注入并发时限](message_insert_parallel_deadline_20260808/index.md) · `message_insert_parallel_deadline_20260808`
- [模型配置默认启用 Tool Call](model_tool_call_default_20260807/index.md) · `model_tool_call_default_20260807`
- [工作流并发、总结与语音条内存排查](oom-workflow-audio/index.md) · `oom-workflow-audio`
- [OpenCode provider current requirements](opencode_provider_current_requirements_20260907/index.md) · `opencode_provider_current_requirements_20260907`
- [工具结果按调用顺序输出](ordered-tool-results/index.md) · `ordered-tool-results`
- [Phone Orientation Layout](phone_orientation_layout_860/index.md) · `phone_orientation_layout_860`
- [插件页面生命周期与聊天草稿](plugin-page-lifecycle-and-drafts/index.md) · `plugin-page-lifecycle-and-drafts`
- [断网中断消息保留修复](preserve_interrupted_ai_output_20260814/index.md) · `preserve_interrupted_ai_output_20260814`
- [Publish Asset Source Selector](publish_asset_source_selector_20260729/index.md) · `publish_asset_source_selector_20260729`
- [移除内置表情与恢复默认](remove-builtin-emoji/index.md) · `remove-builtin-emoji`
- [预编译删除思考正则](remove_thinking_regex_anr_20260913/index.md) · `remove_thinking_regex_anr_20260913`
- [替换资源后的私有文件清理](replaced-resource-cleanup/index.md) · `replaced-resource-cleanup`
- [请求终止错误传播与重试规则](request-error-feedback/index.md) · `request-error-feedback`
- [重试状态与显示设置转场修复](retry-status-settings-transition/index.md) · `retry-status-settings-transition`
- [设置页提醒与协议阅读显示修复](settings-display-feedback/index.md) · `settings-display-feedback`
- [共享流异常收尾契约](shared_stream_failure_observation_20260824/index.md) · `shared_stream_failure_observation_20260824`
- [原始快照导入自动恢复媒体引用](snapshot_media_restore/index.md) · `snapshot_media_restore`
- [Streaming Markdown Cache Invalidation](stream_markdown_cache_invalidation_20260729/index.md) · `stream_markdown_cache_invalidation_20260729`
- [终端与离线资源紧急修复](terminal-resource-hotfix/index.md) · `terminal-resource-hotfix`
- [终端异常退出后的会话回收](terminal_session_auto_recovery_20260808/index.md) · `terminal_session_auto_recovery_20260808`
- [思考 token 边界](thinking-token-boundary/index.md) · `thinking-token-boundary`
- [Third-Party Market Content Updates](third_party_market_content_updates_20260816/index.md) · `third_party_market_content_updates_20260816`
- [Token 统计 Long 迁移](token_statistics_long_20260801/index.md) · `token_statistics_long_20260801`
- [Token Statistics Activity Refresh](token_stats_activity_refresh_20260816/index.md) · `token_stats_activity_refresh_20260816`
- [Token Statistics Legacy Cache Write Semantics](token_stats_legacy_cache_write_20260821/index.md) · `token_stats_legacy_cache_write_20260821`
- [Token Statistics Legacy Identity Runtime Repair](token_stats_legacy_identity_runtime_20260821/index.md) · `token_stats_legacy_identity_runtime_20260821`
- [Token Statistics Mode And Range Linkage](token_stats_mode_range_linkage_20260822/index.md) · `token_stats_mode_range_linkage_20260822`
- [Restore Token Statistics Migration](token_stats_restore_20260821/index.md) · `token_stats_restore_20260821`
- [Token Statistics Success-Only Cleanup](token_stats_success_only_20260816/index.md) · `token_stats_success_only_20260816`
- [工具结果正文与卡片边界](tool-result-content/index.md) · `tool-result-content`
- [工具结果按调用独立限制](tool-result-per-call-limit_20260909/index.md) · `tool-result-per-call-limit_20260909`
- [工具结果大字符串 OOM 定点修复](tool-result-string-oom/index.md) · `tool-result-string-oom`
- [ToolPkg toolCall dispatch repair](toolpkg_api_version_and_toolcall_repair_20260907/index.md) · `toolpkg_api_version_and_toolcall_repair_20260907`
- [ToolPkg 引擎超时与生命周期修复](toolpkg_engine_timeout/index.md) · `toolpkg_engine_timeout`
- [ToolPkg Hook Timeout Toast](toolpkg_hook_timeout_toast_20260807/index.md) · `toolpkg_hook_timeout_toast_20260807`
- [ToolPkg 发布处理](toolpkg_publish_provenance_20260729/index.md) · `toolpkg_publish_provenance_20260729`
- [未匹配工具历史修复](unmatched_tool_history_20260908/index.md) · `unmatched_tool_history_20260908`
- [变体消息持久化事件修复](variant_message_persisted_event_20260908/index.md) · `variant_message_persisted_event_20260908`
- [Weather injection timeout](weather_injection_timeout_20260805/index.md) · `weather_injection_timeout_20260805`
- [工作流启动前异常状态](workflow_pre_execution_failure_status/index.md) · `workflow_pre_execution_failure_status`
- [Workflow Runtime Initialization](workflow_runtime_init_20260731/index.md) · `workflow_runtime_init_20260731`
- [工作流存储稳定性](workflow_storage_stability_20260828/index.md) · `workflow_storage_stability_20260828`
- [工作流定时执行](workflow-scheduling/index.md) · `workflow-scheduling`

- [包工具 JSON 参数诊断](package-proxy-json-diagnostics/index.md)
- [A2UI 编译说明与消息投递](a2ui-generation-delivery/index.md)
- [多工具交错参数完整性](interleaved-tool-call-arguments/index.md)

- [终端非零退出码统一判为失败](terminal-nonzero-exit/index.md)

- [use_package 激活失败状态](use-package-result/index.md)

- [应用升级保留外置扩展安装](upgrade-package-preservation/index.md)

- [GitHub 工具包保留终端失败输出](github-terminal-result/index.md)
- [对话历史长列表滚动重叠](history-scroll-placement/index.md)
- [移植 DSL 与设置页滚动状态修复](scroll-state-preservation/index.md)
- [保留消息复制预览选区与滚动位置](copy-preview-state/index.md)
- [修复浏览器 ref 点击的 WebView 线程访问](browser-ref-main-thread/index.md)

- [工具失败说明去重](tool-error-deduplication/index.md)

- [离线语音试听回调崩溃](offline-tts-callback/index.md)
