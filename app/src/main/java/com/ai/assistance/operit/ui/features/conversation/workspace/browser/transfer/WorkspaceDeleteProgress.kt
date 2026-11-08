package com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer

import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserClipboardItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 只按所选项目统计，目录内部没有上报时不能把项目比例冒充文件或字节进度。 */
internal data class WorkspaceDeleteProgress(
    val target: FileBrowserClipboardItem,
    val completedItems: Int,
    val totalItems: Int,
) {
    val fraction: Float?
        get() = when {
            completedItems == totalItems -> 1f
            totalItems > 1 -> completedItems.toFloat() / totalItems
            else -> null
        }
}

/** 删除动作由既有文件工具执行；这里只管理进度、逐项成功通知和停止后续项。 */
internal class WorkspaceDeleteOperation(
    private val deleteItem: suspend (FileBrowserClipboardItem) -> Unit,
) {
    private val state = MutableStateFlow<WorkspaceDeleteProgress?>(null)
    val progress = state.asStateFlow()

    suspend fun run(
        targets: List<FileBrowserClipboardItem>,
        onDeleted: suspend (FileBrowserClipboardItem) -> Unit = {},
    ) {
        if (targets.isEmpty()) return
        val executionContext = currentCoroutineContext()
        try {
            for ((index, target) in targets.withIndex()) {
                executionContext.ensureActive()
                val current = WorkspaceDeleteProgress(target, index, targets.size)
                state.value = current
                deleteItem(target)
                state.value = current.copy(completedItems = index + 1)
                // 当前工具返回成功才更新选择；失败不计入完成，也不开始下一个项目。
                onDeleted(target)
                executionContext.ensureActive()
            }
        } finally {
            state.value = null
        }
    }
}
