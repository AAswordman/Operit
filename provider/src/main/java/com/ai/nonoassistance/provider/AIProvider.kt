package com.ai.nonoassistance.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Provider Abstraction Layer — PROJECT_PLAN.md §3
 *
 * Each AI provider implements this interface. New provider = one implementation + register in ProviderRegistry.
 * No hardcoded provider/model names in core code — everything goes through this abstraction.
 */
interface AIProvider {
    /** Unique provider identifier, e.g. "openai", "deepseek", "gemini" */
    val id: String

    /** Human-readable display name */
    val displayName: String

    /** List available models from this provider */
    suspend fun listModels(): List<ModelInfo>

    /** Get pricing for a specific model (null if unknown) */
    suspend fun getPricing(modelId: String): ModelPricing?

    /** Streaming chat completion — emits chunks as they arrive */
    suspend fun chatCompletion(request: ChatRequest): Flow<ChatChunk>

    /** Whether this provider supports tool/function calling */
    val supportsToolCalling: Boolean

    /** Whether this provider supports thinking/reasoning mode */
    val supportsThinking: Boolean
}

@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String,
    val contextWindow: Int,
    val supportsToolCalling: Boolean = false,
    val supportsThinking: Boolean = false,
    val maxOutputTokens: Int? = null
)

@Serializable
data class ModelPricing(
    val inputPerMillionTokens: Double,
    val outputPerMillionTokens: Double,
    val currency: String = "USD",
    val lastUpdated: String = "" // ISO-8601 timestamp
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val systemPrompt: String? = null,
    val tools: List<ToolDefinition>? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val thinkingEnabled: Boolean = false
)

@Serializable
data class ChatMessage(
    val role: String, // "system", "user", "assistant", "tool"
    val content: String,
    val toolCallId: String? = null
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: String // JSON schema as string
)

@Serializable
data class ChatChunk(
    val content: String = "",
    val toolCalls: List<ToolCall>? = null,
    val finishReason: String? = null,
    val thinkingContent: String? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String // JSON string
)
