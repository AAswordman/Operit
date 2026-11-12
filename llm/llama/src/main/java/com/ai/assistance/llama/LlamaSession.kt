package com.ai.assistance.llama

class LlamaSession private constructor(
    private var sessionPtr: Long
) {

    data class Config(
        val nThreads: Int = 4,
        val nCtx: Int = 2048,
        val nBatch: Int = 512,
        val nUBatch: Int = 512,
        val nGpuLayers: Int = 0,
        val useMmap: Boolean = false,
        val flashAttention: Boolean = false,
        val kvUnified: Boolean = true,
        val offloadKqv: Boolean = false
    )

    companion object {
        fun isAvailable(): Boolean = runCatching { LlamaNative.nativeIsAvailable() }.getOrDefault(false)

        fun getUnavailableReason(): String = runCatching { LlamaNative.nativeGetUnavailableReason() }
            .getOrDefault("llama.cpp backend unavailable")

        fun create(
            pathModel: String,
            config: Config
        ): LlamaSession? {
            if (!isAvailable()) return null
            // [REVIEW P3] lastError 是 companion 级状态，两个线程同时 create() 时
            // 后进者会把先进者的错误覆盖掉。原生侧本来就用 gInferenceMutex 串行化
            // 会话创建，这里再加一把 JVM 锁不增加成本，只保证错误不被串台。
            synchronized(createLock) {
                val ptr = LlamaNative.nativeCreateSession(
                    pathModel = pathModel,
                    nThreads = config.nThreads,
                    nCtx = config.nCtx,
                    nBatch = config.nBatch,
                    nUBatch = config.nUBatch,
                    nGpuLayers = config.nGpuLayers,
                    useMmap = config.useMmap,
                    flashAttention = config.flashAttention,
                    kvUnified = config.kvUnified,
                    offloadKqv = config.offloadKqv
                )
                if (ptr == 0L) {
                    // [PATCH 0013] 把具体失败原因带出去，避免上层只能看到笼统的 null。
                    val reason = runCatching { LlamaNative.nativeGetLastError() }.getOrDefault("")
                    lastError = reason.ifBlank { "session creation failed" }
                    return null
                }
                lastError = null
                return LlamaSession(ptr)
            }
        }

        /** 最近一次 create() 失败的原因；成功时为 null。 */
        @Volatile
        var lastError: String? = null
            private set

        /** [REVIEW P3] 保护 lastError 不被并发 create() 互相覆盖。 */
        private val createLock = Any()
    }

    @Volatile
    private var released = false

    private val lock = Any()

    private fun checkValid() {
        if (released || sessionPtr == 0L) {
            throw RuntimeException("LlamaSession has been released")
        }
    }

    fun countTokens(text: String): Int {
        synchronized(lock) {
            checkValid()
            return LlamaNative.nativeCountTokens(sessionPtr, text)
        }
    }

    fun generateStream(prompt: String, maxTokens: Int, onToken: (String) -> Boolean): Boolean {
        val ptr: Long
        synchronized(lock) {
            checkValid()
            ptr = sessionPtr
        }

        return LlamaNative.nativeGenerateStream(
            ptr,
            prompt,
            maxTokens,
            object : LlamaNative.GenerationCallback {
                override fun onToken(token: String): Boolean = onToken(token)
            }
        )
    }

    fun applyStructuredChatTemplate(
        messagesJson: String,
        toolsJson: String?,
        enableThinking: Boolean,
        addAssistant: Boolean
    ): String? {
        val ptr: Long
        synchronized(lock) {
            checkValid()
            ptr = sessionPtr
        }

        return LlamaNative.nativeApplyStructuredChatTemplate(
            ptr,
            messagesJson,
            toolsJson,
            enableThinking,
            addAssistant
        )
    }

    fun clearToolCallGrammar(): Boolean {
        val ptr: Long
        synchronized(lock) {
            checkValid()
            ptr = sessionPtr
        }

        return LlamaNative.nativeClearToolCallGrammar(ptr)
    }

    fun parseToolCallResponse(content: String): String? {
        val ptr: Long
        synchronized(lock) {
            checkValid()
            ptr = sessionPtr
        }

        return LlamaNative.nativeParseToolCallResponse(ptr, content)
    }

    fun applyChatTemplate(
        roles: List<String>,
        contents: List<String>,
        enableThinking: Boolean,
        addAssistant: Boolean
    ): String? {
        val ptr: Long
        synchronized(lock) {
            checkValid()
            ptr = sessionPtr
        }

        return LlamaNative.nativeApplyChatTemplate(
            ptr,
            roles.toTypedArray(),
            contents.toTypedArray(),
            enableThinking,
            addAssistant
        )
    }

    fun setSamplingParams(
        temperature: Float,
        topP: Float,
        topK: Int,
        repetitionPenalty: Float,
        frequencyPenalty: Float,
        presencePenalty: Float,
        penaltyLastN: Int = 64
    ): Boolean {
        val ptr: Long
        synchronized(lock) {
            checkValid()
            ptr = sessionPtr
        }

        return LlamaNative.nativeSetSamplingParams(
            ptr,
            temperature,
            topP,
            topK,
            repetitionPenalty,
            frequencyPenalty,
            presencePenalty,
            penaltyLastN
        )
    }

    fun cancel() {
        synchronized(lock) {
            if (released || sessionPtr == 0L) return
            LlamaNative.nativeCancel(sessionPtr)
        }
    }

    fun release() {
        val ptr: Long
        synchronized(lock) {
            if (released) return
            released = true
            ptr = sessionPtr
            sessionPtr = 0L
        }
        if (ptr != 0L) {
            LlamaNative.nativeReleaseSession(ptr)
        }
    }
}
