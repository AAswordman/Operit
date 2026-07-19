package com.ai.assistance.operit.util.markdown

import androidx.compose.runtime.Stable

/**
 * 智能字符串类 - 高性能的字符串构建器，带缓存优化
 * 
 * 特性：
 * 1. 重载 `+` 操作符为高效的 append 操作
 * 2. 缓存 toString() 结果，只在内容变化时重新生成
 * 3. 避免频繁创建新的 String 对象
 * 4. 适用于流式内容构建场景
 */
class SmartString(initialContent: String = "") {
    private val builder = StringBuilder(initialContent)
    private var cachedString: String? = if (initialContent.isEmpty()) "" else initialContent
    private var lastLength = initialContent.length
    
    /**
     * 追加字符串内容
     * @param content 要追加的内容
     * @return 返回自身以支持链式调用
     */
    operator fun plus(content: String): SmartString {
        if (content.isNotEmpty()) {
            builder.append(content)
            cachedString = null // 标记缓存失效
        }
        return this
    }
    
    /**
     * 追加字符内容
     * @param char 要追加的字符
     * @return 返回自身以支持链式调用
     */
    operator fun plus(char: Char): SmartString {
        builder.append(char)
        cachedString = null // 标记缓存失效
        return this
    }
    
    /**
     * 智能 toString - 只在内容变化时重新生成字符串
     * @return 字符串表示
     */
    override fun toString(): String {
        val currentLength = builder.length
        
        // 如果长度没变且有缓存，直接返回缓存
        if (currentLength == lastLength && cachedString != null) {
            return cachedString!!
        }
        
        // 内容已变化，重新生成字符串并缓存
        lastLength = currentLength
        cachedString = builder.toString()
        return cachedString!!
    }
    
    /**
     * 获取当前长度
     */
    val length: Int
        get() = builder.length
    
    /**
     * 判断是否为空
     */
    fun isEmpty(): Boolean = builder.isEmpty()
    
    /**
     * 判断是否非空
     */
    fun isNotEmpty(): Boolean = builder.isNotEmpty()
    
    /**
     * 清空内容
     */
    fun clear() {
        builder.clear()
        cachedString = ""
        lastLength = 0
    }

    /** Truncates accumulated content without creating an immutable String snapshot. */
    fun truncate(length: Int): SmartString {
        require(length in 0..builder.length) {
            "truncate length $length is outside 0..${builder.length}"
        }
        if (length != builder.length) {
            builder.setLength(length)
            cachedString = if (length == 0) "" else null
            lastLength = length
        }
        return this
    }

    /** Replaces accumulated content while preserving this buffer instance. */
    fun replace(content: String): SmartString {
        builder.clear()
        builder.append(content)
        cachedString = content
        lastLength = content.length
        return this
    }
    
    /**
     * 截取字符串
     */
    fun take(n: Int): String {
        return if (n >= builder.length) {
            toString()
        } else {
            builder.substring(0, n)
        }
    }
    
    /**
     * 追加内容（兼容 StringBuilder 风格）
     */
    fun append(content: String): SmartString {
        return this + content
    }
    
    /**
     * 追加字符（兼容 StringBuilder 风格）
     */
    fun append(char: Char): SmartString {
        return this + char
    }
}

