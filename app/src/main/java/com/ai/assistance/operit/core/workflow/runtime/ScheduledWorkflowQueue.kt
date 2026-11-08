package com.ai.assistance.operit.core.workflow.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 定时任务共用执行通道，避免同时占用聊天/工具运行环境；等待不创建失败记录。 */
internal object ScheduledWorkflowQueue {
    private val executionMutex = Mutex()
    private val waitingCounts = mutableMapOf<String, Int>()
    private val waiting = MutableStateFlow<Set<String>>(emptySet())
    val waitingWorkflowIds = waiting.asStateFlow()

    private fun changeWaiting(workflowId: String, delta: Int) = synchronized(waitingCounts) {
        val count = (waitingCounts[workflowId] ?: 0) + delta
        if (count > 0) waitingCounts[workflowId] = count else waitingCounts.remove(workflowId)
        waiting.value = waitingCounts.keys.toSet()
    }

    suspend fun <T> run(workflowId: String, block: suspend () -> T): T {
        var queued = true
        changeWaiting(workflowId, 1)
        try {
            return executionMutex.withLock {
                changeWaiting(workflowId, -1)
                queued = false
                block()
            }
        } finally {
            // 等待中被 WorkManager 取消时，也要移除排队标记。
            if (queued) changeWaiting(workflowId, -1)
        }
    }
}
