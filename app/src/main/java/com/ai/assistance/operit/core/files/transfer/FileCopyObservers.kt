package com.ai.assistance.operit.core.files.transfer

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class FileCopyObserver(
    val onProgress: (FileCopyProgress) -> Unit,
    val checkCancelled: () -> Unit,
)

/** 工具执行跨越同步入口和 IO 协程；用一次性令牌关联进度，避免并行粘贴串到同一个全局状态。 */
internal object FileCopyObservers {
    const val PARAMETER = "__workspace_copy_id"
    private val observers = ConcurrentHashMap<String, FileCopyObserver>()

    fun register(observer: FileCopyObserver): String = UUID.randomUUID().toString().also { observers[it] = observer }

    fun find(id: String?): FileCopyObserver? = id?.let(observers::get)

    fun remove(id: String) { observers.remove(id) }
}