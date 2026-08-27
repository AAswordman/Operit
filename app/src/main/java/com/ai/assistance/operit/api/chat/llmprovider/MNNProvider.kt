package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.os.Environment
import android.util.Base64
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.util.FFmpegUtil
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaPoolManager
import com.ai.assistance.mnn.MNNLlmSession
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * MNN本地推理引擎的AI服务实现
 * 使用 MNN 官方 LLM 引擎进行实际推理
 */
class MNNProvider(
    private val context: Context,
    private val modelName: String,  // 模型文件夹名称（如 "Qwen2-1.5B-Instruct-MNN"）
    private val forwardType: Int,
    private val threadCount: Int,
    private val providerType: ApiProviderType = ApiProviderType.MNN,
    private val enableToolCall: Boolean = false,
    private val supportsVision: Boolean = false,
    private val supportsAudio: Boolean = false,
    private val supportsVideo: Boolean = false
) : AIService {

    companion object {
        private const val TAG = "MNNProvider"
        
        /**
         * 根据模型名称获取模型目录路径
         */
        fun getModelDir(_context: Context, modelName: String): String {
            val modelsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Operit/models/mnn"
            )
            return File(modelsDir, modelName).absolutePath
        }
    }

    // MNN LLM Session 实例
    private var llmSession: MNNLlmSession? = null

    private var cachedModelMaxAllTokens: Int? = null
    private var cachedModelIsVisual: Boolean? = null
    private var cachedModelIsAudio: Boolean? = null

    // Token计数
    private var _inputTokenCount = 0L
    private var _outputTokenCount = 0L
    private var _cachedInputTokenCount = 0L

    @Volatile
    private var isCancelled = false

    override val inputTokenCount: Long
        get() = _inputTokenCount

    override val outputTokenCount: Long
        get() = _outputTokenCount

    override val cachedInputTokenCount: Long
        get() = _cachedInputTokenCount

    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    override fun resetTokenCounts() {
        _inputTokenCount = 0L
        _outputTokenCount = 0L
        _cachedInputTokenCount = 0L
    }

    override fun cancelStreaming() {
        isCancelled = true
        
        // 调用底层 native 取消方法，立即中断推理
        llmSession?.cancel()
        
        AppLogger.d(TAG, "已取消MNN推理（已通知底层中断）")
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

    private fun logFinalOutput(content: CharSequence, prefix: String = "Final MNN output: ") {
        val finalOutput = content.toString()
        if (finalOutput.isBlank()) {
            AppLogger.d(TAG, "${prefix.trimEnd()}[empty]")
            return
        }
        logLargeString(prefix, finalOutput)
    }

    /**
     * 初始化 MNN LLM 模型
     */
    private suspend fun initModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (llmSession == null) {
                AppLogger.d(TAG, "初始化MNN LLM模型: $modelName")
                
                // 获取模型目录
                val modelDir = getModelDir(context, modelName)
                AppLogger.d(TAG, "模型目录: $modelDir")
                
                // 检查目录是否存在
                val modelDirFile = File(modelDir)
                if (!modelDirFile.exists() || !modelDirFile.isDirectory) {
                    return@withContext Result.failure(
                        Exception(context.getString(R.string.mnn_model_dir_not_exist, modelDir))
                    )
                }

                // 检查配置文件是否存在
                val configFile = File(modelDir, "llm_config.json")
                if (!configFile.exists()) {
                    return@withContext Result.failure(
                        Exception(context.getString(R.string.mnn_config_not_exist, configFile.absolutePath))
                    )
                }

                // 将 forwardType 映射到 backend_type 字符串
                val backendType = when (forwardType) {
                    0 -> "cpu"
                    3 -> "opencl"
                    4 -> "auto"
                    6 -> "opengl"
                    7 -> "vulkan"
                    else -> {
                        AppLogger.w(TAG, "未知的 forwardType: $forwardType，使用默认 CPU")
                        "cpu"
                    }
                }
                
                AppLogger.d(TAG, "创建MNN LLM会话，后端: $backendType, 线程数: $threadCount")
                
                // Vulkan/OpenCL 后端需要 normal 内存模式以避免 Clone error
                // CPU 后端可以使用 low 内存模式
                val memoryMode = if (backendType in listOf("vulkan", "opencl", "opengl")) {
                    "normal"
                } else {
                    "low"
                }
                
                AppLogger.d(TAG, "内存模式: $memoryMode (后端: $backendType)")
                
                // 创建缓存目录（用于存放 mnn_cachefile.bin 等临时文件）
                val cacheDir = File(context.cacheDir, "mnn_cache")
                if (!cacheDir.exists()) {
                    val created = cacheDir.mkdirs()
                    AppLogger.d(TAG, "创建MNN缓存目录: $created")
                }
                AppLogger.d(TAG, "MNN缓存目录: ${cacheDir.absolutePath}")
                AppLogger.d(TAG, "缓存目录存在: ${cacheDir.exists()}, 可写: ${cacheDir.canWrite()}")
                
                // 创建 LLM Session（配置必须在创建时传入！）
                llmSession = MNNLlmSession.create(
                    modelDir = modelDir,
                    backendType = backendType,
                    threadNum = threadCount,
                    precision = "low",      // 使用低精度以提升性能
                    memory = memoryMode,    // 根据后端选择内存模式
                    tmpPath = cacheDir.absolutePath  // 指定缓存目录
                )
                
                if (llmSession == null) {
                    return@withContext Result.failure(
                        Exception(context.getString(R.string.mnn_cannot_create_session))
                    )
                }

                AppLogger.i(TAG, "MNN LLM模型初始化成功，后端: $backendType")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "初始化MNN LLM模型失败", e)
            Result.failure(e)
        }
    }

    /**
     * 使用 LLM Session 的实际 tokenizer 计算 Token 数
     */
    private suspend fun countTokens(text: String): Int = withContext(Dispatchers.IO) {
        try {
            val session = llmSession ?: return@withContext estimateTokens(text)
            session.countTokens(text)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Token计数失败，使用估算", e)
            estimateTokens(text)
        }
    }

    private fun readModelMaxAllTokens(modelDir: String): Int {
        return try {
            val configFile = File(modelDir, "llm_config.json")
            if (!configFile.exists()) {
                2048
            } else {
                val json = JSONObject(configFile.readText())
                json.optInt("max_all_tokens", 2048)
            }
        } catch (_: Exception) {
            2048
        }
    }

    private data class ModelCapabilities(
        val isVisual: Boolean,
        val isAudio: Boolean
    )

    private fun readModelCapabilities(modelDir: String): ModelCapabilities {
        val cachedVisual = cachedModelIsVisual
        val cachedAudio = cachedModelIsAudio
        if (cachedVisual != null && cachedAudio != null) {
            return ModelCapabilities(isVisual = cachedVisual, isAudio = cachedAudio)
        }

        val caps = try {
            val configFile = File(modelDir, "llm_config.json")
            if (!configFile.exists()) {
                ModelCapabilities(isVisual = false, isAudio = false)
            } else {
                val json = JSONObject(configFile.readText())
                ModelCapabilities(
                    isVisual = json.optBoolean("is_visual", false),
                    isAudio = json.optBoolean("is_audio", false)
                )
            }
        } catch (_: Exception) {
            ModelCapabilities(isVisual = false, isAudio = false)
        }

        cachedModelIsVisual = caps.isVisual
        cachedModelIsAudio = caps.isAudio
        return caps
    }

    private data class MultimodalPreprocessResult(
        val text: String,
        val tempFiles: List<File>
    )

    private fun ensureMultimodalWorkDir(): File {
        val dir = File(context.cacheDir, "mnn_multimodal")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun extForMimeType(mimeType: String): String {
        val mt = mimeType.lowercase().substringBefore(';')
        return when (mt) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/ogg", "audio/opus" -> "ogg"
            "audio/webm" -> "webm"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/ogg" -> "ogv"
            else -> mt.substringAfter('/', "bin").ifBlank { "bin" }
        }
    }

    private fun writeBase64ToTempFile(base64: String, mimeType: String, prefix: String): File? {
        if (base64.isBlank()) return null
        val dir = ensureMultimodalWorkDir()
        val ext = extForMimeType(mimeType)
        val out = File(dir, "${prefix}_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(0, Int.MAX_VALUE)}.$ext")
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            FileOutputStream(out).use { it.write(bytes) }
            out
        } catch (e: Exception) {
            AppLogger.e(TAG, "写入多模态临时文件失败: mimeType=$mimeType", e)
            runCatching { out.delete() }
            null
        }
    }

    private fun transcodeToWav16kMono(input: File): File? {
        val dir = ensureMultimodalWorkDir()
        val out = File(dir, "audio_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(0, Int.MAX_VALUE)}.wav")
        val inPath = "\"" + input.absolutePath.replace("\"", "\\\"") + "\""
        val outPath = "\"" + out.absolutePath.replace("\"", "\\\"") + "\""
        val ok = FFmpegUtil.executeCommand("-y -i $inPath -vn -ac 1 -ar 16000 -f wav $outPath")
        if (!ok || !out.exists() || out.length() <= 0) {
            runCatching { out.delete() }
            return null
        }
        return out
    }

    private fun extractVideoFrame(input: File): File? {
        val dir = ensureMultimodalWorkDir()
        val out = File(dir, "frame_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(0, Int.MAX_VALUE)}.jpg")
        val inPath = "\"" + input.absolutePath.replace("\"", "\\\"") + "\""
        val outPath = "\"" + out.absolutePath.replace("\"", "\\\"") + "\""
        val ok = FFmpegUtil.executeCommand(
            "-y -i $inPath -frames:v 1 -vf ${FFmpegUtil.scaleFilterMaxWidth(640)} $outPath"
        )
        if (!ok || !out.exists() || out.length() <= 0) {
            runCatching { out.delete() }
            return null
        }
        return out
    }

    private fun preprocessMultimodalText(
        raw: String,
        modelDir: String
    ): MultimodalPreprocessResult {
        if (raw.isBlank()) return MultimodalPreprocessResult(raw, emptyList())

        val caps = readModelCapabilities(modelDir)
        val allowVision = supportsVision && caps.isVisual
        val allowAudio = supportsAudio && caps.isAudio
        val allowVideo = supportsVideo && (caps.isAudio || caps.isVisual)

        val tempFiles = mutableListOf<File>()
        val imageCache = mutableMapOf<String, String>()
        val mediaCache = mutableMapOf<String, String>()

        fun imagePathFor(id: String): String? {
            if (id == "error") return null
            imageCache[id]?.let { return it }
            val data = ImagePoolManager.getImage(id) ?: return null
            val file = writeBase64ToTempFile(data.base64, data.mimeType, "img") ?: return null
            tempFiles.add(file)
            val path = file.absolutePath
            imageCache[id] = path
            return path
        }

        fun mediaPathFor(id: String): Pair<String, String>? {
            if (id == "error") return null
            mediaCache[id]?.let { return it to "" }
            val data = MediaPoolManager.getMedia(id) ?: return null
            val file = writeBase64ToTempFile(data.base64, data.mimeType, "media") ?: return null
            tempFiles.add(file)
            mediaCache[id] = file.absolutePath
            return file.absolutePath to data.mimeType
        }

        var text = raw

        text = MediaLinkParser.replaceImageLinks(text) { id ->
            if (!allowVision) {
                ""
            } else {
                val path = imagePathFor(id)
                if (path == null) "" else "<img>$path</img>"
            }
        }

        text = MediaLinkParser.replaceMediaLinks(text) { type, id ->
            if (type == "audio") {
                if (!allowAudio) return@replaceMediaLinks ""
                val (path, _) = mediaPathFor(id) ?: return@replaceMediaLinks ""
                val file = File(path)
                val wav = transcodeToWav16kMono(file)
                if (wav != null) {
                    tempFiles.add(wav)
                    "<audio>${wav.absolutePath}</audio>"
                } else {
                    "<audio>$path</audio>"
                }
            } else {
                if (!allowVideo) return@replaceMediaLinks ""
                val (path, _) = mediaPathFor(id) ?: return@replaceMediaLinks ""
                val file = File(path)
                val parts = mutableListOf<String>()
                if (caps.isVisual && allowVision) {
                    val frame = extractVideoFrame(file)
                    if (frame != null) {
                        tempFiles.add(frame)
                        parts.add("<img>${frame.absolutePath}</img>")
                    }
                }
                if (caps.isAudio && allowAudio) {
                    val wav = transcodeToWav16kMono(file)
                    if (wav != null) {
                        tempFiles.add(wav)
                        parts.add("<audio>${wav.absolutePath}</audio>")
                    }
                }
                parts.joinToString("")
            }
        }

        return MultimodalPreprocessResult(text = text, tempFiles = tempFiles)
    }

    private fun trimHistoryToTokenBudget(
        session: MNNLlmSession,
        history: List<Pair<String, String>>,
        maxPromptTokens: Int
    ): List<Pair<String, String>> {
        if (history.isEmpty()) return history

        val systemPrefixCount = if (history.first().first == "system") 1 else 0

        val fullTokens = kotlin.runCatching { session.countTokensWithHistory(history) }.getOrDefault(Int.MAX_VALUE)
        if (fullTokens <= maxPromptTokens) return history

        if (history.size <= systemPrefixCount + 1) {
            if (systemPrefixCount == 1) {
                val withoutSystem = history.drop(1)
                val withoutSystemTokens = kotlin.runCatching { session.countTokensWithHistory(withoutSystem) }.getOrDefault(Int.MAX_VALUE)
                if (withoutSystemTokens <= maxPromptTokens) {
                    return withoutSystem
                }
            }
            return history
        }

        var low = systemPrefixCount
        var high = history.size - 1
        while (low < high) {
            val mid = (low + high) / 2
            val candidate = ArrayList<Pair<String, String>>(history.size - (mid - systemPrefixCount))
            if (systemPrefixCount == 1) {
                candidate.add(history[0])
            }
            for (i in mid until history.size) {
                candidate.add(history[i])
            }

            val tokens = kotlin.runCatching { session.countTokensWithHistory(candidate) }.getOrDefault(Int.MAX_VALUE)
            if (tokens > maxPromptTokens) {
                low = mid + 1
            } else {
                high = mid
            }
        }

        val trimmed = ArrayList<Pair<String, String>>(history.size - (low - systemPrefixCount))
        if (systemPrefixCount == 1) {
            trimmed.add(history[0])
        }
        for (i in low until history.size) {
            trimmed.add(history[i])
        }

        if (systemPrefixCount == 1) {
            val trimmedTokens = kotlin.runCatching { session.countTokensWithHistory(trimmed) }.getOrDefault(Int.MAX_VALUE)
            if (trimmedTokens > maxPromptTokens) {
                val withoutSystem = trimmed.drop(1)
                val withoutSystemTokens = kotlin.runCatching { session.countTokensWithHistory(withoutSystem) }.getOrDefault(Int.MAX_VALUE)
                if (withoutSystemTokens <= maxPromptTokens) {
                    return withoutSystem
                }
            }
        }

        return trimmed
    }

    private fun flattenTypedHistory(
        chatHistory: List<PromptTurn>,
        preserveThinkInHistory: Boolean
    ): List<Pair<String, String>> {
        return chatHistory.map { turn ->
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
                    com.ai.assistance.operit.util.ChatUtils.removeThinkingContent(turn.content)
                } else {
                    turn.content
                }
            role to content
        }
    }

    private fun shouldUseInternalToolCall(availableTools: List<ToolPrompt>?): Boolean {
        return enableToolCall && !availableTools.isNullOrEmpty()
    }

    private fun applyRequestJinjaContext(
        session: MNNLlmSession,
        enableThinking: Boolean,
        availableTools: List<ToolPrompt>?,
        useInternalToolCall: Boolean
    ) {
        val toolsArray =
            if (useInternalToolCall) {
                StructuredToolCallBridge.buildToolsArray(availableTools)
            } else {
                JSONArray()
            }

        val contextUpdate =
            JSONObject().apply {
                put("enable_thinking", enableThinking)
                put("tools", toolsArray)
            }

        val configUpdate =
            JSONObject().apply {
                put(
                    "jinja",
                    JSONObject().apply {
                        put("context", contextUpdate)
                    }
                )
            }

        if (!session.setConfig(configUpdate.toString())) {
            throw IllegalStateException("Failed to apply MNN jinja context")
        }

        AppLogger.d(
            TAG,
            "Applied MNN jinja context: thinking=$enableThinking, tools=${toolsArray.length()}"
        )
    }

    /**
     * 估算Token数（备用方法，假设平均4个字符为1个token）
     */
    private fun estimateTokens(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }

    /**
     * 构建完整的提示词（包含历史记录）
     */
    private fun buildPrompt(
        chatHistory: List<Pair<String, String>>
    ): String {
        val promptBuilder = StringBuilder()
        
        // 添加历史记录
        for ((role, content) in chatHistory) {
            when (role.lowercase()) {
                "user" -> promptBuilder.append(context.getString(R.string.mnn_user_prompt, content))
                "assistant" -> promptBuilder.append(context.getString(R.string.mnn_assistant_prompt, content))
                "system" -> promptBuilder.append(context.getString(R.string.mnn_system_prompt, content))
                else -> promptBuilder.append("$role: $content\n")
            }
        }

        promptBuilder.append(context.getString(R.string.mnn_assistant))
        
        return promptBuilder.toString()
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

        val requestTempFiles = mutableListOf<File>()

        try {
            // 初始化模型
            val initResult = initModel()
            if (initResult.isFailure) {
                // Let the caller render the specific initialization failure once.
                throw IOException(initResult.exceptionOrNull()?.message ?: "")
            }

            val session = llmSession ?: throw IOException(context.getString(R.string.mnn_session_not_initialized))

            // 应用模型参数（采样参数）
            applyModelParameters(session, modelParameters)

            if (chatHistory.isEmpty()) {
                AppLogger.d(TAG, "消息为空，跳过处理")
                return@stream
            }

            val useInternalToolCall = shouldUseInternalToolCall(availableTools)
            val modelDir = getModelDir(context, modelName)
            val maxAllTokens = cachedModelMaxAllTokens ?: readModelMaxAllTokens(modelDir).also { cachedModelMaxAllTokens = it }

            val multimodalTurns = chatHistory.map { turn ->
                val processed = preprocessMultimodalText(turn.content, modelDir)
                requestTempFiles.addAll(processed.tempFiles)
                turn.copy(content = processed.text)
            }

            applyRequestJinjaContext(session, enableThinking, availableTools, useInternalToolCall)

            val conversationHistory =
                if (useInternalToolCall) {
                    StructuredToolCallBridge.buildMnnChatHistory(multimodalTurns, preserveThinkInHistory)
                } else {
                    flattenTypedHistory(multimodalTurns, preserveThinkInHistory)
                }

            val requestedMaxNewTokens = modelParameters
                .find { it.name == "max_tokens" }
                ?.let { (it.currentValue as? Number)?.toInt() }
                ?: -1
            val effectiveMaxNewTokens = (if (requestedMaxNewTokens > 0) requestedMaxNewTokens else 512).coerceAtMost(8192)
            val maxPromptTokens = (maxAllTokens - effectiveMaxNewTokens).coerceAtLeast(128)

            val safeHistory = trimHistoryToTokenBudget(session, conversationHistory, maxPromptTokens)

            _inputTokenCount =
                kotlin.runCatching { session.countTokensWithHistory(safeHistory).toLong() }
                    .getOrElse { error ->
                        if (useInternalToolCall) {
                            throw error
                        }
                        countTokens(buildPrompt(conversationHistory)).toLong()
                    }
            onTokensUpdated(_inputTokenCount, 0L, 0L)

            AppLogger.d(
                TAG,
                "开始MNN LLM推理，历史消息数: ${conversationHistory.size}, thinking模式: $enableThinking, toolCall=$useInternalToolCall"
            )

            var outputTokenCount = 0L
            val toolCallOutputBuffer = StringBuilder()
            val finalOutputBuffer = StringBuilder()
            val emitDirectly = !useInternalToolCall
            val usageReporter = LocalUsageReporter(
                com.ai.assistance.operit.data.stats.ProviderUsageNormalizer.SOURCE_MNN,
                onUsageReported,
            )
            val success = session.generateStream(safeHistory, requestedMaxNewTokens) { token ->
                    if (isCancelled) {
                        false
                    } else {
                        outputTokenCount += 1L
                        _outputTokenCount = outputTokenCount

                        if (emitDirectly) {
                            finalOutputBuffer.append(token)
                            runBlocking { emit(token) }
                        } else {
                            toolCallOutputBuffer.append(token)
                        }

                        kotlin.runCatching {
                            kotlinx.coroutines.runBlocking {
                                onTokensUpdated(_inputTokenCount, 0L, _outputTokenCount)
                            }
                        }

                        true
                    }
                }

            LocalGenerationEnd.end(
                cancelled = isCancelled,
                success = success,
                usageReporter = usageReporter,
                inputTokens = _inputTokenCount,
                outputTokens = _outputTokenCount,
                cancelMessage = context.getString(R.string.mnn_error_request_cancelled),
                emitToolResult = {
                    if (useInternalToolCall && toolCallOutputBuffer.isNotEmpty()) {
                        val converted =
                            StructuredToolCallBridge.convertToolCallPayloadToXml(
                                toolCallOutputBuffer.toString()
                            )
                        if (converted.isNotBlank()) {
                            finalOutputBuffer.append(converted)
                            emit(converted)
                        }
                    }
                },
                failWith = {
                    throw IOException(context.getString(R.string.mnn_reasoning_error))
                },
            )
            onUsageFinalized?.invoke(1)

            AppLogger.i(TAG, "MNN LLM推理完成，输出token数: $_outputTokenCount")
            logFinalOutput(finalOutputBuffer, "Final MNN output summary: ")

        } catch (e: CancellationException) {
            // 取消原样传播，不 emit 错误文本
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "发送消息时出错", e)
            throw e
        } finally {
            requestTempFiles.forEach { file ->
                runCatching { file.delete() }
            }
        }
    }

    override suspend fun testConnection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 检查模型名称
            if (modelName.isEmpty()) {
                return@withContext Result.failure(Exception(context.getString(R.string.mnn_model_not_configured)))
            }

            // 获取模型目录
            val modelDir = getModelDir(context, modelName)
            val modelDirFile = File(modelDir)

            if (!modelDirFile.exists() || !modelDirFile.isDirectory) {
                return@withContext Result.failure(
                    Exception(context.getString(R.string.mnn_model_dir_missing, modelDir))
                )
            }

            // 计算模型总大小
            val totalSize = modelDirFile.listFiles()?.sumOf { it.length() } ?: 0L

            // 检查关键文件是否存在
            val modelFile = File(modelDir, "llm.mnn")
            val weightFile = File(modelDir, "llm.mnn.weight")
            val configFile = File(modelDir, "llm_config.json")
            val tokenizerFile = File(modelDir, "tokenizer.txt")

            val fileStatus = buildString {
                appendLine(context.getString(R.string.mnn_file_status))
                appendLine("- llm.mnn: ${if (modelFile.exists()) "✓" else "✗"}")
                appendLine("- llm.mnn.weight: ${if (weightFile.exists()) "✓" else "✗"}")
                appendLine("- llm_config.json: ${if (configFile.exists()) "✓" else "✗"}")
                appendLine("- tokenizer.txt: ${if (tokenizerFile.exists()) "✓" else "✗"}")
            }

            // 尝试初始化模型
            val initResult = initModel()
            if (initResult.isFailure) {
                return@withContext Result.failure(
                    initResult.exceptionOrNull() ?: Exception(context.getString(R.string.mnn_init_failed))
                )
            }

            Result.success(context.getString(R.string.mnn_connection_success, modelName, modelDir, formatFileSize(totalSize), fileStatus))
        } catch (e: Exception) {
            AppLogger.e(TAG, "测试连接失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 应用模型参数到 MNN Session
     * MNN 支持的采样参数：temperature, topP, topK, minP, penalty, tfsZ, typical, nGram 等
     * 
     * 参数映射说明：
     * - temperature: 温度参数，控制输出随机性
     * - top_p -> topP: Top-P 采样（核采样）
     * - top_k -> topK: Top-K 采样
     * - min_p -> minP: Min-P 采样
     * - repetition_penalty/presence_penalty/frequency_penalty -> penalty: 重复惩罚
     * - 自定义参数: 直接传递（如 tfsZ, typical, nGram 等）
     */
    private fun applyModelParameters(session: MNNLlmSession, parameters: List<ModelParameter<*>>) {
        try {
            // 构建配置 JSON（只包含启用的参数）
            val configMap = mutableMapOf<String, Any>()
            
            parameters.filter { it.isEnabled }.forEach { param ->
                when (param.apiName.lowercase()) {
                    "temperature" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["temperature"] = it
                        }
                    }
                    "top_p", "topp" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["topP"] = it  // MNN 使用 topP 而不是 top_p
                        }
                    }
                    "top_k", "topk" -> {
                        (param.currentValue as? Number)?.toInt()?.let { 
                            configMap["topK"] = it  // MNN 使用 topK 而不是 top_k
                        }
                    }
                    "min_p", "minp" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["minP"] = it
                        }
                    }
                    "presence_penalty", "frequency_penalty", "repetition_penalty" -> {
                        // MNN 使用统一的 penalty 参数
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["penalty"] = it
                        }
                    }
                    "max_tokens", "max_new_tokens" -> {
                        // max_tokens 在 generateStream 中单独处理，这里不设置
                        // 但 MNN 也支持 maxNewTokens 配置
                        (param.currentValue as? Number)?.toInt()?.let { 
                            configMap["max_new_tokens"] = it
                        }
                    }
                    // MNN 高级采样参数
                    "tfsz", "tfs_z" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["tfsZ"] = it
                        }
                    }
                    "typical" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["typical"] = it
                        }
                    }
                    "n_gram", "ngram" -> {
                        (param.currentValue as? Number)?.toInt()?.let { 
                            configMap["n_gram"] = it
                        }
                    }
                    "ngram_factor" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["ngram_factor"] = it
                        }
                    }
                    else -> {
                        // 对于自定义参数，尝试直接传递
                        if (param.isCustom) {
                            when (param.valueType) {
                                ParameterValueType.INT -> {
                                    (param.currentValue as? Number)?.toInt()?.let {
                                        configMap[param.apiName] = it
                                    }
                                }
                                ParameterValueType.FLOAT -> {
                                    (param.currentValue as? Number)?.toFloat()?.let {
                                        configMap[param.apiName] = it
                                    }
                                }
                                ParameterValueType.BOOLEAN -> {
                                    (param.currentValue as? Boolean)?.let {
                                        configMap[param.apiName] = it
                                    }
                                }
                                ParameterValueType.STRING -> {
                                    configMap[param.apiName] = param.currentValue.toString()
                                }
                                ParameterValueType.OBJECT -> {
                                    val raw = param.currentValue.toString().trim()
                                    val parsed: Any? = try {
                                        when {
                                            raw.startsWith("{") -> JSONObject(raw)
                                            raw.startsWith("[") -> JSONArray(raw)
                                            else -> null
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.w(TAG, "自定义OBJECT参数解析失败: ${param.apiName}", e)
                                        null
                                    }
                                    if (parsed != null) {
                                        configMap[param.apiName] = parsed
                                    } else {
                                        // 解析失败时回退为字符串，避免崩溃
                                        configMap[param.apiName] = raw
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (configMap.isNotEmpty()) {
                // 将 Map 转换为 JSON 字符串
                val configJson = buildString {
                    append("{")
                    configMap.entries.forEachIndexed { index, entry ->
                        if (index > 0) append(",")
                        append("\"${entry.key}\":")
                        when (val value = entry.value) {
                            is JSONObject -> append(value.toString())
                            is JSONArray -> append(value.toString())
                            is String -> append("\"$value\"")
                            is Number -> append(value)
                            is Boolean -> append(value)
                            else -> append("\"$value\"")
                        }
                    }
                    append("}")
                }
                
                AppLogger.d(TAG, "应用模型参数: $configJson")
                val success = session.setConfig(configJson)
                if (!success) {
                    AppLogger.w(TAG, "部分模型参数设置失败")
                }
            } else {
                AppLogger.d(TAG, "没有启用的模型参数需要应用")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "应用模型参数时出错", e)
        }
    }
    
    /**
     * 应用硬件后端配置（backend_type 和 thread_num）
     * 将用户在 UI 中选择的 forwardType 和 threadCount 应用到 MNN Session
     * 
     * forwardType 映射:
     * - 0 -> "cpu"
     * - 3 -> "opencl" 
     * - 4 -> "auto"
     * - 6 -> "opengl"
     * - 7 -> "vulkan"
     */
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format("%.2f KB", sizeBytes / 1024.0)
            sizeBytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    override suspend fun calculateInputTokens(
        chatHistory: List<PromptTurn>,
        availableTools: List<ToolPrompt>?
    ): Long {
        val flattenedHistory = flattenTypedHistory(chatHistory, preserveThinkInHistory = false)
        val initResult = initModel()
        if (initResult.isFailure) {
            val prompt = buildPrompt(flattenedHistory)
            return countTokens(prompt).toLong()
        }
        val session = llmSession ?: run {
            val prompt = buildPrompt(flattenedHistory)
            return countTokens(prompt).toLong()
        }
        val modelDir = getModelDir(context, modelName)
        val maxAllTokens = cachedModelMaxAllTokens ?: readModelMaxAllTokens(modelDir).also { cachedModelMaxAllTokens = it }
        val maxPromptTokens = (maxAllTokens - 512).coerceAtLeast(128)
        val safeHistory = trimHistoryToTokenBudget(session, flattenedHistory, maxPromptTokens)
        return kotlin.runCatching { session.countTokensWithHistory(safeHistory).toLong() }
            .getOrElse {
                val prompt = buildPrompt(flattenedHistory)
                countTokens(prompt).toLong()
            }
    }

    override suspend fun getModelsList(context: Context): Result<List<ModelOption>> {
        // MNN使用本地模型，从固定目录读取已下载的模型
        return ModelListFetcher.getMnnLocalModels(context)
    }

    /**
     * 释放资源
     * 释放MNN模型占用的native内存和相关资源
     */
    override fun release() {
        try {
            llmSession?.release()
            llmSession = null
            AppLogger.d(TAG, "MNN LLM资源已释放")
        } catch (e: Exception) {
            AppLogger.e(TAG, "释放资源时出错", e)
        }
    }
}

