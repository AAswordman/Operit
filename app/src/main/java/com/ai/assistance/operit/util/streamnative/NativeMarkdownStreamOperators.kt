package com.ai.assistance.operit.util.streamnative

import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.StreamGroup
import com.ai.assistance.operit.util.stream.StreamLogger
import com.ai.assistance.operit.util.stream.asStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

private fun Int.toMarkdownTypeOrNull(): MarkdownProcessorType? =
    MarkdownProcessorType.entries.getOrNull(this)

private fun Stream<Char>.nativeMarkdownSplitBySession(
    sessionFactory: () -> NativeMarkdownSplitter.Session,
    debugTag: String,
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
    initialContent: String = "",
): Stream<StreamGroup<MarkdownProcessorType?>> {
    val upstream = this

    return object : Stream<StreamGroup<MarkdownProcessorType?>> {
        override val isLocked: Boolean
            get() = upstream.isLocked

        override val bufferedCount: Int
            get() = upstream.bufferedCount

        override suspend fun lock() = upstream.lock()
        override suspend fun unlock() = upstream.unlock()
        override fun clearBuffer() = upstream.clearBuffer()

        override suspend fun collect(collector: StreamCollector<StreamGroup<MarkdownProcessorType?>>) {
            coroutineScope {
                val groupChannel = Channel<StreamGroup<MarkdownProcessorType?>>(Channel.UNLIMITED)

                launch {
                    val session = sessionFactory()
                    val fullContent = StringBuilder(initialContent)
                    val deltaBuffer = StringBuilder()

                    if (initialContent.isNotEmpty()) {
                        // Restore parser state without re-emitting the already rendered rollback prefix.
                        session.push(initialContent)
                    }

                    val mutex = Mutex()
                    val flushMutex = Mutex()

                    var defaultTextChannel: Channel<String>? = null
                    var activePluginChannel: Channel<String>? = null
                    var activeTag: MarkdownProcessorType? = null

                    suspend fun openDefaultChannel() {
                        if (defaultTextChannel == null) {
                            val newChannel = Channel<String>(Channel.UNLIMITED)
                            defaultTextChannel = newChannel
                            val stream = newChannel.consumeAsFlow().asStream()
                            groupChannel.send(StreamGroup(null, stream))
                        }
                    }

                    suspend fun closeDefaultChannel() {
                        defaultTextChannel?.close()
                        defaultTextChannel = null
                    }

                    suspend fun openPluginChannel(tag: MarkdownProcessorType) {
                        val newChannel = Channel<String>(Channel.UNLIMITED)
                        activePluginChannel = newChannel
                        activeTag = tag
                        val stream = newChannel.consumeAsFlow().asStream()
                        groupChannel.send(StreamGroup(tag, stream))
                    }

                    suspend fun closePluginChannel() {
                        activePluginChannel?.close()
                        activePluginChannel = null
                        activeTag = null
                    }

                    suspend fun flushDelta() {
                        flushMutex.withLock {
                            val delta = mutex.withLock {
                                if (deltaBuffer.isEmpty()) {
                                    null
                                } else {
                                    deltaBuffer.toString().also { deltaBuffer.setLength(0) }
                                }
                            } ?: return

                            val segments = session.push(delta)
                            if (segments.isEmpty()) return

                            data class Action(val type: MarkdownProcessorType?, val text: String?)

                            val actions = ArrayList<Action>(segments.size / 3)
                            mutex.withLock {
                                var i = 0
                                while (i + 2 < segments.size) {
                                    val typeOrdinal = segments[i]
                                    val start = segments[i + 1]
                                    val end = segments[i + 2]
                                    i += 3

                                    if (typeOrdinal < 0) {
                                        actions.add(Action(type = null, text = null))
                                        continue
                                    }

                                    val type = typeOrdinal.toMarkdownTypeOrNull() ?: MarkdownProcessorType.PLAIN_TEXT
                                    if (start < 0 || end < 0 || start > end || end > fullContent.length) {
                                        continue
                                    }

                                    actions.add(Action(type = type, text = fullContent.substring(start, end)))
                                }
                            }

                            for (action in actions) {
                                if (action.type == null) {
                                    closeDefaultChannel()
                                    closePluginChannel()
                                    continue
                                }

                                val type = action.type
                                val text = action.text ?: ""

                                if (type == MarkdownProcessorType.PLAIN_TEXT) {
                                    if (activePluginChannel != null) {
                                        closePluginChannel()
                                    }
                                    openDefaultChannel()
                                    if (text.isNotEmpty()) {
                                        defaultTextChannel?.send(text)
                                    }
                                } else {
                                    if (defaultTextChannel != null) {
                                        closeDefaultChannel()
                                    }
                                    if (activeTag == null) {
                                        openPluginChannel(type)
                                    } else if (activeTag != type) {
                                        closePluginChannel()
                                        openPluginChannel(type)
                                    }
                                    if (text.isNotEmpty()) {
                                        activePluginChannel?.send(text)
                                    }
                                }
                            }
                        }
                    }

                    val flushJob =
                        if (flushIntervalMs != null && flushIntervalMs > 0) {
                            launch {
                                while (true) {
                                    delay(flushIntervalMs)
                                    flushDelta()
                                }
                            }
                        } else {
                            null
                        }

                    try {
                        upstream.collect { c ->
                            val noBatching = flushIntervalMs == null && maxDeltaChars == null
                            val shouldFlush =
                                mutex.withLock {
                                    fullContent.append(c)
                                    deltaBuffer.append(c)

                                    c == '\n' ||
                                        (maxDeltaChars != null && maxDeltaChars > 0 && deltaBuffer.length >= maxDeltaChars)
                                }

                            if (noBatching) {
                                flushDelta()
                            } else if (shouldFlush) {
                                flushDelta()
                            }
                        }
                        flushDelta()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        StreamLogger.e(debugTag, "nativeMarkdownSplitBy failed: ${e.message}", e)
                        throw e
                    } finally {
                        flushJob?.cancel()
                        try {
                            closeDefaultChannel()
                            closePluginChannel()
                        } finally {
                            groupChannel.close()
                            session.destroy()
                        }
                    }
                }

                for (group in groupChannel) {
                    collector.emit(group)
                }
            }
        }
    }
}

private fun Stream<String>.nativeMarkdownSplitBySessionString(
    sessionFactory: () -> NativeMarkdownSplitter.Session,
    debugTag: String,
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
    initialContent: String = "",
): Stream<StreamGroup<MarkdownProcessorType?>> {
    val upstream = this

    return object : Stream<StreamGroup<MarkdownProcessorType?>> {
        override val isLocked: Boolean
            get() = upstream.isLocked

        override val bufferedCount: Int
            get() = upstream.bufferedCount

        override suspend fun lock() = upstream.lock()
        override suspend fun unlock() = upstream.unlock()
        override fun clearBuffer() = upstream.clearBuffer()

        override suspend fun collect(collector: StreamCollector<StreamGroup<MarkdownProcessorType?>>) {
            coroutineScope {
                val groupChannel = Channel<StreamGroup<MarkdownProcessorType?>>(Channel.UNLIMITED)

                launch {
                    val session = sessionFactory()
                    val fullContent = StringBuilder(initialContent)
                    val deltaBuffer = StringBuilder()

                    if (initialContent.isNotEmpty()) {
                        // Restore inline parser state without re-emitting the already rendered prefix.
                        session.push(initialContent)
                    }

                    val mutex = Mutex()
                    val flushMutex = Mutex()

                    var defaultTextChannel: Channel<String>? = null
                    var activePluginChannel: Channel<String>? = null
                    var activeTag: MarkdownProcessorType? = null

                    suspend fun openDefaultChannel() {
                        if (defaultTextChannel == null) {
                            val newChannel = Channel<String>(Channel.UNLIMITED)
                            defaultTextChannel = newChannel
                            val stream = newChannel.consumeAsFlow().asStream()
                            groupChannel.send(StreamGroup(null, stream))
                        }
                    }

                    suspend fun closeDefaultChannel() {
                        defaultTextChannel?.close()
                        defaultTextChannel = null
                    }

                    suspend fun openPluginChannel(tag: MarkdownProcessorType) {
                        val newChannel = Channel<String>(Channel.UNLIMITED)
                        activePluginChannel = newChannel
                        activeTag = tag
                        val stream = newChannel.consumeAsFlow().asStream()
                        groupChannel.send(StreamGroup(tag, stream))
                    }

                    suspend fun closePluginChannel() {
                        activePluginChannel?.close()
                        activePluginChannel = null
                        activeTag = null
                    }

                    suspend fun flushDelta() {
                        flushMutex.withLock {
                            val delta = mutex.withLock {
                                if (deltaBuffer.isEmpty()) {
                                    null
                                } else {
                                    deltaBuffer.toString().also { deltaBuffer.setLength(0) }
                                }
                            } ?: return

                            val segments = session.push(delta)
                            if (segments.isEmpty()) return

                            data class Action(val type: MarkdownProcessorType?, val text: String?)

                            val actions = ArrayList<Action>(segments.size / 3)
                            mutex.withLock {
                                var i = 0
                                while (i + 2 < segments.size) {
                                    val typeOrdinal = segments[i]
                                    val start = segments[i + 1]
                                    val end = segments[i + 2]
                                    i += 3

                                    if (typeOrdinal < 0) {
                                        actions.add(Action(type = null, text = null))
                                        continue
                                    }

                                    val type = typeOrdinal.toMarkdownTypeOrNull() ?: MarkdownProcessorType.PLAIN_TEXT
                                    if (start < 0 || end < 0 || start > end || end > fullContent.length) {
                                        continue
                                    }

                                    actions.add(Action(type = type, text = fullContent.substring(start, end)))
                                }
                            }

                            for (action in actions) {
                                if (action.type == null) {
                                    closeDefaultChannel()
                                    closePluginChannel()
                                    continue
                                }

                                val type = action.type
                                val text = action.text ?: ""

                                if (type == MarkdownProcessorType.PLAIN_TEXT) {
                                    if (activePluginChannel != null) {
                                        closePluginChannel()
                                    }
                                    openDefaultChannel()
                                    if (text.isNotEmpty()) {
                                        defaultTextChannel?.send(text)
                                    }
                                } else {
                                    if (defaultTextChannel != null) {
                                        closeDefaultChannel()
                                    }
                                    if (activeTag == null) {
                                        openPluginChannel(type)
                                    } else if (activeTag != type) {
                                        closePluginChannel()
                                        openPluginChannel(type)
                                    }
                                    if (text.isNotEmpty()) {
                                        activePluginChannel?.send(text)
                                    }
                                }
                            }
                        }
                    }

                    val flushJob =
                        if (flushIntervalMs != null && flushIntervalMs > 0) {
                            launch {
                                while (true) {
                                    delay(flushIntervalMs)
                                    flushDelta()
                                }
                            }
                        } else {
                            null
                        }

                    try {
                        upstream.collect { chunk ->
                            val noBatching = flushIntervalMs == null && maxDeltaChars == null
                            val shouldFlush =
                                mutex.withLock {
                                    fullContent.append(chunk)
                                    deltaBuffer.append(chunk)

                                    chunk.indexOf('\n') >= 0 ||
                                        (maxDeltaChars != null && maxDeltaChars > 0 && deltaBuffer.length >= maxDeltaChars)
                                }

                            if (noBatching) {
                                flushDelta()
                            } else if (shouldFlush) {
                                flushDelta()
                            }
                        }
                        flushDelta()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        StreamLogger.e(debugTag, "nativeMarkdownSplitBy failed: ${e.message}", e)
                        throw e
                    } finally {
                        flushJob?.cancel()
                        try {
                            closeDefaultChannel()
                            closePluginChannel()
                        } finally {
                            groupChannel.close()
                            session.destroy()
                        }
                    }
                }

                for (group in groupChannel) {
                    collector.emit(group)
                }
            }
        }
    }
}

fun Stream<Char>.nativeMarkdownSplitByBlock(
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySession(
        sessionFactory = { NativeMarkdownSplitter.createBlockSession() },
        debugTag = "NativeMarkdownBlockSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
    )

internal fun Stream<Char>.nativeMarkdownSplitByBlock(
    flushIntervalMs: Long?,
    maxDeltaChars: Int?,
    initialContent: String,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySession(
        sessionFactory = { NativeMarkdownSplitter.createBlockSession() },
        debugTag = "NativeMarkdownBlockSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
        initialContent = initialContent,
    )

fun Stream<Char>.nativeMarkdownSplitByInline(
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySession(
        sessionFactory = { NativeMarkdownSplitter.createInlineSession() },
        debugTag = "NativeMarkdownInlineSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
    )

internal fun Stream<Char>.nativeMarkdownSplitByInline(
    flushIntervalMs: Long?,
    maxDeltaChars: Int?,
    initialContent: String,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySession(
        sessionFactory = { NativeMarkdownSplitter.createInlineSession() },
        debugTag = "NativeMarkdownInlineSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
        initialContent = initialContent,
    )

@JvmName("nativeMarkdownSplitByBlockString")
fun Stream<String>.nativeMarkdownSplitByBlock(
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySessionString(
        sessionFactory = { NativeMarkdownSplitter.createBlockSession() },
        debugTag = "NativeMarkdownBlockSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
    )

@JvmName("nativeMarkdownSplitByInlineString")
fun Stream<String>.nativeMarkdownSplitByInline(
    flushIntervalMs: Long? = null,
    maxDeltaChars: Int? = null,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySessionString(
        sessionFactory = { NativeMarkdownSplitter.createInlineSession() },
        debugTag = "NativeMarkdownInlineSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
    )

@JvmName("nativeMarkdownSplitByInlineStringWithInitialContent")
internal fun Stream<String>.nativeMarkdownSplitByInline(
    flushIntervalMs: Long?,
    maxDeltaChars: Int?,
    initialContent: String,
): Stream<StreamGroup<MarkdownProcessorType?>> =
    nativeMarkdownSplitBySessionString(
        sessionFactory = { NativeMarkdownSplitter.createInlineSession() },
        debugTag = "NativeMarkdownInlineSplitBy",
        flushIntervalMs = flushIntervalMs,
        maxDeltaChars = maxDeltaChars,
        initialContent = initialContent,
    )
