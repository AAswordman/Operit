package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class OpenAIProviderContentFieldTest {

    @Test
    fun `chat completions content field removes responses protocol markup`() {
        val provider = openAiProvider()
        val context = mock<Context>()
        val content =
            "<meta provider=\"openai:responses_reasoning\">reasoning</meta>" +
                "<meta provider=\"openai:responses_output_item\">web-search</meta>" +
                "<search provider=\"deepseek\"><query>q</query></search>" +
                "answer"

        assertEquals("answer", provider.buildContentField(context, content, role = "assistant"))
    }

    @Test
    fun `responses content field preserves protocol markup for adapter conversion`() {
        val provider = responsesProvider()
        val context = mock<Context>()
        val content =
            "<meta provider=\"openai:responses_output_item\">web-search</meta>" +
                "<search provider=\"deepseek\"><query>q</query></search>" +
                "answer"

        assertEquals(content, provider.buildContentField(context, content, role = "assistant"))
    }

    private fun openAiProvider(): OpenAIProvider =
        OpenAIProvider(
            apiEndpoint = "https://example.test/v1/chat/completions",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "test-model",
            client = OkHttpClient(),
        )

    private fun responsesProvider(): OpenAIProvider =
        object : OpenAIProvider(
            apiEndpoint = "https://example.test/v1/responses",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "test-model",
            client = OkHttpClient(),
        ) {
            override val useResponsesApi: Boolean = true
        }
}
