package com.ai.assistance.operit.core.tools.javascript

internal object JsTimeoutConfig {
    const val MAIN_TIMEOUT_SECONDS = 1800L
    const val PRE_TIMEOUT_LEAD_SECONDS = 5L
    const val SCRIPT_TIMEOUT_MS = MAIN_TIMEOUT_SECONDS * 1000L
    const val TOOL_CALL_TIMEOUT_MS = SCRIPT_TIMEOUT_MS
}
