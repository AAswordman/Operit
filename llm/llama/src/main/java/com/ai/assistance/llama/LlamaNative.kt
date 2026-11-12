package com.ai.assistance.llama

object LlamaNative {

    init {
        LlamaLibraryLoader.loadLibraries()
    }

    @JvmStatic external fun nativeIsAvailable(): Boolean

    @JvmStatic external fun nativeGetUnavailableReason(): String

    /**
     * [PATCH 0013] 最近一次失败的详细原因（加载/校验阶段）。
     * nativeCreateSession 返回 0 时调用，用于把「模型文件损坏」这类
     * 具体原因显示到 UI，而不是笼统的"加载失败"。
     */
    @JvmStatic external fun nativeGetLastError(): String

    @JvmStatic
    external fun nativeCreateSession(
        pathModel: String,
        nThreads: Int,
        nCtx: Int,
        nBatch: Int,
        nUBatch: Int,
        nGpuLayers: Int,
        useMmap: Boolean,
        flashAttention: Boolean,
        kvUnified: Boolean,
        offloadKqv: Boolean
    ): Long

    @JvmStatic external fun nativeReleaseSession(sessionPtr: Long)

    @JvmStatic external fun nativeCancel(sessionPtr: Long)

    @JvmStatic external fun nativeCountTokens(sessionPtr: Long, text: String): Int

    @JvmStatic
    external fun nativeSetSamplingParams(
        sessionPtr: Long,
        temperature: Float,
        topP: Float,
        topK: Int,
        repetitionPenalty: Float,
        frequencyPenalty: Float,
        presencePenalty: Float,
        penaltyLastN: Int
    ): Boolean

    @JvmStatic
    external fun nativeApplyChatTemplate(
        sessionPtr: Long,
        roles: Array<String>,
        contents: Array<String>,
        enableThinking: Boolean,
        addAssistant: Boolean
    ): String?

    @JvmStatic
    external fun nativeApplyStructuredChatTemplate(
        sessionPtr: Long,
        messagesJson: String,
        toolsJson: String?,
        enableThinking: Boolean,
        addAssistant: Boolean
    ): String?

    @JvmStatic
    external fun nativeGenerateStream(
        sessionPtr: Long,
        prompt: String,
        maxTokens: Int,
        callback: GenerationCallback
    ): Boolean

    @JvmStatic
    external fun nativeClearToolCallGrammar(sessionPtr: Long): Boolean

    @JvmStatic
    external fun nativeParseToolCallResponse(
        sessionPtr: Long,
        content: String
    ): String?

    interface GenerationCallback {
        fun onToken(token: String): Boolean
    }
}
