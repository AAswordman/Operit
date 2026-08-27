package com.ai.assistance.operit.data.collects

import com.ai.assistance.operit.data.model.ApiProviderType
import java.net.URI

data class ProviderEndpointOption(
    val endpoint: String,
    val label: String
)

data class ProviderApiConfig(
    val providerType: ApiProviderType,
    val defaultModelName: String = "",
    val defaultApiEndpoint: String = "",
    val endpointOptions: List<ProviderEndpointOption> = emptyList(),
    val requiresApiKey: Boolean = true
)

object ApiProviderConfigs {
    private val configs: Map<ApiProviderType, ProviderApiConfig> = listOf(
        ProviderApiConfig(
            providerType = ApiProviderType.OPENAI,
            defaultModelName = "gpt-4o",
            defaultApiEndpoint = "https://api.openai.com/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OPENAI_RESPONSES,
            defaultModelName = "gpt-4o",
            defaultApiEndpoint = "https://api.openai.com/v1/responses"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OPENAI_RESPONSES_GENERIC,
            defaultModelName = "",
            defaultApiEndpoint = ""
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OPENAI_GENERIC,
            defaultModelName = "",
            defaultApiEndpoint = ""
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.ANTHROPIC,
            defaultModelName = "claude-3-opus-20240229",
            defaultApiEndpoint = "https://api.anthropic.com/v1/messages"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.ANTHROPIC_GENERIC,
            defaultModelName = "",
            defaultApiEndpoint = ""
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.GOOGLE,
            defaultModelName = "gemini-2.0-flash",
            defaultApiEndpoint = "https://generativelanguage.googleapis.com/v1beta/models"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.GEMINI_GENERIC,
            defaultModelName = "gemini-2.0-flash",
            defaultApiEndpoint = ""
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.DEEPSEEK,
            defaultModelName = "deepseek-v4-flash",
            defaultApiEndpoint = "https://api.deepseek.com/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.BAIDU,
            defaultModelName = "ernie-bot-4",
            defaultApiEndpoint = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.ALIYUN,
            defaultModelName = "qwen-max",
            defaultApiEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.XUNFEI,
            defaultModelName = "spark3.5",
            defaultApiEndpoint = "https://spark-api-open.xf-yun.com/v2/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.ZHIPU,
            defaultModelName = "glm-4.5",
            defaultApiEndpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            endpointOptions = listOf(
                ProviderEndpointOption(
                    endpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                    label = "CN standard"
                ),
                ProviderEndpointOption(
                    endpoint = "https://open.bigmodel.cn/api/coding/paas/v4/chat/completions",
                    label = "CN coding"
                ),
                ProviderEndpointOption(
                    endpoint = "https://api.z.ai/api/paas/v4/chat/completions",
                    label = "International standard"
                ),
                ProviderEndpointOption(
                    endpoint = "https://api.z.ai/api/coding/paas/v4/chat/completions",
                    label = "International coding"
                )
            )
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.BAICHUAN,
            defaultModelName = "baichuan4",
            defaultApiEndpoint = "https://api.baichuan-ai.com/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.MOONSHOT,
            defaultModelName = "moonshot-v1-128k",
            defaultApiEndpoint = "https://api.moonshot.cn/v1/chat/completions",
            endpointOptions = listOf(
                ProviderEndpointOption(
                    endpoint = "https://api.moonshot.cn/v1/chat/completions",
                    label = "China (moonshot.cn)"
                ),
                ProviderEndpointOption(
                    endpoint = "https://api.moonshot.ai/v1/chat/completions",
                    label = "International (moonshot.ai)"
                ),
                ProviderEndpointOption(
                    endpoint = "https://api.kimi.com/coding/v1/chat/completions",
                    label = "Kimi Code (api.kimi.com)"
                )
            )
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.MIMO,
            defaultModelName = "mimo-v2.5-pro",
            defaultApiEndpoint = "https://api.xiaomimimo.com/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.MISTRAL,
            defaultModelName = "codestral-latest",
            defaultApiEndpoint = "https://codestral.mistral.ai/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.SILICONFLOW,
            defaultModelName = "yi-1.5-34b",
            defaultApiEndpoint = "https://api.siliconflow.cn/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.IFLOW,
            defaultModelName = "TBStars2-200B-A13B",
            defaultApiEndpoint = "https://apis.iflow.cn/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OPENROUTER,
            defaultModelName = "google/gemini-pro",
            defaultApiEndpoint = "https://openrouter.ai/api/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OPENCODE,
            defaultModelName = "",
            defaultApiEndpoint = "https://opencode.ai/zen",
            endpointOptions = listOf(
                ProviderEndpointOption(
                    endpoint = "https://opencode.ai/zen",
                    label = "Zen"
                ),
                ProviderEndpointOption(
                    endpoint = "https://opencode.ai/zen/go",
                    label = "Go"
                )
            )
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.FOUR_ROUTER,
            defaultModelName = "gpt-5.4-mini",
            defaultApiEndpoint = "https://4router.net/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.NOUS_PORTAL,
            defaultModelName = "",
            defaultApiEndpoint = "https://inference-api.nousresearch.com/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.INFINIAI,
            defaultModelName = "infini-mini",
            defaultApiEndpoint = "https://cloud.infini-ai.com/maas/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.ALIPAY_BAILING,
            defaultModelName = "Ling-1T",
            defaultApiEndpoint = "https://api.tbox.cn/api/llm/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.DOUBAO,
            defaultModelName = "Doubao-pro-4k",
            defaultApiEndpoint = "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
            endpointOptions = listOf(
                ProviderEndpointOption(
                    endpoint = "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                    label = "CN standard"
                ),
                ProviderEndpointOption(
                    endpoint = "https://ark.cn-beijing.volces.com/api/coding/v3/chat/completions",
                    label = "CN coding"
                )
            )
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.NVIDIA,
            defaultModelName = "nvidia/nemotron-3-nano-30b-a3b",
            defaultApiEndpoint = "https://integrate.api.nvidia.com/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.LMSTUDIO,
            defaultModelName = "meta-llama-3.1-8b-instruct",
            defaultApiEndpoint = "http://localhost:1234/v1/chat/completions",
            requiresApiKey = false
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OLLAMA,
            defaultModelName = "",
            defaultApiEndpoint = "http://localhost:11434/v1/chat/completions",
            requiresApiKey = false
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OPENAI_LOCAL,
            defaultModelName = "",
            defaultApiEndpoint = "http://localhost:8000/v1/chat/completions",
            requiresApiKey = false
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.MNN,
            defaultModelName = "",
            defaultApiEndpoint = "",
            requiresApiKey = false
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.LLAMA_CPP,
            defaultModelName = "",
            defaultApiEndpoint = "",
            requiresApiKey = false
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.PPINFRA,
            defaultModelName = "gpt-4o-mini",
            defaultApiEndpoint = "https://api.ppinfra.com/openai/v1/chat/completions"
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.NOVITA,
            defaultModelName = "moonshotai/kimi-k2.5",
            defaultApiEndpoint = "https://api.novita.ai/openai/v1/chat/completions",
            endpointOptions = listOf(
                ProviderEndpointOption(
                    endpoint = "https://api.novita.ai/openai/v1/chat/completions",
                    label = "OpenAI-compatible"
                ),
                ProviderEndpointOption(
                    endpoint = "https://api.novita.ai/anthropic/v1/messages",
                    label = "Anthropic-compatible"
                )
            )
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.MINIMAX,
            defaultModelName = "MiniMax-M2.7",
            defaultApiEndpoint = "https://api.minimaxi.com/v1/chat/completions",
            endpointOptions = listOf(
                ProviderEndpointOption(
                    endpoint = "https://api.minimaxi.com/v1/chat/completions",
                    label = "China (minimaxi.com)"
                ),
                ProviderEndpointOption(
                    endpoint = "https://api.minimax.io/v1/chat/completions",
                    label = "International (minimax.io)"
                )
            )
        ),
        ProviderApiConfig(
            providerType = ApiProviderType.OTHER,
            defaultModelName = "",
            defaultApiEndpoint = ""
        )
    ).associateBy(ProviderApiConfig::providerType)

    fun get(providerType: ApiProviderType): ProviderApiConfig {
        return configs[providerType] ?: ProviderApiConfig(providerType = providerType)
    }

    fun getDefaultModelName(providerType: ApiProviderType): String {
        return get(providerType).defaultModelName
    }

    fun getDefaultApiEndpoint(providerType: ApiProviderType): String {
        return get(providerType).defaultApiEndpoint
    }

    fun getEndpointOptions(providerType: ApiProviderType): List<ProviderEndpointOption>? {
        return get(providerType).endpointOptions.takeIf { it.isNotEmpty() }
    }

    fun requiresApiKey(providerType: ApiProviderType, apiEndpoint: String = ""): Boolean {
        if (!get(providerType).requiresApiKey) {
            return false
        }

        return !isLoopbackEndpoint(apiEndpoint)
    }

    fun requiresApiKey(providerTypeId: String, apiEndpoint: String = ""): Boolean {
        val providerType = ApiProviderType.fromProviderTypeId(providerTypeId) ?: return !isLoopbackEndpoint(apiEndpoint)
        return requiresApiKey(providerType, apiEndpoint)
    }

    fun isDefaultModelName(modelName: String): Boolean {
        return configs.values.any { it.defaultModelName == modelName }
    }

    fun isDefaultApiEndpoint(endpoint: String): Boolean {
        return configs.values.any { it.defaultApiEndpoint == endpoint }
    }

    fun isLoopbackEndpoint(apiEndpoint: String): Boolean {
        if (apiEndpoint.isBlank()) {
            return false
        }

        val normalizedEndpoint = apiEndpoint.trim().lowercase()
        return try {
            val host = URI(apiEndpoint).host
            if (host != null) {
                isLoopbackHost(host)
            } else {
                isLoopbackEndpointText(normalizedEndpoint)
            }
        } catch (_: Exception) {
            isLoopbackEndpointText(normalizedEndpoint)
        }
    }

    private fun isLoopbackHost(host: String): Boolean {
        return when (host.lowercase().trim('[', ']')) {
            "localhost",
            "127.0.0.1",
            "::1",
            "0.0.0.0",
            "10.0.2.2" -> true
            else -> false
        }
    }

    private fun isLoopbackEndpointText(apiEndpoint: String): Boolean {
        return apiEndpoint.startsWith("localhost:") ||
            apiEndpoint.startsWith("127.0.0.1:") ||
            apiEndpoint.startsWith("[::1]:") ||
            apiEndpoint.startsWith("::1:") ||
            apiEndpoint.startsWith("0.0.0.0:") ||
            apiEndpoint.startsWith("10.0.2.2:")
    }
}
