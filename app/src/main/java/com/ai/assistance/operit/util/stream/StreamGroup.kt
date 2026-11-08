package com.ai.assistance.operit.util.stream

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger

/**
 * 流处理器接口，定义了如何处理流数据
 * @param T 输入数据类型
 * @param R 处理结果类型
 */
interface StreamProcessor<T, R> {
    /**
     * 处理流数据
     * @param stream 输入流
     * @return 处理结果
     */
    suspend fun process(stream: Stream<T>): R
}

/**
 * 复合型流处理器，可以组合多个处理器按顺序执行
 * @param T 输入数据类型
 * @param R 最终结果类型
 */
class CompositeStreamProcessor<T, R>(
        private val processors: List<StreamProcessor<T, *>>,
        private val finalProcessor: StreamProcessor<T, R>
) : StreamProcessor<T, R> {
    override suspend fun process(stream: Stream<T>): R {
        // 先执行中间处理器
        processors.forEach { it.process(stream) }
        // 最后执行最终处理器并返回结果
        return finalProcessor.process(stream)
    }

    companion object {
        /** 构建复合处理器的工厂方法 */
        fun <T, R> compose(
                vararg processors: StreamProcessor<T, *>,
                final: StreamProcessor<T, R>
        ): CompositeStreamProcessor<T, R> {
            return CompositeStreamProcessor(processors.toList(), final)
        }
    }
}

/**
 * StreamGroup表示带有关联标签的流数据组，支持嵌套结构和处理器绑定
 *
 * @param TAG 标签类型
 * @property tag 标识这个流组的标签
 * @property stream 这个组中的数据流
 * @property processor 处理这个组数据的处理器
 * @property children 子分组列表
 * @property continuesPrevious 是否接续恢复前尚未完成的同一块，仅由分段器标记
 */
class StreamGroup<TAG>(
        val tag: TAG,
        val stream: Stream<String>,
        val processor: StreamProcessor<String, *>? = null,
        val children: MutableList<StreamGroup<*>> = mutableListOf(),
        val continuesPrevious: Boolean = false,
) {
    /**
     * 收集这个组的流数据
     * @param collector 对每个发射的值调用的函数
     */
    suspend fun collect(collector: suspend (String) -> Unit) {
        StreamLogger.d("StreamGroup", "开始收集组[$tag]的元素")
        stream.collect { value ->
            StreamLogger.v("StreamGroup", "组[$tag]收集到元素: $value")
            collector(value)
        }
        StreamLogger.d("StreamGroup", "完成组[$tag]的收集")
    }

    /**
     * 使用StreamCollector收集这个组的流数据
     * @param collector 用于收集值的StreamCollector
     */
    suspend fun collect(collector: StreamCollector<String>) {
        StreamLogger.d("StreamGroup", "开始使用StreamCollector收集组[$tag]的元素")
        stream.collect(collector)
    }

    /**
     * 添加子分组
     * @param child 要添加的子分组
     * @return 这个StreamGroup实例，用于链式调用
     */
    fun addChild(child: StreamGroup<*>): StreamGroup<TAG> {
        children.add(child)
        return this
    }

    /**
     * 递归处理当前组和所有子组
     * @param action 要对每个组执行的操作
     */
    suspend fun processRecursively(action: suspend (StreamGroup<*>) -> Unit) {
        action(this)
        children.forEach { it.processRecursively(action) }
    }

    /**
     * 使用绑定的处理器处理这个流
     * @return 处理结果，如果没有绑定处理器则返回null
     */
    suspend fun <R> processWithBoundProcessor(): R? {
        @Suppress("UNCHECKED_CAST") return processor?.process(stream) as? R
    }

    /**
     * 将这个流组转换为标签和流的Pair
     * @return 标签和流的Pair
     */
    fun toPair(): Pair<TAG, Stream<String>> = tag to stream

    override fun toString(): String = "StreamGroup(tag=$tag, childrenCount=${children.size})"
}

/** StreamGroupBuilder - 用于构建嵌套的StreamGroup结构 */
class StreamGroupBuilder<TAG> {
    private var tag: TAG? = null
    private var stream: Stream<String>? = null
    private var processor: StreamProcessor<String, *>? = null
    private val children = mutableListOf<StreamGroup<*>>()

    /** 设置组的标签 */
    fun tag(tag: TAG): StreamGroupBuilder<TAG> {
        this.tag = tag
        return this
    }

    /** 设置组的数据流 */
    fun stream(stream: Stream<String>): StreamGroupBuilder<TAG> {
        this.stream = stream
        return this
    }

    /** 设置组的处理器 */
    fun processor(processor: StreamProcessor<String, *>): StreamGroupBuilder<TAG> {
        this.processor = processor
        return this
    }

    /** 添加子组 */
    fun addChild(child: StreamGroup<*>): StreamGroupBuilder<TAG> {
        children.add(child)
        return this
    }

    /** 使用嵌套构建器添加子组 */
    fun <CHILD_TAG> child(init: StreamGroupBuilder<CHILD_TAG>.() -> Unit): StreamGroupBuilder<TAG> {
        val childBuilder = StreamGroupBuilder<CHILD_TAG>().apply(init)
        val childGroup = childBuilder.build(null)
        children.add(childGroup)
        return this
    }

    /**
     * 根据配置构建StreamGroup
     * @param context 应用上下文（用于本地化错误消息）
     */
    fun build(context: Context? = null): StreamGroup<TAG> {
        requireNotNull(tag) {
            context?.getString(R.string.stream_group_tag_must_be_set) ?: "标签必须设置"
        }
        requireNotNull(stream) {
            context?.getString(R.string.stream_group_stream_must_be_set) ?: "数据流必须设置"
        }

        return StreamGroup(
                tag = tag!!,
                stream = stream!!,
                processor = processor,
                children = children.toMutableList()
        )
    }
}

/** 创建StreamGroup的便捷扩展函数 */
fun <TAG> streamGroup(init: StreamGroupBuilder<TAG>.() -> Unit): StreamGroup<TAG> {
    return StreamGroupBuilder<TAG>().apply(init).build(null)
}

/** 创建嵌套结构的StreamGroup的便捷扩展函数 */
fun <TAG> Stream<String>.asNestedGroup(
        tag: TAG,
        processor: StreamProcessor<String, *>? = null,
        init: (StreamGroupBuilder<TAG>.() -> Unit)? = null
): StreamGroup<TAG> {
    val builder = StreamGroupBuilder<TAG>().tag(tag).stream(this)

    processor?.let { builder.processor(it) }
    init?.let { builder.apply(it) }

    return builder.build(null)
}

/** 将Pair<TAG, Stream<String>>转换为StreamGroup的扩展函数 */
fun <TAG> Pair<TAG, Stream<String>>.asStreamGroup(
        processor: StreamProcessor<String, *>? = null
): StreamGroup<TAG> = StreamGroup(first, second, processor)


class StreamInterceptor<T, R>(
    sourceStream: Stream<T>,
    private var onEach: (T) ->  R
) {
    // 下游流，用于向外部提供数据
    val interceptedStream: Stream<R> = stream { 
        // 收集上游流的数据并转发
        sourceStream.collect { value ->
            emit(onEach(value))
        }
        AppLogger.d("StreamInterceptor", "上游流收集完成")
    }

    fun setOnEach(onEach: (T) -> R) {
        this.onEach = onEach
    }
}