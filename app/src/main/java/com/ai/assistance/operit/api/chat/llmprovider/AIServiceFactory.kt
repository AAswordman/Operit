package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.llama.LlamaSession
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.plugins.toolpkg.ToolPkgAiProviderRegistry
import com.ai.assistance.operit.util.AppLogger
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/**
 * A factory for creating and managing a shared OkHttpClient instance.
 * Using a shared client allows for efficient reuse of connections and resources.
 */
internal data class LlmRequestTraceContext(
    val requestId: String,
    val provider: String,
    val model: String,
    val stream: Boolean,
    val attempt: Int,
    val endpointLabel: String
)

private object LlmNetworkEventListenerFactory : EventListener.Factory {
    override fun create(call: Call): EventListener {
        return LlmNetworkEventListener(call.request().tag(LlmRequestTraceContext::class.java))
    }
}

private class LlmNetworkEventListener(
    private val traceContext: LlmRequestTraceContext?
) : EventListener() {
    private val startedAtNs = System.nanoTime()

    private fun elapsedMs(): Long = (System.nanoTime() - startedAtNs) / 1_000_000

    private fun prefix(): String {
        val requestId = traceContext?.requestId ?: "unknown"
        val provider = traceContext?.provider ?: "unknown"
        val model = traceContext?.model ?: "unknown"
        val attempt = traceContext?.attempt ?: -1
        val stream = traceContext?.stream ?: false
        return "[req=$requestId provider=$provider model=$model attempt=$attempt stream=$stream]"
    }

    private fun log(stage: String, details: String = "") {
        val suffix = if (details.isBlank()) "" else " | $details"
        AppLogger.d("AIHttpTrace", "${prefix()} +${elapsedMs()}ms $stage$suffix")
    }

    private fun logFailure(stage: String, error: IOException, details: String = "") {
        val message = buildString {
            append("${prefix()} +${elapsedMs()}ms $stage")
            if (details.isNotBlank()) {
                append(" | ")
                append(details)
            }
            append(" | ")
            append(error.javaClass.simpleName)
            append(": ")
            append(error.message ?: "no message")
        }
        AppLogger.e("AIHttpTrace", message, error)
    }

    private fun formatSocketAddress(socketAddress: InetSocketAddress): String {
        return "${socketAddress.hostString}:${socketAddress.port}"
    }

    private fun formatAddresses(addresses: List<InetAddress>): String {
        return addresses.joinToString(",") { it.hostAddress ?: it.hostName }
    }

    private fun describeRequest(request: Request): String {
        val bodyBytes = runCatching { request.body?.contentLength() ?: -1L }.getOrDefault(-1L)
        return "${request.method} ${request.url.scheme}://${request.url.host}:${request.url.port}${request.url.encodedPath}, bodyBytes=$bodyBytes"
    }

    override fun callStart(call: Call) {
        log("callStart", describeRequest(call.request()))
    }

    override fun dnsStart(call: Call, domainName: String) {
        log("dnsStart", "host=$domainName")
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        log("dnsEnd", "host=$domainName, addresses=${formatAddresses(inetAddressList)}")
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        log("connectStart", "target=${formatSocketAddress(inetSocketAddress)}, proxy=${proxy.type()}")
    }

    override fun secureConnectStart(call: Call) {
        log("secureConnectStart")
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        val tlsVersion = handshake?.tlsVersion?.javaName ?: "unknown"
        val cipherSuite = handshake?.cipherSuite?.javaName ?: "unknown"
        log("secureConnectEnd", "tls=$tlsVersion, cipher=$cipherSuite")
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        log(
            "connectEnd",
            "target=${formatSocketAddress(inetSocketAddress)}, protocol=${protocol ?: "unknown"}"
        )
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        logFailure(
            "connectFailed",
            ioe,
            "target=${formatSocketAddress(inetSocketAddress)}, proxy=${proxy.type()}, protocol=${protocol ?: "unknown"}"
        )
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        val route = runCatching { formatSocketAddress(connection.route().socketAddress) }.getOrDefault("unknown")
        log("connectionAcquired", "route=$route, protocol=${connection.protocol()}")
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        val route = runCatching { formatSocketAddress(connection.route().socketAddress) }.getOrDefault("unknown")
        log("connectionReleased", "route=$route, protocol=${connection.protocol()}")
    }

    override fun requestHeadersStart(call: Call) {
        log("requestHeadersStart")
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        log("requestHeadersEnd", describeRequest(request))
    }

    override fun requestBodyStart(call: Call) {
        log("requestBodyStart")
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        log("requestBodyEnd", "bytes=$byteCount")
    }

    override fun responseHeadersStart(call: Call) {
        log("responseHeadersStart")
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        log("responseHeadersEnd", "code=${response.code}, message=${response.message}")
    }

    override fun responseBodyStart(call: Call) {
        log("responseBodyStart")
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        log("responseBodyEnd", "bytes=$byteCount")
    }

    override fun callEnd(call: Call) {
        log("callEnd")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        logFailure("callFailed", ioe)
    }
}

private object SharedHttpClient {
    val instance: OkHttpClient by lazy {
        UnsafeModelSsl.apply(
            OkHttpClient.Builder()
                // Increase the connection timeout to handle slow networks better.
                .connectTimeout(60, TimeUnit.SECONDS)
                // Set long read/write timeouts for streaming responses.
                .readTimeout(1000, TimeUnit.SECONDS)
                .writeTimeout(1000, TimeUnit.SECONDS)
                .eventListenerFactory(LlmNetworkEventListenerFactory)
                // Use a connection pool to reuse connections, improving latency and reducing resource usage.
                // Increased idle connections to 10 from the default of 5.
                .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                // Explicitly enable HTTP/2, which is the default but good to have declared.
                // OkHttp will use HTTP/2 if the server supports it, falling back to HTTP/1.1.
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        )
            .build()
    }
}

/** AI服务工厂，根据提供商类型创建相应的AIService实例 */
object AIServiceFactory {

    /**
     * 解析自定义请求头的JSON字符串为Map
     */
    private fun parseCustomHeaders(customHeadersJson: String): Map<String, String> {
        return try {
            val headers = mutableMapOf<String, String>()
            if (customHeadersJson.isNotEmpty() && customHeadersJson != "{}") {
                val jsonObject = JSONObject(customHeadersJson)
                for (key in jsonObject.keys()) {
                    headers[key] = jsonObject.getString(key)
                }
            }
            headers
        } catch (e: Exception) {
            AppLogger.e("AIServiceFactory", "解析自定义请求头失败", e)
            emptyMap()
        }
    }

    private fun buildAndroidLlamaSessionConfig(config: ModelConfigData): LlamaSession.Config {
        val safeThreadCount =
            config.llamaThreadCount.coerceAtLeast(1)
                .coerceAtMost(Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
        return LlamaSession.Config(
            nThreads = safeThreadCount,
            nCtx = config.llamaContextSize.coerceAtLeast(1),
            nBatch = 512,
            nUBatch = 512,
            nGpuLayers = config.llamaGpuLayers.coerceAtLeast(0),
            useMmap = false,
            flashAttention = false,
            kvUnified = true,
            offloadKqv = false
        )
    }

    /**
     * 创建AI服务实例（统一统计记录边界）。
     *
     * 所有服务（包括连接测试器直接创建的探测服务）都在这里统一包装
      * [TokenTrackingAIService]：正式 sendMessage 调用完成且提供真实 usage 时才会
      * 写入统计账本；测试和探测调用显式跳过记录。
     *
     * @param config 模型配置数据
     * @param modelConfigManager 模型配置管理器，用于多API Key模式
     * @param context Android上下文，用于MNN等需要访问本地资源的提供商
     * @return 对应的AIService实现
     */
    fun createService(
        config: ModelConfigData,
        modelConfigManager: ModelConfigManager,
        context: Context
    ): AIService {
        val rawService = buildService(config, modelConfigManager, context)
        return TokenTrackingAIService(
            delegate = rawService,
            context = context,
            configId = config.id,
        )
    }
    private fun buildService(
        config: ModelConfigData,
        modelConfigManager: ModelConfigManager,
        context: Context
    ): AIService {
        val providerTypeId = config.apiProviderTypeId.trim()
        ToolPkgAiProviderRegistry.get(providerTypeId)?.let { provider ->
            return ToolPkgJsAiProviderService(
                config = config,
                provider = provider
            )
        }

        val httpClient = SharedHttpClient.instance
        val customHeaders = parseCustomHeaders(config.customHeaders)
        val providerType =
            ApiProviderType.fromProviderTypeId(providerTypeId)
                ?: throw IllegalArgumentException(
                    "AI provider type not found or not enabled: $providerTypeId"
                )

        // 根据配置决定使用单个API Key还是多API Key轮询
        val apiKeyProvider = if (config.useMultipleApiKeys) {
            MultiApiKeyProvider(config.id, modelConfigManager)
        } else {
            SingleApiKeyProvider(config.apiKey)
        }

        // 图片处理支持标志
        val supportsVision = config.enableDirectImageProcessing
        // 音频/视频输入支持标志（OpenAI兼容的多模态content数组）
        val supportsAudio = config.enableDirectAudioProcessing
        val supportsVideo = config.enableDirectVideoProcessing
        // Tool Call支持标志
        val enableToolCall = config.enableToolCall

        return when (providerType) {
            // OpenAI格式，支持原生和兼容OpenAI API的服务
            ApiProviderType.OPENAI ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall,
                    includeUsageInStream = true,
                )

            ApiProviderType.OPENAI_GENERIC,
            ApiProviderType.OPENAI_LOCAL ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            ApiProviderType.OPENAI_RESPONSES,
            ApiProviderType.OPENAI_RESPONSES_GENERIC ->
                OpenAIResponsesProvider(
                    responsesApiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    responsesProviderType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            // Claude格式，支持Anthropic Claude系列
            ApiProviderType.ANTHROPIC,
            ApiProviderType.ANTHROPIC_GENERIC ->
                ClaudeProvider(
                    config.apiEndpoint,
                    apiKeyProvider,
                    config.modelName,
                    httpClient,
                    customHeaders,
                    providerType,
                    enableToolCall,
                    config.enableClaude1hPromptCache
                )

            // Gemini格式，支持Google Gemini系列及通用Gemini端点
            ApiProviderType.GOOGLE,
            ApiProviderType.GEMINI_GENERIC ->
                GeminiProvider(
                    config.apiEndpoint,
                    apiKeyProvider,
                    config.modelName,
                    httpClient,
                    customHeaders,
                    providerType,
                    config.enableGoogleSearch,
                    enableToolCall
                )

            // LM Studio使用OpenAI兼容格式
            ApiProviderType.LMSTUDIO ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            // Ollama使用OpenAI兼容格式
            ApiProviderType.OLLAMA ->
                OllamaProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            // MNN本地推理引擎
            ApiProviderType.MNN ->
                MNNProvider(
                    context = context,
                    modelName = config.modelName,
                    forwardType = config.mnnForwardType,
                    threadCount = config.mnnThreadCount,
                    providerType = providerType,
                    enableToolCall = enableToolCall,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo
                )

            // llama.cpp 本地推理引擎
            ApiProviderType.LLAMA_CPP ->
                LlamaProvider(
                    context = context,
                    modelName = config.modelName,
                    sessionConfig = buildAndroidLlamaSessionConfig(config),
                    providerType = providerType,
                    enableToolCall = enableToolCall
                )

            // 阿里云（通义千问）使用QwenProvider
            ApiProviderType.ALIYUN ->
                QwenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    qwenProviderType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            // 其他中文服务商，当前使用OpenAI Provider (大多数兼容OpenAI格式)
            ApiProviderType.BAIDU,
            ApiProviderType.XUNFEI,
            ApiProviderType.ZHIPU,
            ApiProviderType.BAICHUAN,
            ApiProviderType.IFLOW,
            ApiProviderType.INFINIAI,
            ApiProviderType.ALIPAY_BAILING,
            ApiProviderType.PPINFRA,
            ApiProviderType.NOVITA,
            ApiProviderType.MINIMAX,
            ApiProviderType.OTHER ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            ApiProviderType.MOONSHOT ->
                KimiProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.MIMO ->
                MimoProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.DEEPSEEK ->
                DeepseekProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.MISTRAL ->
                MistralProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.SILICONFLOW ->
                QwenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    qwenProviderType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.OPENCODE ->
                OpenCodeProvider.create(
                    config = config,
                    modelConfigManager = modelConfigManager,
                    context = context,
                    client = httpClient,
                    customHeaders = customHeaders,
                    apiKeyProvider = apiKeyProvider,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.OPENROUTER ->
                OpenRouterProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.FOUR_ROUTER ->
                FourRouterProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.NOUS_PORTAL ->
                NousPortalProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.DOUBAO ->
                DoubaoAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.NVIDIA ->
                NvidiaAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = providerType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
        }
    }
}
