package com.ai.assistance.operit.util.stream

import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue

/** 日志工具类，统一Stream相关日志 */
object StreamLogger {
    private const val TAG = "StreamFramework"
    private var enabled = true
    private var verboseEnabled = false

    /** 启用或禁用日志 */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /** 启用或禁用详细日志 */
    fun setVerboseEnabled(enabled: Boolean) {
        this.verboseEnabled = enabled
    }

    /** 记录调试信息 */
    fun d(component: String, message: String) {
        if (enabled) {
            AppLogger.d(TAG, "[$component] $message")
        }
    }

    /** 记录信息 */
    fun i(component: String, message: String) {
        if (enabled) {
            AppLogger.i(TAG, "[$component] $message")
        }
    }

    /** 记录详细信息 */
    fun v(component: String, message: String) {
        if (enabled && verboseEnabled) {
            AppLogger.v(TAG, "[$component] $message")
        }
    }

    /** 记录警告 */
    fun w(component: String, message: String) {
        if (enabled) {
            AppLogger.w(TAG, "[$component] $message")
        }
    }

    /** 记录错误 */
    fun e(component: String, message: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) {
                AppLogger.e(TAG, "[$component] $message", throwable)
            } else {
                AppLogger.e(TAG, "[$component] $message")
            }
        }
    }
}

/** Stream接口，类似于Kotlin Flow，用于表示异步计算的数据流 */
interface Stream<T> {
    /** 流是否处于锁定状态 */
    val isLocked: Boolean
    
    /** 缓存的元素数量 */
    val bufferedCount: Int
    
    /** 锁定流 - 暂停接收新数据但继续发送 */
    suspend fun lock()
    
    /** 解锁流 - 恢复接收并发送缓存的数据 */
    suspend fun unlock()
    
    /** 清空缓存的数据 */
    fun clearBuffer()
    
    /** 收集Stream发出的值 */
    suspend fun collect(collector: StreamCollector<T>)

    /** Stream的标准收集方法，简化使用 */
    suspend fun collect(onEach: suspend (T) -> Unit) {
        collect(
            object : StreamCollector<T> {
                override suspend fun emit(value: T) {
                    StreamLogger.v("Stream", "收集到元素: $value")
                    onEach(value)
                }
            }
        )
    }
}

/** Stream收集器接口，类似于FlowCollector */
interface StreamCollector<in T> {
    /** 发射一个值到Stream */
    suspend fun emit(value: T)
}

/** 默认Stream抽象实现，提供锁定功能 */
abstract class AbstractStream<T> : Stream<T> {
    private val mutex = Mutex()
    private val isLockedFlag = AtomicBoolean(false)
    private val isClosedFlag = AtomicBoolean(false)
    private val buffer = ConcurrentLinkedQueue<T>()
    
    override val isLocked: Boolean get() = isLockedFlag.get()
    override val bufferedCount: Int get() = buffer.size
    
    override suspend fun lock() {
        mutex.withLock {
            if (!isLockedFlag.get() && !isClosedFlag.get()) {
                // StreamLogger.d("Stream", "流已锁定")
                isLockedFlag.set(true)
            } else if (isClosedFlag.get()) {
                // StreamLogger.d("Stream", "流已关闭，锁定操作被忽略")
            }
        }
    }
    
    override suspend fun unlock() {
        mutex.withLock {
            if (isLockedFlag.compareAndSet(true, false)) {
                val bufferSize = buffer.size
                if (bufferSize > 0) {
                    // StreamLogger.d("Stream", "流已解锁，发送缓存数据 (${bufferSize}项)")
                    val tempList = ArrayList<T>(buffer)
                    buffer.clear()
                
                    // 无论流是否关闭，都尝试处理所有缓冲项
                    for (item in tempList) {
                        try {
                            emitBufferedItem(item)
                        } catch (e: Exception) {
                            // 处理缓存项时发生异常
                            StreamLogger.w("Stream", "处理缓存项时发生异常: ${e.message}")
                            throw e
                        }
                    }
                } else {
                    // StreamLogger.d("Stream", "流已解锁，无缓存数据")
                }
            }
        }
    }
    
    override fun clearBuffer() {
        val size = buffer.size
        buffer.clear()
        StreamLogger.d("Stream", "已清空缓冲区 ($size 项)")
    }
    
    /** 当流被锁定时，将值存入缓冲区 */
    protected suspend fun tryBuffer(value: T): Boolean {
        if (isLockedFlag.get() && !isClosedFlag.get()) {
            buffer.offer(value)
            StreamLogger.v("Stream", "锁定中，值已缓存")
            return true
        }
        return false
    }
    
    /** 在流解锁时处理缓冲区中的项目 */
    protected abstract suspend fun emitBufferedItem(item: T)
    
    /**
     * 标记流已关闭，此方法应在流完成或发生错误时调用
     * 如果流处于锁定状态，此方法将允许处理缓冲区数据
     */
    protected fun markClosed() {
        isClosedFlag.set(true)
        // StreamLogger.d("Stream", "流已标记为关闭")
    }
    
    /**
     * 检查流是否已关闭
     */
    protected fun isClosed(): Boolean = isClosedFlag.get()
}

/** Flow到Stream的适配器，允许将Kotlin Flow转换为Stream */
class FlowAsStream<T>(private val flow: Flow<T>) : AbstractStream<T>() {
    private var activeCollector: StreamCollector<T>? = null

    override suspend fun collect(collector: StreamCollector<T>) {
        activeCollector = collector
        
        try {
            flow.collect { value ->
                // 如果流被锁定，则缓存值
                if (!isClosed() && !tryBuffer(value)) {
                    // 否则直接发送
                    collector.emit(value)
                }
            }
        } finally {
            // 流收集完成或异常时，标记流已关闭
            markClosed()
            
            // 如果流在关闭时处于锁定状态，解锁以处理缓冲的数据
            if (isLocked) {
                StreamLogger.i("FlowAsStream", "流关闭时处于锁定状态，尝试解锁处理缓冲数据")
                try {
                    unlock()
                } catch (e: Exception) {
                    StreamLogger.w("FlowAsStream", "流关闭时解锁失败: ${e.message}")
                }
            }
        }
    }
    
    override suspend fun emitBufferedItem(item: T) {
        // 即使流已关闭，也尝试发送缓冲的数据
        activeCollector?.emit(item)
    }
}

/** Stream到Flow的适配器，允许将Stream转换为Kotlin Flow */
class StreamAsFlow<T>(private val stream: Stream<T>) : Flow<T> {
    override suspend fun collect(collector: FlowCollector<T>) {
        stream.collect { value ->
            collector.emit(value)
        }
    }
}

/** 将Flow转换为Stream */
fun <T> Flow<T>.asStream(): Stream<T> = FlowAsStream(this)

/** 将Stream转换为Flow */
fun <T> Stream<T>.asFlow(): Flow<T> = StreamAsFlow(this)

/** 在指定的协程作用域中启动Stream收集 */
fun <T> Stream<T>.launchIn(scope: CoroutineScope, onEach: suspend (T) -> Unit = {}): Job {
    StreamLogger.d("Stream.launchIn", "在协程作用域中启动Stream收集")
    return scope.launch {
        collect { value ->
            StreamLogger.v("Stream.launchIn", "收集到元素: $value")
            onEach(value)
        }
    }
}

