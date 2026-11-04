package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.withEventChannel
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 在卡片引用展开后重新记录修订范围，避免用模型原文坐标裁剪展开后的卡片。 */
internal fun Stream<String>.expandCardReferences(resolve: (String) -> String?): Stream<String> {
    val source = this
    val events = MutableSharedStream<TextStreamEvent>(replay = Int.MAX_VALUE)
    return stream<String> {
        coroutineScope {
            val carrier = source as? TextStreamEventCarrier
            val tracker = TextStreamRevisionTracker()
            val stateMutex = Mutex()
            var decoder = CardReferenceExpander(resolve)
            val displayed = StringBuilder()
            var consumedEvents = 0
            var emittedLength = 0
            var retainedStart = 0
            var completed = false
            val collectionJob = coroutineContext.job
            val stopping = AtomicBoolean(false)

            suspend fun publish(content: String) {
                if (content.isEmpty()) return
                displayed.append(content)
                emittedLength += content.length
                emit(content)
            }

            suspend fun restore() {
                // 只在模型修订时重建解码器；正常增量始终只解码新到达的尾部。
                decoder = CardReferenceExpander(resolve)
                val replacement = decoder.push(tracker.currentContent().toString())
                if (displayed.toString() == replacement) return
                val id = "card-revision-${UUID.randomUUID()}"
                events.emit(TextStreamEvent(TextStreamEventType.SAVEPOINT, id, retainedStart))
                events.emit(
                    TextStreamEvent(
                        TextStreamEventType.ROLLBACK,
                        id,
                        retainedStart,
                        emittedLength - retainedStart,
                    )
                )
                retainedStart = emittedLength
                displayed.setLength(0)
                publish(replacement)
            }

            suspend fun drainEvents() {
                val history = carrier?.eventChannel?.replayCache.orEmpty()
                while (consumedEvents < history.size) {
                    val event = history[consumedEvents++]
                    when (event.eventType) {
                        TextStreamEventType.SAVEPOINT ->
                            tracker.savepoint(event.id, event.contentLength)
                        TextStreamEventType.ROLLBACK -> {
                            tracker.rollback(event.id, event.contentLength, event.discardedLength)
                            restore()
                        }
                    }
                }
            }

            val revisions = carrier?.let {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        it.eventChannel.collect {
                            stateMutex.withLock { drainEvents() }
                        }
                    } catch (error: CancellationException) {
                        if (collectionJob.isActive && !stopping.get()) collectionJob.cancel(error)
                        throw error
                    }
                }
            }
            try {
                source.collect { chunk ->
                    stateMutex.withLock {
                        drainEvents()
                        val previousLength = tracker.currentContent().length
                        val visible = tracker.append(chunk)
                        publish(decoder.push(visible.substring(previousLength)))
                    }
                }
                completed = true
            } finally {
                withContext(NonCancellable) {
                    stopping.set(true)
                    revisions?.cancelAndJoin()
                    stateMutex.withLock {
                        // 文本结束或取消后，仍读取生产者最终修订；不等待事件通道关闭。
                        drainEvents()
                        if (completed) publish(decoder.finish())
                    }
                }
            }
        }
    }.withEventChannel(events)
}
