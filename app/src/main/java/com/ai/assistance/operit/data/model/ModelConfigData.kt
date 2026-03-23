package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/** API提供商类型枚举 */
@Serializable
enum class ApiProviderType {
        OPENAI, // OpenAI (GPT系列)
        OPENAI_RESPONSES, // OpenAI Responses API
        OPENAI_RESPONSES_GENERIC, // OpenAI Responses通用（自定义端点）
        OPENAI_GENERIC, // OpenAI通用（自定义端点）
        ANTHROPIC, // Anthropic (Claude系列)
        ANTHROPIC_GENERIC, // Anthropic通用（自定义端点）
        GOOGLE, // Google (Gemini系列)
        GEMINI_GENERIC, // Gemini通用（自定义端点）
        BAIDU, // 百度 (文心一言系列)
        ALIYUN, // 阿里云 (通义千问系列)
        XUNFEI, // 讯飞 (星火认知系列)
        ZHIPU, // 智谱AI (ChatGLM系列)
        BAICHUAN, // 百川大模型
        MOONSHOT, // 月之暗面大模型
        DEEPSEEK, // Deepseek大模型
        MISTRAL, // Mistral AI (Codestral等)
        SILICONFLOW, // 硅基流动
        IFLOW, // iFlow
        OPENROUTER, // OpenRouter (多模型聚合)
        INFINIAI, // 无问芯穹
        ALIPAY_BAILING, // 支付宝百灵大模型
        DOUBAO, // 豆包（火山模型）
        NVIDIA, // NVIDIA API Catalog / NIM
        LMSTUDIO, // LM Studio本地模型服务
        OLLAMA, // Ollama 本地/私有部署服务（OpenAI兼容）
        MNN, // MNN本地推理引擎
        LLAMA_CPP, // llama.cpp 本地推理引擎
        PPINFRA, // 派欧云
        NOVITA, // Novita AI
        OTHER // 其他提供商（自定义端点）
}

object ModelConfigDefaults {
        const val DEFAULT_CONTEXT_LENGTH = 48.0f
        const val DEFAULT_MAX_CONTEXT_LENGTH = 128.0f
        const val DEFAULT_ENABLE_MAX_CONTEXT_MODE = false
        const val DEFAULT_SUMMARY_TOKEN_THRESHOLD = 0.70f
        const val DEFAULT_ENABLE_SUMMARY = true
        const val DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT = true
        const val DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD = 16
}

/** 表示完整的模型配置，包括API设置和模型参数 */
@Serializable
data class ModelConfigData(
        val id: String,
        val name: String,

        // API设置
        val apiKey: String = "",
        val apiEndpoint: String = "",
        val modelName: String = "",
        val apiProviderType: ApiProviderType = ApiProviderType.DEEPSEEK,

        // 多API Key支持
        val useMultipleApiKeys: Boolean = false, // 是否启用多API Key模式
        val apiKeyPool: List<ApiKeyInfo> = emptyList(), // API Key池
        val currentKeyIndex: Int = 0, // 当前使用的Key索引
        val keyRotationMode: String = "ROUND_ROBIN", // 轮询模式: ROUND_ROBIN / RANDOM

        // 是否包含自定义参数
        val hasCustomParameters: Boolean = false,

        // 模型参数的enabled状态
        val maxTokensEnabled: Boolean = false,
        val temperatureEnabled: Boolean = false,
        val topPEnabled: Boolean = false,
        val topKEnabled: Boolean = false,
        val presencePenaltyEnabled: Boolean = false,
        val frequencyPenaltyEnabled: Boolean = false,
        val repetitionPenaltyEnabled: Boolean = false,

        // 模型参数值
        val maxTokens: Int = 4096,
        val temperature: Float = 1.0f,
        val topP: Float = 1.0f,
        val topK: Int = 0,
        val presencePenalty: Float = 0.0f,
        val frequencyPenalty: Float = 0.0f,
        val repetitionPenalty: Float = 1.0f,

        // 自定义参数JSON字符串
        val customParameters: String = "[]",

        // 自定义请求头JSON字符串
        val customHeaders: String = "{}",

        // 上下文/总结配置
        val contextLength: Float = ModelConfigDefaults.DEFAULT_CONTEXT_LENGTH,
        val maxContextLength: Float = ModelConfigDefaults.DEFAULT_MAX_CONTEXT_LENGTH,
        val enableMaxContextMode: Boolean = ModelConfigDefaults.DEFAULT_ENABLE_MAX_CONTEXT_MODE,
        val summaryTokenThreshold: Float = ModelConfigDefaults.DEFAULT_SUMMARY_TOKEN_THRESHOLD,
        val enableSummary: Boolean = ModelConfigDefaults.DEFAULT_ENABLE_SUMMARY,
        val enableSummaryByMessageCount: Boolean =
                ModelConfigDefaults.DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT,
        val summaryMessageCountThreshold: Int =
                ModelConfigDefaults.DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD,

        // MNN特定配置
        // 注意：MNN模型路径会根据modelName自动构建，不需要单独存储
        val mnnForwardType: Int = 0, // 前向计算类型 (CPU/GPU等)
        val mnnThreadCount: Int = 4, // 推理线程数

        // llama.cpp 特定配置
        val llamaThreadCount: Int = 4, // 推理线程数
        val llamaContextSize: Int = 4096, // n_ctx

        // 图片处理配置
        val enableDirectImageProcessing: Boolean = false, // 是否启用直接图片处理

        // 音频/视频处理配置
        val enableDirectAudioProcessing: Boolean = false, // 是否启用直接音频处理
        val enableDirectVideoProcessing: Boolean = false, // 是否启用直接视频处理

        // Gemini特定配置
        val enableGoogleSearch: Boolean = false, // 是否启用Google Search Grounding (仅Gemini支持)

        // Tool Call配置
        val enableToolCall: Boolean = false, // 是否启用Tool Call接口调用工具（使用模型原生工具调用而非XML格式）
        val strictToolCall: Boolean = false, // 严格Tool Call模式（用于Tool Call支持不稳定时的兼容代理）

        // 请求频率限制配置
        val requestLimitPerMinute: Int = 0, // 每分钟最大请求次数，0表示不限流
        val maxConcurrentRequests: Int = 0 // 最大并发请求数，0表示不限制
)

/** 简化版的模型配置数据，用于列表显示 */
@Serializable
data class ModelConfigSummary(
        val id: String,
        val name: String,
        val modelName: String = "",
        val apiEndpoint: String = "",
        val apiProviderType: ApiProviderType = ApiProviderType.DEEPSEEK,
        val modelIndex: Int = 0 // 当modelName包含多个模型（逗号分隔）时，选择第几个模型（从0开始）
)

/** 从逗号分隔的模型名称字符串中根据索引获取具体模型 */
fun getModelByIndex(modelName: String, index: Int): String {
    if (modelName.isEmpty()) return ""
    val models = modelName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return if (index >= 0 && index < models.size) models[index] else models.getOrNull(0) ?: ""
}

/** 获取模型列表 */
fun getModelList(modelName: String): List<String> {
    if (modelName.isEmpty()) return emptyList()
    return modelName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/** 
 * 计算有效的模型索引（处理越界情况）
 * 如果索引超出范围，自动返回0（第一个模型）
 * @param modelName 逗号分隔的模型名称字符串
 * @param requestedIndex 请求的索引
 * @return 有效的索引值（0到模型数量-1之间）
 */
fun getValidModelIndex(modelName: String, requestedIndex: Int): Int {
    val modelList = getModelList(modelName)
    return if (requestedIndex >= 0 && requestedIndex < modelList.size) {
        requestedIndex
    } else {
        0 // 索引越界时使用第一个
    }
}
