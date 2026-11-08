package com.ai.assistance.operit.core.chat.message

import java.util.concurrent.atomic.AtomicLong

/**
 * 为聊天消息分配单调递增的时间戳。
 *
 * 项目里大量逻辑仍然把 timestamp 当作消息唯一标识使用，
 * 因此这里需要保证同一进程内新建消息不会拿到重复值。
 */
object ChatMessageTimestampAllocator {
    private val lastIssuedTimestamp = AtomicLong(0L)

    fun next(baseTimestamp: Long = System.currentTimeMillis()): Long {
        while (true) {
            val previous = lastIssuedTimestamp.get()
            val candidate = maxOf(baseTimestamp, previous + 1L)
            if (lastIssuedTimestamp.compareAndSet(previous, candidate)) {
                return candidate
            }
        }
    }

    fun observe(timestamp: Long) {
        while (true) {
            val previous = lastIssuedTimestamp.get()
            if (timestamp <= previous) {
                return
            }
            if (lastIssuedTimestamp.compareAndSet(previous, timestamp)) {
                return
            }
        }
    }
}
