package com.ai.assistance.operit.ui.features.conversation.chat.foundation.stream

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.core.chat.protocol.RevisableTextDisplayState
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamRollbackPrefix
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private class RollbackTailStream(
    private val upstream: Stream<String>,
    override val rollbackPrefix: String,
) : Stream<String> by upstream, StreamRollbackPrefix

@Composable
fun rememberRevisableTextStream(sourceStream: Stream<String>?): Stream<String>? {
    if (sourceStream == null) return null
    val carrier = sourceStream as? TextStreamEventCarrier
    var displayStream by remember(sourceStream) {
        mutableStateOf<Stream<String>?>(MutableSharedStream(replay = Int.MAX_VALUE))
    }

    LaunchedEffect(sourceStream) {
        // Long protocol/XML parsing and rollback projection must never run on AndroidUiDispatcher.
        withContext(Dispatchers.Default) {
            val state = RevisableTextDisplayState()
            val stateMutex = Mutex()
            var currentOutputStream = MutableSharedStream<String>(replay = Int.MAX_VALUE)
            withContext(Dispatchers.Main.immediate) { displayStream = currentOutputStream }

            fun replacement(prefix: String): RollbackTailStream {
                val previous = currentOutputStream
                currentOutputStream = MutableSharedStream(replay = Int.MAX_VALUE)
                previous.resetReplayCache()
                return RollbackTailStream(currentOutputStream, prefix)
            }

            coroutineScope {
                val eventJob = carrier?.let { eventCarrier ->
                    launch {
                        eventCarrier.eventChannel.collect { event ->
                            val update = stateMutex.withLock { state.apply(event)?.let { replacement(it) } }
                            if (update != null) withContext(Dispatchers.Main.immediate) { displayStream = update }
                        }
                    }
                }
                try {
                    sourceStream.collect { chunk ->
                        val (output, visibleChunk) = stateMutex.withLock { currentOutputStream to state.append(chunk) }
                        if (visibleChunk.isNotEmpty()) output.emit(visibleChunk)
                    }
                } finally {
                    withContext(NonCancellable) {
                        eventJob?.cancelAndJoin()
                        val (update, output, tail) = stateMutex.withLock {
                            val update = state.reconcile(carrier?.eventChannel?.replayCache.orEmpty())?.let { replacement(it) }
                            Triple(update, currentOutputStream, state.finish())
                        }
                        if (update != null) withContext(Dispatchers.Main.immediate) { displayStream = update }
                        if (tail.isNotEmpty()) output.emit(tail)
                        // Keep completed replay available until the static message takes over.
                        (output as? MutableSharedStreamImpl<String>)?.close()
                    }
                }
            }
        }
    }
    return displayStream
}
