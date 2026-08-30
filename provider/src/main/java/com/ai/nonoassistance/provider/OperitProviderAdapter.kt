package com.ai.nonoassistance.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * OperitProviderAdapter — bridges the new [AIProvider] interface with Operit's existing
 * [AIService] system.
 *
 * This adapter allows the new Orchestration Engine and Context Budget Manager to work
 * with the existing provider implementations (DeepSeek, Gemini, OpenAI, etc.) without
 * rewriting them.
 *
 * Usage:
 * ```kotlin
 * val adapter = OperitProviderAdapter(
 *     providerId = "deepseek",
 *     displayName = "DeepSeek",
 *     underlyingService = deepseekAIService,
 *     supportsToolCalling = true,
 *     supportsThinking = true
 * )
 * providerRegistry.register(adapter)
 * ```
 *
 * Note: This adapter delegates [listModels] and [get Pricing] to the underlying service.
 * The [chatCompletion] method converts the existing Stream-based API to Flow-based API.
 */
class OperitProviderAdapter(
    override val id: String,
    override val displayName: String,
    private val underlyingServiceId: String,
    override val supportsToolCalling: Boolean = true,
    override val supportsThinking: Boolean = false
) : AIProvider {

    /**
     * List available models from the underlying provider.
     *
     * Phase 1 TODO: When integrating with the actual AIService, call
     * `service.getModelsList(context)` and convert ModelOption list to ModelInfo list.
     * For now, returns empty list — will be wired up when app module depends on provider module.
     */
    override suspend fun listModels(): List<ModelInfo> {
        // Phase 1 TODO: Wire up to actual AIService.getModelsList()
        // val result = underlyingService.getModelsList(context)
        // return result.getOrElse { emptyList() }.map { option ->
        //     ModelInfo(
        //         id = option.id,
        //         displayName = option.name,
        //         contextWindow = option.contextLength.toInt(),
        //         supportsToolCalling = option.supportsToolCalling,
        //         supportsThinking = option.supportsThinking
        //     )
        // }
        return emptyList()
    }

    /**
     * Get pricing for a model.
     * Pricing is fetched separately by [ModelPricingService] from remote JSON,
     * not from the provider directly.
     */
    override suspend fun getPricing(modelId: String): ModelPricing? {
        // Pricing is handled by ModelPricingService, not directly by the provider
        return null
    }

    /**
     * Chat completion — delegates to the underlying service.
     *
     * Phase 1 TODO: When integrating with the actual AIService, convert the
     * Stream-based response to Flow<ChatChunk>.
     */
    override suspend fun chatCompletion(request: ChatRequest): Flow<ChatChunk> {
        // Phase 1 TODO: Wire up to actual AIService.sendMessage()
        // This requires:
        // 1. Converting ChatRequest.messages to List<PromptTurn>
        // 2. Calling service.sendMessage() with converted params
        // 3. Converting Stream<String> to Flow<ChatChunk>
        return flow {
            emit(ChatChunk(
                content = "OperitProviderAdapter placeholder — wire up to AIService in Phase 1",
                finishReason = "stop"
            ))
        }
    }

    override fun toString(): String = "OperitProviderAdapter(id=$id, displayName=$displayName)"
}
