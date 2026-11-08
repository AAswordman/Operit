package com.ai.assistance.operit.core.tools.javascript

internal object JsTimeoutConfig {
    const val MAIN_TIMEOUT_SECONDS = 1800L
    const val PRE_TIMEOUT_LEAD_SECONDS = 5L
    const val SCRIPT_TIMEOUT_MS = MAIN_TIMEOUT_SECONDS * 1000L
    const val TOOL_CALL_TIMEOUT_MS = SCRIPT_TIMEOUT_MS
    const val TOOLPKG_IPC_TIMEOUT_SECONDS = 15L
    // 页面建树与首次加载需要结束边界，交互触发的长任务仍沿用调用方设置。
    const val COMPOSE_DSL_LOAD_TIMEOUT_SECONDS = 30L
}
