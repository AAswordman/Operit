package com.ai.assistance.operit.core.tools.packTool

internal const val TOOLPKG_RUNTIME_COMPOSE_DSL = "compose_dsl"

internal const val TOOLPKG_EVENT_APPLICATION_ON_CREATE = "application_on_create"
internal const val TOOLPKG_EVENT_APPLICATION_ON_FOREGROUND = "application_on_foreground"
internal const val TOOLPKG_EVENT_APPLICATION_ON_BACKGROUND = "application_on_background"
internal const val TOOLPKG_EVENT_APPLICATION_ON_LOW_MEMORY = "application_on_low_memory"
internal const val TOOLPKG_EVENT_APPLICATION_ON_TRIM_MEMORY = "application_on_trim_memory"
internal const val TOOLPKG_EVENT_APPLICATION_ON_TERMINATE = "application_on_terminate"
internal const val TOOLPKG_EVENT_ACTIVITY_ON_CREATE = "activity_on_create"
internal const val TOOLPKG_EVENT_ACTIVITY_ON_START = "activity_on_start"
internal const val TOOLPKG_EVENT_ACTIVITY_ON_RESUME = "activity_on_resume"
internal const val TOOLPKG_EVENT_ACTIVITY_ON_PAUSE = "activity_on_pause"
internal const val TOOLPKG_EVENT_ACTIVITY_ON_STOP = "activity_on_stop"
internal const val TOOLPKG_EVENT_ACTIVITY_ON_DESTROY = "activity_on_destroy"
internal const val TOOLPKG_EVENT_MESSAGE_PROCESSING = "toolpkg_message_processing"
internal const val TOOLPKG_EVENT_XML_RENDER = "toolpkg_xml_render"
internal const val TOOLPKG_EVENT_INPUT_MENU_TOGGLE = "toolpkg_input_menu_toggle"
internal const val TOOLPKG_EVENT_CHAT_INPUT = "toolpkg_chat_input"
internal const val TOOLPKG_EVENT_CHAT_VIEW = "toolpkg_chat_view"
internal const val TOOLPKG_EVENT_CHAT_MESSAGE = "toolpkg_chat_message"
internal const val TOOLPKG_EVENT_CHAT_RUNTIME_STATE = "toolpkg_chat_runtime_state"
internal const val TOOLPKG_EVENT_NAVIGATION_ENTRY_ACTION = "toolpkg_navigation_entry_action"
internal const val TOOLPKG_EVENT_TOOL_LIFECYCLE = "toolpkg_tool_lifecycle"
internal const val TOOLPKG_EVENT_PROMPT_INPUT = "toolpkg_prompt_input"
internal const val TOOLPKG_EVENT_PROMPT_HISTORY = "toolpkg_prompt_history"
internal const val TOOLPKG_EVENT_PROMPT_ESTIMATE_HISTORY = "toolpkg_prompt_estimate_history"
internal const val TOOLPKG_EVENT_SYSTEM_PROMPT_COMPOSE = "toolpkg_system_prompt_compose"
internal const val TOOLPKG_EVENT_TOOL_PROMPT_COMPOSE = "toolpkg_tool_prompt_compose"
internal const val TOOLPKG_EVENT_PROMPT_FINALIZE = "toolpkg_prompt_finalize"
internal const val TOOLPKG_EVENT_PROMPT_ESTIMATE_FINALIZE = "toolpkg_prompt_estimate_finalize"
internal const val TOOLPKG_EVENT_SUMMARY_GENERATE = "toolpkg_summary_generate"
internal const val TOOLPKG_EVENT_AI_PROVIDER_LIST_MODELS = "toolpkg_ai_provider_list_models"
internal const val TOOLPKG_EVENT_AI_PROVIDER_SEND_MESSAGE = "toolpkg_ai_provider_send_message"
internal const val TOOLPKG_EVENT_AI_PROVIDER_TEST_CONNECTION = "toolpkg_ai_provider_test_connection"
internal const val TOOLPKG_EVENT_AI_PROVIDER_CALCULATE_INPUT_TOKENS =
    "toolpkg_ai_provider_calculate_input_tokens"

internal const val TOOLPKG_REGISTRATION_TOOLBOX_UI_MODULE = "registerToolPkgToolboxUiModule"
internal const val TOOLPKG_REGISTRATION_UI_ROUTE = "registerToolPkgUiRoute"
internal const val TOOLPKG_REGISTRATION_NAVIGATION_ENTRY = "registerToolPkgNavigationEntry"
internal const val TOOLPKG_REGISTRATION_DESKTOP_WIDGET = "registerToolPkgDesktopWidget"
internal const val TOOLPKG_REGISTRATION_APP_LIFECYCLE_HOOK = "registerToolPkgAppLifecycleHook"
internal const val TOOLPKG_REGISTRATION_MESSAGE_PROCESSING_PLUGIN =
    "registerToolPkgMessageProcessingPlugin"
internal const val TOOLPKG_REGISTRATION_XML_RENDER_PLUGIN =
    "registerToolPkgXmlRenderPlugin"
internal const val TOOLPKG_REGISTRATION_INPUT_MENU_TOGGLE_PLUGIN =
    "registerToolPkgInputMenuTogglePlugin"
internal const val TOOLPKG_REGISTRATION_CHAT_INPUT_HOOK =
    "registerToolPkgChatInputHook"
internal const val TOOLPKG_REGISTRATION_CHAT_VIEW_HOOK =
    "registerToolPkgChatViewHook"
internal const val TOOLPKG_REGISTRATION_CHAT_MESSAGE_HOOK =
    "registerToolPkgChatMessageHook"
internal const val TOOLPKG_REGISTRATION_CHAT_RUNTIME_STATE_HOOK =
    "registerToolPkgChatRuntimeStateHook"
internal const val TOOLPKG_REGISTRATION_TOOL_LIFECYCLE_HOOK =
    "registerToolPkgToolLifecycleHook"
internal const val TOOLPKG_REGISTRATION_PROMPT_INPUT_HOOK =
    "registerToolPkgPromptInputHook"
internal const val TOOLPKG_REGISTRATION_PROMPT_HISTORY_HOOK =
    "registerToolPkgPromptHistoryHook"
internal const val TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_HISTORY_HOOK =
    "registerToolPkgPromptEstimateHistoryHook"
internal const val TOOLPKG_REGISTRATION_SYSTEM_PROMPT_COMPOSE_HOOK =
    "registerToolPkgSystemPromptComposeHook"
internal const val TOOLPKG_REGISTRATION_TOOL_PROMPT_COMPOSE_HOOK =
    "registerToolPkgToolPromptComposeHook"
internal const val TOOLPKG_REGISTRATION_PROMPT_FINALIZE_HOOK =
    "registerToolPkgPromptFinalizeHook"
internal const val TOOLPKG_REGISTRATION_PROMPT_ESTIMATE_FINALIZE_HOOK =
    "registerToolPkgPromptEstimateFinalizeHook"
internal const val TOOLPKG_REGISTRATION_SUMMARY_GENERATE_HOOK =
    "registerToolPkgSummaryGenerateHook"
internal const val TOOLPKG_REGISTRATION_AI_PROVIDER =
    "registerToolPkgAiProvider"

internal const val TOOLPKG_NAV_SURFACE_TOOLBOX = "toolbox"
internal const val TOOLPKG_NAV_SURFACE_MAIN_SIDEBAR_PLUGINS = "main_sidebar_plugins"

internal fun buildToolPkgRouteId(
    containerPackageName: String,
    uiRouteId: String
): String = "toolpkg:$containerPackageName:ui:$uiRouteId"
