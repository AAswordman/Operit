package com.ai.assistance.operit.util.stream

import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.stream.plugins.PluginState
import com.ai.assistance.operit.util.stream.plugins.StreamPlugin
import kotlin.collections.ArrayDeque
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 将Stream中的元素转换为另一种类型 */
fun <T, R> Stream<T>.map(transform: suspend (T) -> R): Stream<R> = stream {
    collect { value -> emit(transform(value)) }
}

/** 过滤Stream中的元素 */
fun <T> Stream<T>.filter(predicate: suspend (T) -> Boolean): Stream<T> = stream {
    collect { value ->
        if (predicate(value)) {
            emit(value)
        }
    }
}

/** 限制Stream发射的元素数量 */
fun <T> Stream<T>.take(count: Int): Stream<T> = stream {
    var remaining = count
    collect { value ->
        if (remaining > 0) {
            emit(value)
            remaining--
        }
    }
}

/** 丢弃Stream前n个元素 */
fun <T> Stream<T>.drop(count: Int): Stream<T> = stream {
    var dropped = 0
    collect { value ->
        if (dropped < count) {
            dropped++
        } else {
            emit(value)
        }
    }
}

/** 转换Stream中的每个元素，可以发出多个值 */
fun <T, R> Stream<T>.flatMap(transform: suspend (T) -> Stream<R>): Stream<R> = stream {
    collect { value -> transform(value).collect { transformedValue -> emit(transformedValue) } }
}

/** 对Stream中的每个元素执行操作，并继续传递原始值 */
fun <T> Stream<T>.onEach(action: suspend (T) -> Unit): Stream<T> = stream {
    collect { value ->
        action(value)
        emit(value)
    }
}

/** 添加超时限制 */
fun <T> Stream<T>.timeout(timeout: Duration): Stream<T> = stream {
    var lastEmissionTime = System.currentTimeMillis()
    collect { value ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmissionTime > timeout.inWholeMilliseconds) {
            throw TimeoutException("Stream timed out after $timeout")
        }
        lastEmissionTime = currentTime
        emit(value)
    }
}

/** 合并两个Stream */
fun <T> Stream<T>.merge(other: Stream<T>): Stream<T> = stream {
    coroutineScope {
        launch { this@merge.collect { value -> emit(value) } }
        launch { other.collect { value -> emit(value) } }
    }
}

/** 组合两个Stream的值 */
fun <T1, T2, R> Stream<T1>.combine(other: Stream<T2>, transform: suspend (T1, T2) -> R): Stream<R> =
        stream {
            val latest1 = mutableListOf<T1>()
            val latest2 = mutableListOf<T2>()
            val channel = Channel<Pair<T1?, T2?>>(Channel.BUFFERED)

            coroutineScope {
                launch {
                    this@combine.collect { value ->
                        val latest2Value: T2? =
                                synchronized(latest2) {
                                    if (latest2.isNotEmpty()) latest2.last() else null
                                }

                        synchronized(latest1) {
                            if (latest1.isNotEmpty()) latest1.clear()
                            latest1.add(value)
                        }

                        if (latest2Value != null) {
                            channel.send(value to latest2Value)
                        }
                    }
                }

                launch {
                    other.collect { value ->
                        val latest1Value: T1? =
                                synchronized(latest1) {
                                    if (latest1.isNotEmpty()) latest1.last() else null
                                }

                        synchronized(latest2) {
                            if (latest2.isNotEmpty()) latest2.clear()
                            latest2.add(value)
                        }

                        if (latest1Value != null) {
                            channel.send(latest1Value to value)
                        }
                    }
                }

                launch {
                    for (pair in channel) {
                        emit(transform(pair.first!!, pair.second!!))
                    }
                }
            }
        }

/** 连接两个Stream，第一个完成后再收集第二个 */
fun <T> Stream<T>.concatWith(other: Stream<T>): Stream<T> = stream {
    this@concatWith.collect { value -> emit(value) }
    other.collect { value -> emit(value) }
}

/** 捕获Stream中的异常 */
fun <T> Stream<T>.catch(action: suspend (Throwable) -> Unit): Stream<T> = stream {
    try {
        this@catch.collect { value -> emit(value) }
    } catch (e: Throwable) {
        if (e is CancellationException) {
            throw e
        }
        action(e)
    }
}

/** 无论Stream是否正常完成或发生异常，都执行指定操作 */
fun <T> Stream<T>.finally(action: suspend () -> Unit): Stream<T> = stream {
    try {
        this@finally.collect { value -> emit(value) }
    } finally {
        action()
    }
}

/** 节流操作，限制发射频率 */
fun <T> Stream<T>.throttleFirst(windowDuration: Duration): Stream<T> = stream {
    var lastEmitTime = 0L
    collect { value ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmitTime >= windowDuration.inWholeMilliseconds) {
            lastEmitTime = currentTime
            emit(value)
        }
    }
}

/** 去除重复元素 */
fun <T> Stream<T>.distinctUntilChanged(): Stream<T> = stream {
    var lastValue: Any? = NoValue
    collect { value ->
        if (lastValue === NoValue || lastValue != value) {
            lastValue = value
            emit(value)
        }
    }
}

/**
 * 将流中的元素按指定大小分块 (分组)。
 *
 * @param size 每个块的大小。
 * @return 一个新的Stream，它发出元素列表 (块)。
 * @throws IllegalArgumentException 如果 `size` 不是正数。
 */
fun <T> Stream<T>.chunked(size: Int): Stream<List<T>> {
    require(size > 0) { "Size must be positive." }
    return stream {
        val currentChunk = mutableListOf<T>()
        this@chunked.collect { value ->
            currentChunk.add(value)
            if (currentChunk.size == size) {
                emit(currentChunk.toList()) // Emit a copy of the chunk
                currentChunk.clear()
            }
        }
        // Emit the last chunk if it's not empty
        if (currentChunk.isNotEmpty()) {
            emit(currentChunk.toList())
        }
    }
}

/**
 * 使用一组插件将字符流分割成不同的组。
 *
 * 该函数会根据插件的匹配状态将字符流划分为不同的组：
 * 1. 匹配到插件的字符会分到对应插件组
 * 2. 未匹配到任何插件的字符会归为默认文本组（tag为null）
 *
 * @param plugins 用于分割流的插件列表
 * @return 返回一个包含分组后结果的Stream。每个组内的流会实时发射单个字符（作为String）。
 */
fun Stream<Char>.splitBy(plugins: List<StreamPlugin>): Stream<StreamGroup<StreamPlugin?>> {
    val upstream = this
    val TAG = "StreamSplitter"

    return object : Stream<StreamGroup<StreamPlugin?>> {
        // 实现Stream接口必须的属性
        override val isLocked: Boolean
            get() = upstream.isLocked
        override val bufferedCount: Int
            get() = upstream.bufferedCount

        // 实现Stream接口必须的方法
        override suspend fun lock() = upstream.lock()
        override suspend fun unlock() = upstream.unlock()
        override fun clearBuffer() = upstream.clearBuffer()

        override suspend fun collect(collector: StreamCollector<StreamGroup<StreamPlugin?>>) {
            coroutineScope {
                val groupChannel = Channel<StreamGroup<StreamPlugin?>>(Channel.UNLIMITED)

                launch { // Producer Coroutine
                    try {
                        plugins.forEach {
                            it.initPlugin()
                        }

                        var defaultTextChannel: Channel<Char>? = null
                        var activePlugin: StreamPlugin? = null
                        var activePluginChannel: Channel<Char>? = null

                        // 用于在没有活动插件时缓冲字符和插件处理结果
                        val evaluationBuffer = mutableListOf<Char>()
                        val evaluationShouldEmit = mutableListOf<Map<StreamPlugin, Boolean>>()

                        // 用于处理插件状态转换时需要重新评估的字符
                        val pendingChars = ArrayDeque<Char>()
                        val upstreamChannel = Channel<Char>(Channel.UNLIMITED)
                        var atStartOfLine = true

                        launch {
                            try {
                                upstream.collect { upstreamChannel.send(it) }
                            } finally {
                                upstreamChannel.close()
                            }
                        }

                        suspend fun openDefaultChannel() {
                            if (defaultTextChannel == null) {
                                val newChannel = Channel<Char>(Channel.UNLIMITED)
                                defaultTextChannel = newChannel
                                val stream =
                                        newChannel.consumeAsFlow().map { it.toString() }.asStream()
                                groupChannel.send(StreamGroup(null, stream))
                            }
                        }

                        suspend fun closeDefaultChannel() {
                            defaultTextChannel?.close()
                            defaultTextChannel = null
                        }

                        suspend fun openPluginChannel(plugin: StreamPlugin) {
                            val newChannel = Channel<Char>(Channel.UNLIMITED)
                            activePluginChannel = newChannel
                            activePlugin = plugin
                            val stream = newChannel.consumeAsFlow().map { it.toString() }.asStream()
                            groupChannel.send(StreamGroup(plugin, stream))
                        }

                        suspend fun closePluginChannel() {
                            activePluginChannel?.close()
                            activePluginChannel = null
                            activePlugin = null
                        }

                        while (coroutineContext.isActive) {
                            val char: Char =
                                    if (pendingChars.isNotEmpty()) {
                                        pendingChars.removeFirst()
                                    } else {
                                        upstreamChannel.receiveCatching().getOrNull() ?: break
                                    }

                            val isAtStartOfLineForCurrentChar = atStartOfLine
                            atStartOfLine = (char == '\n')

                            val currentActivePlugin = activePlugin

                            if (currentActivePlugin != null) {
                                // --- 状态：处理中 ---
                                val shouldEmit =
                                        currentActivePlugin.processChar(
                                                char,
                                                isAtStartOfLineForCurrentChar
                                        )
                                if (shouldEmit) {
                                    activePluginChannel?.send(char)
                                }

                                if (currentActivePlugin.state != PluginState.PROCESSING) {
                                    // 处理WAITFOR状态 - 积累字符，等待确认或退出
                                    if (currentActivePlugin.state == PluginState.WAITFOR) {
                                        // 创建WAITFOR缓冲区
                                        val waitforBuffer = mutableListOf<Char>()
                                        if (shouldEmit) {
                                            waitforBuffer.add(char)
                                        }

                                        // 等待下一个字符决定去留
                                        var nextChar: Char? = null
                                        try {
                                            nextChar = upstreamChannel.receiveCatching().getOrNull()
                                        } catch (e: Exception) {
                                            // WAITFOR状态时接收字符失败
                                        }

                                        if (nextChar != null) {
                                            val isNextAtStartOfLine = (char == '\n')
                                            val nextShouldEmit =
                                                    currentActivePlugin.processChar(
                                                            nextChar,
                                                            isNextAtStartOfLine
                                                    )

                                            if (currentActivePlugin.state == PluginState.PROCESSING
                                            ) {
                                                // 确认继续处理 - 发射缓冲的字符
                                                if (nextShouldEmit) {
                                                    activePluginChannel?.send(nextChar)
                                                }
                                                atStartOfLine = (nextChar == '\n')
                                                continue
                                            } else {
                                                // 退出WAITFOR状态 - 返还所有字符
                                                pendingChars.addFirst(nextChar)
                                                waitforBuffer.reversed().forEach {
                                                    pendingChars.addFirst(it)
                                                }
                                            }
                                        } else {
                                            // 流结束，返还缓冲的字符
                                            waitforBuffer.reversed().forEach {
                                                pendingChars.addFirst(it)
                                            }
                                        }
                                    }

                                    closePluginChannel()
                                }
                            } else {
                                // --- 状态：评估中 ---
                                // 所有插件并行处理字符
                                evaluationBuffer.add(char)
                                val shouldEmitMap =
                                        plugins.associateWith {
                                            it.processChar(char, isAtStartOfLineForCurrentChar)
                                        }
                                evaluationShouldEmit.add(shouldEmitMap)

                                // 记录所有TRYING状态的插件
                                val tryingPlugins =
                                        plugins.filter { it.state == PluginState.TRYING }

                                val successfulPlugin =
                                        plugins.find { it.state == PluginState.PROCESSING }

                                if (successfulPlugin != null) {
                                    // --- 转换：评估中 -> 处理中 ---
                                    
                                    // 如果有多个插件可能同时匹配，记录潜在冲突
                                    val otherTryingPlugins =
                                            plugins.filter {
                                                it != successfulPlugin &&
                                                        it.state == PluginState.TRYING
                                            }

                                    closeDefaultChannel()
                                    openPluginChannel(successfulPlugin)

                                    // 回放缓冲区中的字符到成功的插件
                                    evaluationBuffer.forEachIndexed { index, bufferedChar ->
                                        val shouldEmit =
                                                evaluationShouldEmit[index][successfulPlugin]
                                        if (shouldEmit == true) {
                                            activePluginChannel?.send(bufferedChar)
                                        }
                                    }
                                    evaluationBuffer.clear()
                                    evaluationShouldEmit.clear()

                                    // 重置其他插件
                                    plugins.forEach { if (it != successfulPlugin) it.reset() }
                                } else if (plugins.none { it.state == PluginState.TRYING }) {
                                    // --- 转换：评估中 -> 空闲 ---
                                    // 没有插件处于TRYING状态，意味着匹配失败
                                    openDefaultChannel()
                                    evaluationBuffer.forEach { defaultTextChannel?.send(it) }
                                    evaluationBuffer.clear()
                                    evaluationShouldEmit.clear()

                                    // 重置所有插件
                                    plugins.forEach { it.reset() }
                                }
                                // 如果有插件处于TRYING状态，则继续缓冲
                            }
                        }

                        // 流结束后，关闭所有剩余通道并清空缓冲区
                        closeDefaultChannel()
                        closePluginChannel()

                        if (evaluationBuffer.isNotEmpty()) {
                            openDefaultChannel()
                            evaluationBuffer.forEach { defaultTextChannel?.send(it) }
                            closeDefaultChannel()
                        }
                    } finally {
                        groupChannel.close()
                    }
                }

                for (group in groupChannel) {
                    collector.emit(group)
                }
            }
        }
    }
}

/**
 * 使用一组插件将字符串流分割成不同的组。
 *
 * 该函数将字符串流中的每个字符串拆分为单个字符，然后应用与 Char 版本相同的分割逻辑。 这允许字符串流以与字符流相同的方式被处理，便于处理文本数据流。
 *
 * @param plugins 用于分割流的插件列表
 * @return 返回一个包含分组后结果的Stream。每个组内的流会实时发射文本片段。
 */
@JvmName("splitByString")
fun Stream<String>.splitBy(plugins: List<StreamPlugin>): Stream<StreamGroup<StreamPlugin?>> {
    val TAG = "StringStreamSplitter"
    val upstream = this

    // 创建一个包装的Stream，附带委托功能
    val delegatingStream =
            object : Stream<String> by upstream {
                override suspend fun collect(collector: StreamCollector<String>) {
                    upstream.collect(collector)
                }
            }

    // 将字符串流转换为字符流
    return delegatingStream
            .flatMap { str ->
                stream {
                    for (char in str) {
                        emit(char)
                    }
                }
            }
            .splitBy(plugins) // 复用字符流的splitBy实现
}

/** 用于表示没有值的标记对象 */
private object NoValue

/** Take操作完成时抛出的异常 */
private class TakeCompletedException : Exception()

/** 超时异常 */
class TimeoutException(message: String) : Exception(message)

/** 对每个元素应用延迟后再发射 */
fun <T> Stream<T>.delay(duration: Duration): Stream<T> = stream {
    collect { value ->
        delay(duration.inWholeMilliseconds)
        emit(value)
    }
}

/** 防抖操作，仅在指定时间内没有新值到来时才发射最后一个值 */
fun <T> Stream<T>.debounce(timeout: Duration): Stream<T> = stream {
    var lastValue: T? = null
    var lastEmitJob: Job? = null

    coroutineScope {
        collect { value ->
            lastValue = value
            lastEmitJob?.cancel()
            lastEmitJob = launch {
                delay(timeout.inWholeMilliseconds)
                lastValue?.let {
                    emit(it)
                }
            }
        }
    }
}

/**
 * 采样操作，以固定时间间隔发射最近的一个值 即使没有新值到来也会按间隔发射最后已知的值
 *
 * @param period 采样间隔
 */
fun <T> Stream<T>.sample(period: Duration): Stream<T> = stream {
    var latestValue: Any? = NoValue
    var hasValue = false

    coroutineScope {
        launch {
            while (true) {
                delay(period.inWholeMilliseconds)
                if (hasValue) {
                    @Suppress("UNCHECKED_CAST") emit(latestValue as T)
                }
            }
        }

        collect { value ->
            latestValue = value
            hasValue = true
        }
    }
}

/**
 * 节流操作，在指定的时间窗口内仅发射最后一个元素 与throttleFirst相反，throttleFirst保留窗口内第一个元素
 *
 * @param windowDuration 时间窗口大小
 */
fun <T> Stream<T>.throttleLast(windowDuration: Duration): Stream<T> = stream {
    var lastEmitTime = 0L
    var pendingValue: T? = null
    var hasPending = false

    collect { value ->
        val currentTime = System.currentTimeMillis()
        pendingValue = value
        hasPending = true

        if (currentTime - lastEmitTime >= windowDuration.inWholeMilliseconds) {
            lastEmitTime = currentTime
            if (hasPending) {
                pendingValue?.let {
                    emit(it)
                    hasPending = false
                }
            }
        }
    }
}

/**
 * 以固定频率限制发射的值，如果在时间内没有值，则等待下个周期 与sample不同，fixedRate只会发射实际接收到的值，不会重复发射同一个值
 *
 * @param period 固定周期
 */
fun <T> Stream<T>.fixedRate(period: Duration): Stream<T> = stream {
    var nextEmitTime = 0L

    collect { value ->
        val currentTime = System.currentTimeMillis()

        if (nextEmitTime == 0L) {
            // 第一个元素立即发射
            nextEmitTime = currentTime + period.inWholeMilliseconds
            emit(value)
        } else if (currentTime >= nextEmitTime) {
            // 已经过了发射时间，立即发射并设置下次时间
            emit(value)
            nextEmitTime = currentTime + period.inWholeMilliseconds
        } else {
            // 等待到下次发射时间
            val waitTime = nextEmitTime - currentTime
            delay(waitTime)
            emit(value)
            nextEmitTime = System.currentTimeMillis() + period.inWholeMilliseconds
        }
    }
}

/**
 * 在超时时间内每次有新值都会重新计时，如果超时则发出超时信号并终止
 *
 * @param timeoutDuration 超时时间
 * @param timeoutValue 超时时发射的值，如果为null则不发射值
 */
fun <T> Stream<T>.timeoutTrigger(timeoutDuration: Duration, timeoutValue: T? = null): Stream<T> =
        stream {
            var timeoutJob: Job? = null

            try {
                coroutineScope {
                    timeoutJob = launch {
                        delay(timeoutDuration)
                        if (timeoutValue != null) {
                            emit(timeoutValue)
                        }
                        throw TimeoutException("Stream timeoutTrigger after $timeoutDuration")
                    }

                    this@timeoutTrigger.collect { value ->
                        timeoutJob?.cancel()
                        timeoutJob = launch {
                            delay(timeoutDuration)
                            if (timeoutValue != null) {
                                emit(timeoutValue)
                            }
                            throw TimeoutException("Stream timeoutTrigger after $timeoutDuration")
                        }
                        emit(value)
                    }
                }
            } catch (e: TimeoutException) {
                // 流已超时
            } finally {
                timeoutJob?.cancel()
            }
        }
