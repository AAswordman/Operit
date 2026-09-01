package com.ai.assistance.operit.core.hardening

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * ProviderCompatibilityTester — tests tool calls through mock request for all providers.
 *
 * Per PROJECT_PLAN.md §10:
 * > Third-party provider (เช่น DeepSeek ผ่าน proxy) error 400 เมื่อ trigger tool call
 * > ทำ compatibility test suite รัน mock request ผ่านทุก provider ก่อนปล่อย release
 *
 * This tester sends standardized mock requests to each provider's endpoint
 * and verifies the response format is compatible with the app's parsing logic.
 */
class ProviderCompatibilityTester {

    /**
     * Test a provider's compatibility with the app's expected request/response format.
     */
    suspend fun testProvider(config: ProviderTestConfig): CompatibilityResult = withContext(Dispatchers.IO) {
        try {
            // 1. Send mock chat completion request with tool definitions
            val requestBody = buildChatCompletionRequest(
                model = config.model,
                includeTools = config.supportsToolCalling,
                includeThinking = config.supportsThinking
            )

            val response = sendRequest(config.endpoint, config.apiKey, requestBody)

            // 2. Verify response format (content, tool_calls, usage)
            val hasContent = response.contains("\"content\"") || response.contains("\"choices\"")
            val hasUsage = response.contains("\"usage\"")

            // 3. Check for error responses
            val isError = response.contains("\"error\"")
            val errorCode = if (isError) extractErrorCode(response) else null

            CompatibilityResult(
                providerId = config.providerId,
                passed = hasContent && !isError,
                details = when {
                    isError -> "Provider returned error: $errorCode"
                    !hasContent -> "Response missing 'content' or 'choices' field"
                    !hasUsage -> "Response missing 'usage' field (non-critical)"
                    else -> "Provider compatible — response format valid"
                }
            )
        } catch (e: Exception) {
            CompatibilityResult(
                providerId = config.providerId,
                passed = false,
                details = "Connection failed: ${e.message}",
                error = e
            )
        }
    }

    /**
     * Run compatibility tests for all configured providers.
     */
    suspend fun testAllProviders(configs: List<ProviderTestConfig>): List<CompatibilityResult> {
        return configs.map { testProvider(it) }
    }

    /**
     * Test tool call format compatibility.
     * Sends a mock tool call request and verifies the provider handles it correctly.
     */
    suspend fun testToolCallFormat(config: ProviderTestConfig): CompatibilityResult = withContext(Dispatchers.IO) {
        if (!config.supportsToolCalling) {
            return@withContext CompatibilityResult(
                providerId = config.providerId,
                passed = true,
                details = "Skipped — provider does not support tool calling"
            )
        }

        try {
            val requestBody = buildToolCallRequest(config.model)
            val response = sendRequest(config.endpoint, config.apiKey, requestBody)

            // Check for tool_calls in response
            val hasToolCalls = response.contains("\"tool_calls\"") || response.contains("\"function_call\"")
            val isError = response.contains("\"error\"")

            CompatibilityResult(
                providerId = config.providerId,
                passed = !isError && hasToolCalls,
                details = when {
                    isError -> "Tool call test failed: error in response"
                    hasToolCalls -> "Tool call format compatible"
                    else -> "No tool_calls in response — may not support function calling"
                }
            )
        } catch (e: Exception) {
            CompatibilityResult(
                providerId = config.providerId,
                passed = false,
                details = "Tool call test failed: ${e.message}",
                error = e
            )
        }
    }

    /**
     * Test thinking/reasoning mode compatibility.
     */
    suspend fun testThinkingMode(config: ProviderTestConfig): CompatibilityResult = withContext(Dispatchers.IO) {
        if (!config.supportsThinking) {
            return@withContext CompatibilityResult(
                providerId = config.providerId,
                passed = true,
                details = "Skipped — provider does not support thinking mode"
            )
        }

        try {
            val requestBody = buildThinkingRequest(config.model)
            val response = sendRequest(config.endpoint, config.apiKey, requestBody)

            // Check for thinking/reasoning content
            val hasThinking = response.contains("\"thinking\"") ||
                    response.contains("\"reasoning\"") ||
                    response.contains("\"chain_of_thought\"")
            val isError = response.contains("\"error\"")

            CompatibilityResult(
                providerId = config.providerId,
                passed = !isError,
                details = when {
                    isError -> "Thinking mode test failed: error in response"
                    hasThinking -> "Thinking mode compatible — reasoning content detected"
                    else -> "No thinking content in response (may use different format)"
                }
            )
        } catch (e: Exception) {
            CompatibilityResult(
                providerId = config.providerId,
                passed = false,
                details = "Thinking mode test failed: ${e.message}",
                error = e
            )
        }
    }

    // --- Request builders ---

    private fun buildChatCompletionRequest(
        model: String,
        includeTools: Boolean,
        includeThinking: Boolean
    ): String {
        val toolsSection = if (includeTools) {
            """,
            |  "tools": [
            |    {
            |      "type": "function",
            |      "function": {
            |        "name": "get_weather",
            |        "description": "Get current weather for a location",
            |        "parameters": {
            |          "type": "object",
            |          "properties": {
            |            "location": { "type": "string", "description": "City name" }
            |          },
            |          "required": ["location"]
            |        }
            |      }
            |    }
            |  ]""".trimMargin()
        } else ""

        return """
        |{
        |  "model": "$model",
        |  "messages": [
        |    {"role": "user", "content": "What is the weather in Tokyo?"}
        |  ]$toolsSection
        |}""".trimMargin()
    }

    private fun buildToolCallRequest(model: String): String {
        return """
        |{
        |  "model": "$model",
        |  "messages": [
        |    {"role": "user", "content": "Get weather for New York"}
        |  ],
        |  "tools": [
        |    {
        |      "type": "function",
        |      "function": {
        |        "name": "get_weather",
        |        "description": "Get weather",
        |        "parameters": {
        |          "type": "object",
        |          "properties": {
        |            "location": { "type": "string" }
        |          },
        |          "required": ["location"]
        |        }
        |      }
        |    }
        |  ],
        |  "tool_choice": "auto"
        |}""".trimMargin()
    }

    private fun buildThinkingRequest(model: String): String {
        return """
        |{
        |  "model": "$model",
        |  "messages": [
        |    {"role": "user", "content": "Solve: what is 127 * 893?"}
        |  ],
        |  "thinking": {
        |    "type": "enabled",
        |    "budget_tokens": 1024
        |  }
        |}""".trimMargin()
    }

    // --- HTTP helper ---

    private fun sendRequest(endpoint: String, apiKey: String, body: String): String {
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.doOutput = true

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(body)
            writer.flush()
        }

        val responseCode = conn.responseCode
        val responseBody = if (responseCode in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
            """{"error": {"code": $responseCode, "message": "$errorBody"}}"""
        }

        conn.disconnect()
        return responseBody
    }

    private fun extractErrorCode(response: String): String {
        // Simple extraction of error code from JSON-like response
        val codeMatch = Regex(""""code"\s*:\s*(\d+)""").find(response)
        val msgMatch = Regex(""""message"\s*:\s*"([^"]+)""").find(response)
        val code = codeMatch?.groupValues?.get(1) ?: "unknown"
        val msg = msgMatch?.groupValues?.get(1) ?: ""
        return "$code: $msg"
    }

    // --- Data classes ---

    data class ProviderTestConfig(
        val providerId: String,
        val endpoint: String,
        val apiKey: String,
        val model: String,
        val supportsToolCalling: Boolean = true,
        val supportsThinking: Boolean = false
    )

    data class CompatibilityResult(
        val providerId: String,
        val passed: Boolean,
        val details: String,
        val error: Exception? = null
    )
}
