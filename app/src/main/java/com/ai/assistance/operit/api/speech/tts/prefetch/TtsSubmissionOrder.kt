package com.ai.assistance.operit.api.speech.tts.prefetch

import kotlinx.coroutines.CompletableDeferred

/** 清理文本和初始化服务会挂起，先预订顺序，实际入队后再放行下一次提交。 */
internal class TtsSubmissionOrder {
    private val lock = Any()
    private var generation = 0L
    private var accepting = true
    private var tail: CompletableDeferred<Unit>? = null

    inner class Turn internal constructor(
        private val token: Long,
        private val previous: CompletableDeferred<Unit>?,
        private val submitted: CompletableDeferred<Unit>,
    ) {
        suspend fun awaitTurn(): Boolean {
            previous?.await()
            return synchronized(lock) { accepting && token == generation }
        }

        fun markSubmitted() {
            submitted.complete(Unit)
        }

        fun failIfCurrent(): Boolean = synchronized(lock) {
            if (!accepting || token != generation) return@synchronized false
            stop()
            true
        }
    }

    fun reserve(interrupt: Boolean): Turn = synchronized(lock) {
        if (interrupt) {
            generation++
            accepting = true
            tail = null
        }
        val submitted = CompletableDeferred<Unit>()
        Turn(generation, tail, submitted).also { tail = submitted }
    }

    fun stop() = synchronized(lock) {
        generation++
        accepting = false
        tail?.complete(Unit)
        tail = null
    }
}
