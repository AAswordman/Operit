package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.os.Environment
import com.ai.assistance.llama.LlamaSession
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class LlamaProvider(
    private val context: Context,
    private val modelName: String,
    private val sessionConfig: LlamaSession.Config,
    private val providerType: ApiProviderType = ApiProviderType.LLAMA_CPP,
    private val enableToolCall: Boolean = false
) : AIService {

    companion object {
        private const val TAG = "LlamaProvider"

        fun getModelsDir(): File {
            return File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Operit/models/llama"
            )
        }

        fun getModelFile(_context: Context, modelName: String): File {
            return File(getModelsDir(), modelName)
        }
    }

    private var _inputTokenCount: Long = 0L
    private var _outputTokenCount: Long = 0L
    private var _cachedInputTokenCount: Long = 0L

    @Volatile
    private var isCancelled = false

    private val sessionLock = Any()
    private var session: LlamaSession? = null

    override val inputTokenCount: Long
        get() = _inputTokenCount

    override val cachedInputTokenCount: Long
        get() = _cachedInputTokenCount

    override val outputTokenCount: Long
        get() = _outputTokenCount

    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    override fun resetTokenCounts() {
        _inputTokenCount = 0L
        _outputTokenCount = 0L
        _cachedInputTokenCount = 0L
    }

    private fun logLargeString(prefix: String, message: String) {
        val maxLogSize = 3000
        if (message.length <= maxLogSize) {
            AppLogger.d(TAG, "$prefix$message")
            return
        }

        val chunkCount = (message.length + maxLogSize - 1) / maxLogSize
        for (index in 0 until chunkCount) {
            val start = index * maxLogSize
            val end = minOf((index + 1) * maxLogSize, message.length)
            val chunk = message.substring(start, end)
            AppLogger.d(TAG, "$prefix Part ${index + 1}/$chunkCount: $chunk")
        }
    }

    private fun logFinalOutput(content: CharSequence, prefix: String = "Final llama.cpp output: ") {
        val finalOutput = content.toString()
        if (finalOutput.isBlank()) {
            AppLogger.d(TAG, "${prefix.trimEnd()}[empty]")
            return
        }
        logLargeString(prefix, finalOutput)
    }

    override fun cancelStreaming() {
        isCancelled = true
        synchronized(sessionLock) {
            session?.cancel()
        }
    }

    override fun release() {
        synchronized(sessionLock) {
            session?.release()
            session = null
        }
    }

    override suspend fun getModelsList(context: Context): Result<List<ModelOption>> {
        return ModelListFetcher.getLlamaLocalModels(context)
    }

    override suspend fun testConnection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        if (!LlamaSession.isAvailable()) {
            return@withContext Result.failure(Exception(LlamaSession.getUnavailableReason()))
        }

        val modelFile = getModelFile(context, modelName)
        if (!modelFile.exists()) {
            return@withContext Result.failure(Exception(context.getString(R.string.llama_error_model_file_not_exist, modelFile.absolutePath)))
        }

        val testSession = LlamaSession.create(
            pathModel = modelFile.absolutePath,
            config = sessionConfig
        ) ?: return@withContext Result.failure(Exception(context.getString(R.string.llama_error_create_session_failed)))

        testSession.release()
        Result.success("llama.cpp backend is available (native ready).")
    }

    override suspend fun calculateInputTokens(
        chatHistory: List<PromptTurn>,
        availableTools: List<ToolPrompt>?
    ): Long {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val s = ensureSessionLocked()
                if (s == null) return@runCatching null

                val prompt = if (shouldUseToolCall(availableTools)) {
                    val messagesJson = StructuredToolCallBridge.buildMessagesJson(
                        history = chatHistory,
                        preserveThinkInHistory = false
                    )
                    val toolsJson = StructuredToolCallBridge.buildToolsJson(availableTools)
                    s.applyStructuredChatTemplate(messagesJson, toolsJson, true, true)
                } else {
                    val (roles, contents) = buildPlainPromptMessages(
                        chatHistory = chatHistory,
                        preserveThinkInHistory = false
                    )
                    s.applyChatTemplate(roles, contents, true, true)
                } ?: return@runCatching null

                s.countTokens(prompt).toLong()
            }.getOrNull() ?: 0L
        }
    }

    override suspend fun sendMessage(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
        onTokensUpdated: suspend (input: Long, cachedInput: Long, output: Long) -> Unit,
        onUsageReported: (suspend (com.ai.assistance.operit.data.stats.ProviderUsageSnapshot, attempt: Int) -> Unit)?,
        onNonFatalError: suspend (error: String) -> Unit,
        enableRetry: Boolean,
        recordTokenUsage: Boolean,
        onUsageFinalized: (suspend (attempt: Int?) -> Unit)?,
    ): Stream<String> = stream {
        isCancelled = false

        if (!LlamaSession.isAvailable()) {
            throw IOException("${context.getString(R.string.llama_error_prefix)}: ${LlamaSession.getUnavailableReason()}")
        }

        val modelFile = getModelFile(context, modelName)
        if (!modelFile.exists()) {
            throw IOException("${context.getString(R.string.llama_error_prefix)}: ${context.getString(R.string.llama_error_model_file_not_exist, modelFile.absolutePath)}")
        }

        val s = withContext(Dispatchers.IO) {
            ensureSessionLocked()
        }
        if (s == null) {
            throw IOException(context.getString(R.string.llama_error_session_create_failed))
        }

        val effectiveEnableToolCall = shouldUseToolCall(availableTools)

        if (effectiveEnableToolCall) {
            AppLogger.d(TAG, "llama.cpp Tool Call转换已启用，tools=${availableTools?.size ?: 0}")
        }

        val prompt = withContext(Dispatchers.IO) {
            if (effectiveEnableToolCall) {
                val messagesJson = StructuredToolCallBridge.buildMessagesJson(
                    history = chatHistory,
                    preserveThinkInHistory = preserveThinkInHistory
                )
                val toolsJson = StructuredToolCallBridge.buildToolsJson(availableTools)
                s.applyStructuredChatTemplate(messagesJson, toolsJson, enableThinking, true)
            } else {
                val (roles, contents) = buildPlainPromptMessages(
                    chatHistory = chatHistory,
                    preserveThinkInHistory = preserveThinkInHistory
                )
                s.applyChatTemplate(roles, contents, enableThinking, true)
            }
        }
        if (prompt.isNullOrBlank()) {
            throw IOException(context.getString(R.string.llama_error_chat_template_failed))
        }

        logLargeString("Final prompt before llama generation: ", prompt)

        val temperature = modelParameters
            .firstOrNull { it.id == "temperature" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 1.0f
        val topP = modelParameters
            .firstOrNull { it.id == "top_p" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 1.0f
        val topK = modelParameters
            .firstOrNull { it.id == "top_k" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toInt() }
            ?: 0
        val repetitionPenalty = modelParameters
            .firstOrNull { it.id == "repetition_penalty" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 1.0f
        val frequencyPenalty = modelParameters
            .firstOrNull { it.id == "frequency_penalty" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 0.0f
        val presencePenalty = modelParameters
            .firstOrNull { it.id == "presence_penalty" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 0.0f

        withContext(Dispatchers.IO) {
            kotlin.runCatching {
                s.setSamplingParams(
                    temperature = temperature,
                    topP = topP,
                    topK = topK,
                    repetitionPenalty = repetitionPenalty,
                    frequencyPenalty = frequencyPenalty,
                    presencePenalty = presencePenalty,
                    penaltyLastN = 64
                )
            }

            if (!effectiveEnableToolCall) {
                kotlin.runCatching {
                    s.clearToolCallGrammar()
                    Unit
                }.onFailure {
                    AppLogger.w(TAG, "清理llama.cpp原生Tool Call状态失败", it)
                }
            }
        }

        _inputTokenCount = kotlin.runCatching { s.countTokens(prompt).toLong() }.getOrElse { 0L }
        _outputTokenCount = 0L
        onTokensUpdated(_inputTokenCount, 0L, 0L)

        val requestedMaxNewTokens = modelParameters
            .find { it.name == "max_tokens" }
            ?.let { (it.currentValue as? Number)?.toInt() }
            ?: -1

        AppLogger.d(
            TAG,
            "开始llama.cpp推理，history=${chatHistory.size}, threads=${sessionConfig.nThreads}, n_ctx=${sessionConfig.nCtx}, n_batch=${sessionConfig.nBatch}, n_ubatch=${sessionConfig.nUBatch}, gpu_layers=${sessionConfig.nGpuLayers}, mmap=${sessionConfig.useMmap}"
        )

        var outputTokenCount = 0L
        val toolCallOutputBuffer = StringBuilder()
        val finalOutputBuffer = StringBuilder()

        val usageReporter = LocalUsageReporter(
            com.ai.assistance.operit.data.stats.ProviderUsageNormalizer.SOURCE_LLAMA,
            onUsageReported,
        )
        val success = withContext(Dispatchers.IO) {
                s.generateStream(prompt, requestedMaxNewTokens) { token ->
                    if (isCancelled) {
                        false
                    } else {
                        outputTokenCount += 1L
                        _outputTokenCount = outputTokenCount

                        if (effectiveEnableToolCall) {
                            toolCallOutputBuffer.append(token)
                        } else {
                            finalOutputBuffer.append(token)
                            runBlocking { emit(token) }
                        }

                        kotlin.runCatching {
                            kotlinx.coroutines.runBlocking {
                                onTokensUpdated(_inputTokenCount, 0L, _outputTokenCount)
                            }
                        }

                        true
                    }
                }
            }

        LocalGenerationEnd.end(
            cancelled = isCancelled,
            success = success,
            usageReporter = usageReporter,
            inputTokens = _inputTokenCount,
            outputTokens = _outputTokenCount,
            cancelMessage = context.getString(R.string.llama_error_request_cancelled),
            emitToolResult = {
                if (effectiveEnableToolCall) {
                    val normalizedPayload = withContext(Dispatchers.IO) {
                        kotlin.runCatching {
                            s.parseToolCallResponse(toolCallOutputBuffer.toString())
                        }.getOrNull()
                    }
                    val converted = StructuredToolCallBridge.convertToolCallPayloadToXml(
                        normalizedPayload ?: toolCallOutputBuffer.toString()
                    )
                    if (converted.isNotBlank()) {
                        finalOutputBuffer.append(converted)
                        emit(converted)
                    }
                }
            },
            failWith = {
                kotlin.runCatching {
                    onNonFatalError(context.getString(R.string.llama_error_inference_failed))
                }
                throw IOException(context.getString(R.string.llama_error_inference_failed))
            },
        )
        onUsageFinalized?.invoke(1)

        AppLogger.i(TAG, "llama.cpp推理完成，输出token数: $_outputTokenCount")
        logFinalOutput(finalOutputBuffer, "Final llama.cpp output summary: ")
    }

    private fun shouldUseToolCall(availableTools: List<ToolPrompt>?): Boolean {
        return enableToolCall && !availableTools.isNullOrEmpty()
    }

    private fun buildPlainPromptMessages(
        chatHistory: List<PromptTurn>,
        preserveThinkInHistory: Boolean
    ): Pair<List<String>, List<String>> {
        val normalizedHistory =
            chatHistory.map { turn ->
                val role =
                    when (turn.kind) {
                        PromptTurnKind.SYSTEM -> "system"
                        PromptTurnKind.USER,
                        PromptTurnKind.SUMMARY,
                        PromptTurnKind.TOOL_RESULT -> "user"
                        PromptTurnKind.ASSISTANT,
                        PromptTurnKind.TOOL_CALL -> "assistant"
                    }
                val content =
                    if (!preserveThinkInHistory && turn.kind == PromptTurnKind.ASSISTANT) {
                        ChatUtils.removeThinkingContent(turn.content)
                    } else {
                        turn.content
                    }
                role to content
            }
        val roles = ArrayList<String>(normalizedHistory.size)
        val contents = ArrayList<String>(normalizedHistory.size)

        for ((role, content) in normalizedHistory) {
            roles.add(role)
            contents.add(content)
        }

        return roles to contents
    }

    private fun ensureSessionLocked(): LlamaSession? {
        synchronized(sessionLock) {
            session?.let { return it }
            val modelFile = getModelFile(context, modelName)
            val created = LlamaSession.create(
                pathModel = modelFile.absolutePath,
                config = sessionConfig
            )
            session = created
            return created
        }
    }

}
