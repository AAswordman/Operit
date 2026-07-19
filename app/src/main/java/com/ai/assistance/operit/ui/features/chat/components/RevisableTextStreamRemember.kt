package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun rememberRevisableTextStream(sourceStream: Stream<String>?): Stream<String>? {
    val carrier = sourceStream as? TextStreamEventCarrier ?: return sourceStream

    var displayStream by remember(sourceStream) {
        mutableStateOf<Stream<String>?>(MutableSharedStream(replay = Int.MAX_VALUE))
    }

    LaunchedEffect(sourceStream) {
        val tracker = TextStreamRevisionTracker()
        val stateMutex = Mutex()
        var currentDisplayStream = MutableSharedStream<String>(replay = Int.MAX_VALUE)
        displayStream = currentDisplayStream

        coroutineScope {
            val eventJob = launch {
                carrier.eventChannel.collect { event ->
                    when (event.eventType) {
                        TextStreamEventType.SAVEPOINT -> {
                            stateMutex.withLock {
                                tracker.savepoint(event.id)
                            }
                        }

                        TextStreamEventType.ROLLBACK -> {
                            val snapshot =
                                stateMutex.withLock {
                                    tracker.rollback(event.id)?.toString()
                                } ?: return@collect
                            val previousDisplayStream = currentDisplayStream
                            val replacementStream =
                                MutableSharedStream<String>(replay = Int.MAX_VALUE)
                            if (snapshot.isNotEmpty()) {
                                replacementStream.emit(snapshot)
                            }
                            currentDisplayStream = replacementStream
                            displayStream = replacementStream
                            previousDisplayStream.resetReplayCache()
                        }
                    }
                }
            }

            try {
                sourceStream.collect { chunk ->
                    val activeDisplayStream =
                        stateMutex.withLock {
                            tracker.append(chunk)
                            currentDisplayStream
                        }
                    activeDisplayStream.emit(chunk)
                }
            } finally {
                eventJob.cancelAndJoin()
                currentDisplayStream.resetReplayCache()
                displayStream = null
            }
        }
    }

    return displayStream
}
