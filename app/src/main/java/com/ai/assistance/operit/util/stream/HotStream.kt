package com.ai.assistance.operit.util.stream

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 共享Stream接口，类似于SharedFlow */
interface SharedStream<T> : Stream<T> {
    /** 当前订阅者数量 */
    val subscriptionCount: Int

    /** 重放缓存大小 */
    val replayCache: List<T>
}

/** 可变共享Stream接口，类似于MutableSharedFlow */
interface MutableSharedStream<T> : SharedStream<T> {
    /** 发射一个值到Stream */
    suspend fun emit(value: T)

    /** 尝试发射一个值，如果缓冲区已满返回false */
    fun tryEmit(value: T): Boolean

    /** 重置重放缓存 */
    fun resetReplayCache()
}

/** 状态Stream接口，类似于StateFlow */
interface StateStream<T> : SharedStream<T> {
    /** 当前值 */
    val value: T
}

/** 可变状态Stream接口，类似于MutableStateFlow */
interface MutableStateStream<T> : StateStream<T>, MutableSharedStream<T> {
    /** 设置当前值 */
    override var value: T

    /** 比较并设置值 */
    fun compareAndSet(expect: T, update: T): Boolean
}

/**
 * Helper to access the internal kotlinx.coroutines.flow.StateFlow<Int> for subscription count This
 * is for internal use by the .state() and .share() operators.
 */
internal fun <T> SharedStream<T>.getInternalSubscriptionCountFlow():
        kotlinx.coroutines.flow.StateFlow<Int>? {
    return when (this) {
        is MutableSharedStreamImpl<T> -> this.internalSubscriptionCountFlow
        is MutableStateStreamImpl<T> -> this.internalFlow.subscriptionCount
        else -> null
    }
}

/** MutableSharedFlow的包装器，实现MutableSharedStream */
class MutableSharedStreamImpl<T>(
        replay: Int = 0,
        extraBufferCapacity: Int = 0,
        onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
) : MutableSharedStream<T> {
    private sealed interface SharedEvent<out T> {
        data class Value<T>(val payload: T) : SharedEvent<T>
        data class Completion(val cause: Throwable?) : SharedEvent<Nothing>
    }

    private val replayLimit = replay.coerceAtLeast(0)
    private val replayBuffer = ArrayDeque<T>()
    private val subscribers = linkedMapOf<Long, Channel<SharedEvent<T>>>()
    private val stateLock = Any()
    private var nextSubscriberId = 0L
    private var closeCause: Throwable? = null
    private var isClosed = false

    internal val internalSubscriptionCountFlow = MutableStateFlow(0)

    // 热流不需要锁定机制，所以这里提供默认实现
    override val isLocked: Boolean = false
    override val bufferedCount: Int = 0

    override suspend fun lock() {
        // 热流不支持锁定，此处不执行任何操作
        StreamLogger.d("HotStream", "热流不支持锁定操作")
    }

    override suspend fun unlock() {
        // 热流不支持锁定，此处不执行任何操作
        StreamLogger.d("HotStream", "热流不支持解锁操作")
    }

    override fun clearBuffer() {
        // 热流有自己的缓冲管理，此方法不适用
        StreamLogger.d("HotStream", "热流不支持清空缓冲区操作")
    }

    override val subscriptionCount: Int
        get() = internalSubscriptionCountFlow.value

    override val replayCache: List<T>
        get() = synchronized(stateLock) { replayBuffer.toList() }

    override suspend fun emit(value: T) {
        val subscriberChannels =
            synchronized(stateLock) {
                if (isClosed) {
                    emptyList()
                } else {
                    appendToReplayBufferLocked(value)
                    subscribers.values.toList()
                }
            }

        for (channel in subscriberChannels) {
            channel.send(SharedEvent.Value(value))
        }
    }

    override fun tryEmit(value: T): Boolean {
        val subscriberChannels =
            synchronized(stateLock) {
                if (isClosed) {
                    return false
                }
                appendToReplayBufferLocked(value)
                subscribers.values.toList()
            }

        subscriberChannels.forEach { channel ->
            channel.trySend(SharedEvent.Value(value))
        }
        return true
    }

    override fun resetReplayCache() {
        synchronized(stateLock) {
            replayBuffer.clear()
        }
    }

    fun close(cause: Throwable? = null) {
        val subscriberChannels =
            synchronized(stateLock) {
                if (isClosed) {
                    return
                }
                isClosed = true
                closeCause = cause
                subscribers.values.toList()
            }

        subscriberChannels.forEach { channel ->
            channel.trySend(SharedEvent.Completion(cause))
            channel.close()
        }
    }

    override suspend fun collect(collector: StreamCollector<T>) {
        val replaySnapshot: List<T>
        val subscriberId: Long?
        val subscriberChannel: Channel<SharedEvent<T>>?
        val closedSnapshot: Throwable?

        synchronized(stateLock) {
            replaySnapshot = replayBuffer.toList()
            closedSnapshot = if (isClosed) closeCause else null
            if (isClosed) {
                subscriberId = null
                subscriberChannel = null
            } else {
                subscriberId = nextSubscriberId++
                subscriberChannel = Channel(Channel.UNLIMITED)
                subscribers[subscriberId] = subscriberChannel
                internalSubscriptionCountFlow.value = subscribers.size
            }
        }

        try {
            replaySnapshot.forEach { value ->
                collector.emit(value)
            }

            if (subscriberChannel == null) {
                if (closedSnapshot != null) {
                    throw closedSnapshot
                }
                return
            }

            for (event in subscriberChannel) {
                when (event) {
                    is SharedEvent.Value -> collector.emit(event.payload)
                    is SharedEvent.Completion -> {
                        if (event.cause != null) {
                            throw event.cause
                        }
                        return
                    }
                }
            }

            if (closedSnapshot != null) {
                throw closedSnapshot
            }
        } finally {
            subscriberChannel?.cancel()
            if (subscriberId != null) {
                synchronized(stateLock) {
                    subscribers.remove(subscriberId)
                    internalSubscriptionCountFlow.value = subscribers.size
                }
            }
        }
    }

    private fun appendToReplayBufferLocked(value: T) {
        if (replayLimit <= 0) {
            return
        }
        replayBuffer.addLast(value)
        while (replayBuffer.size > replayLimit) {
            replayBuffer.removeFirst()
        }
    }
}

/** MutableStateFlow的包装器，实现MutableStateStream */
class MutableStateStreamImpl<T>(initialValue: T) : MutableStateStream<T> {
    internal val internalFlow = MutableStateFlow(initialValue)

    // 热流不需要锁定机制，所以这里提供默认实现
    override val isLocked: Boolean = false
    override val bufferedCount: Int = 0

    override suspend fun lock() {
        // 热流不支持锁定，此处不执行任何操作
        StreamLogger.d("HotStream", "状态流不支持锁定操作")
    }

    override suspend fun unlock() {
        // 热流不支持锁定，此处不执行任何操作
        StreamLogger.d("HotStream", "状态流不支持解锁操作")
    }

    override fun clearBuffer() {
        // 热流有自己的缓冲管理，此方法不适用
        StreamLogger.d("HotStream", "状态流不支持清空缓冲区操作")
    }

    override var value: T
        get() = internalFlow.value
        set(value) {
            internalFlow.value = value
        }

    override val subscriptionCount: Int
        get() = internalFlow.subscriptionCount.value

    override val replayCache: List<T>
        get() = internalFlow.replayCache

    override suspend fun emit(value: T) {
        internalFlow.emit(value)
    }

    override fun tryEmit(value: T): Boolean {
        return internalFlow.tryEmit(value)
    }

    override fun resetReplayCache() {
        // StateFlow does not support resetting replay cache as it always holds the current state.
    }

    override fun compareAndSet(expect: T, update: T): Boolean {
        return internalFlow.compareAndSet(expect, update)
    }

    override suspend fun collect(collector: StreamCollector<T>) {
        internalFlow.collect { value -> collector.emit(value) }
    }
}

/** 创建一个MutableSharedStream */
fun <T> MutableSharedStream(
        replay: Int = 0,
        extraBufferCapacity: Int = 0,
        onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
        context: CoroutineContext = EmptyCoroutineContext
): MutableSharedStream<T> {
    return MutableSharedStreamImpl<T>(replay, extraBufferCapacity, onBufferOverflow)
}

/** 创建一个MutableStateStream */
fun <T> MutableStateStream(initialValue: T): MutableStateStream<T> {
    return MutableStateStreamImpl(initialValue)
}

/** 将Stream转变为热流，类似于Flow的shareIn */
fun <T> Stream<T>.share(
        scope: CoroutineScope,
        replay: Int = 0,
        started: StreamStart = StreamStart.EAGERLY,
        onComplete: suspend () -> Unit = {}
): SharedStream<T> {
    val sharedStream = MutableSharedStreamImpl<T>(replay = replay)
    var upstreamJob: Job? = null

    when (started) {
        StreamStart.EAGERLY -> {
            // 这个Job现在是scope的直接子Job
            upstreamJob =
                    scope.launch {
                        var completionCause: Throwable? = null
                        try {
                            this@share.collect { value -> sharedStream.emit(value) }
                        } catch (error: Throwable) {
                            completionCause = error
                        } finally {
                            sharedStream.close(completionCause)
                            onComplete()
                        }
                    }
        }
        StreamStart.LAZILY -> {
            scope.launch {
                val subscriptionCountFlow = sharedStream.getInternalSubscriptionCountFlow()
                if (subscriptionCountFlow != null) {
                    subscriptionCountFlow.collect { count ->
                        if (count > 0 && upstreamJob?.isActive != true) {
                            upstreamJob =
                                    scope.launch {
                                        var completionCause: Throwable? = null
                                        try {
                                            this@share.collect { emittedValue ->
                                                sharedStream.emit(emittedValue)
                                            }
                                        } catch (error: Throwable) {
                                            completionCause = error
                                        } finally {
                                            sharedStream.close(completionCause)
                                            onComplete()
                                        }
                                    }
                        } else if (count == 0) {
                            // 当没有订阅者时，取消上游流的收集
                            upstreamJob?.cancel()
                            upstreamJob = null
                        }
                    }
                } else {
                    println(
                            "Warning: Stream.share LAZILY mode could not observe subscriptions, may behave like EAGERLY."
                    )
                    // Fallback to EAGERLY behavior
                    scope.launch {
                        var completionCause: Throwable? = null
                        try {
                            this@share.collect { value -> sharedStream.emit(value) }
                        } catch (error: Throwable) {
                            completionCause = error
                        } finally {
                            sharedStream.close(completionCause)
                            onComplete()
                        }
                    }
                }
            }
        }
    }

    return sharedStream
}

/** 将Stream转变为StateStream，类似于Flow的stateIn */
fun <T> Stream<T>.state(
        scope: CoroutineScope,
        initialValue: T,
        started: StreamStart = StreamStart.EAGERLY
): StateStream<T> {
    val stateStream = MutableStateStreamImpl(initialValue)
    var upstreamJob: Job? = null

    when (started) {
        StreamStart.EAGERLY -> {
            scope.launch { this@state.collect { value -> stateStream.value = value } }
        }
        StreamStart.LAZILY -> {
            scope.launch {
                val subscriptionCountFlow = stateStream.getInternalSubscriptionCountFlow()
                if (subscriptionCountFlow != null) {
                    subscriptionCountFlow.collect { count ->
                        if (count > 0 && upstreamJob == null) {
                            upstreamJob =
                                    scope.launch {
                                        this@state.collect { emittedValue ->
                                            stateStream.value = emittedValue
                                        }
                                    }
                        }
                    }
                } else {
                    println(
                            "Warning: Stream.state LAZILY mode could not observe subscriptions, may behave like EAGERLY."
                    )
                    upstreamJob =
                            scope.launch {
                                this@state.collect { value -> stateStream.value = value }
                            }
                }
            }
        }
    }

    return stateStream
}

/** 流启动模式 */
enum class StreamStart {
    /** 立即启动 */
    EAGERLY,

    /** 有订阅者时启动 */
    LAZILY
}
