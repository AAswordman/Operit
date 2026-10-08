package com.ai.assistance.operit.ui.features.chat.webview.workspace.text

internal const val WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES = 10L * 1024 * 1024

/** 元数据超出上限时不调用读取，避免完整内容进入内存和编辑器。 */
internal fun <T : Any> readWorkspaceTextWithinLimit(size: Long, readText: () -> T): T? {
    if (size !in 0L..WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES) return null
    return readText()
}
