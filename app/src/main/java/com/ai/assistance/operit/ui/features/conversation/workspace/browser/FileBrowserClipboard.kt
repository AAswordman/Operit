package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class FileBrowserClipboardItem(
    val path: String,
    val environment: String?,
    val cut: Boolean,
    val isDirectory: Boolean,
)

/** 剪贴板保存完整选择及各自来源，跨目录粘贴和部分成功时都不会丢失剩余项。 */
internal object FileBrowserClipboard {
    private val state = MutableStateFlow<List<FileBrowserClipboardItem>>(emptyList())
    val content = state.asStateFlow()

    fun set(item: FileBrowserClipboardItem) = set(listOf(item))

    fun set(items: List<FileBrowserClipboardItem>) {
        state.value = items.distinctBy { (it.environment ?: "android") to it.path }
    }

    fun clear(item: FileBrowserClipboardItem) {
        state.update { current -> current.filterNot { it == item } }
    }
}