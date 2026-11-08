package com.ai.assistance.operit.core.chat.tools.timing

import android.os.SystemClock
import com.ai.assistance.operit.core.chat.tools.timing.ToolCallExecution
import java.util.UUID

/** 只记录实际执行区间，通过类型化事件交给消息持有者，不写入文本流。 */
internal class ToolExecutionTiming(
    private val count: Int,
    private val publish: suspend (ToolCallExecution) -> Unit,
    private val callOffset: Int = 0,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    companion object {
        val clockId: String = UUID.randomUUID().toString()
    }
    private val lock = Any()
    private val records = Array(count) { ToolCallExecution(callOffset + it, clockId) }

    suspend fun queued() {
        records.forEach { publish(it) }
    }

    suspend fun start(index: Int) {
        val record = synchronized(lock) {
            records[index].copy(startedMs = now()).also { records[index] = it }
        }
        publish(record)
    }

    /** 结束时立刻固定毫秒数，原始顺序的结果队列不会延长执行耗时。 */
    suspend fun completed(index: Int) {
        val record = synchronized(lock) {
            val current = records[index]
            val end = now()
            val duration = current.startedMs?.let { (end - it).coerceAtLeast(0L) } ?: 0L
            current.copy(durationMs = duration, endedMs = end).also { records[index] = it }
        }
        publish(record)
    }

    suspend fun finish() {
        val stopped = synchronized(lock) {
            val end = now()
            records.indices.filter { records[it].durationMs == null }.map { index ->
                records[index].copy(endedMs = end, stopped = true).also { records[index] = it }
            }
        }
        stopped.forEach { publish(it) }
    }
}
