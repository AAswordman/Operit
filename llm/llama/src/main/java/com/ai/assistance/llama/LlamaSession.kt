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
            if (ptr == 0L) return null
            return LlamaSession(ptr)
        }
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
