package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAIProviderToolCallIdTest {
    private val provider =
        OpenAIProvider(
            apiEndpoint = "https://example.com/v1/chat/completions",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "test-model",
            client = OkHttpClient(),
            providerType = ApiProviderType.OPENAI,
            enableToolCall = true
        )

    @Test
    fun `tool call id is parsed from responses tool markup`() {
        val (_, calls) =
            provider.parseXmlToolCalls(
                """<tool_test name="list_files" call_id="daxkro1vn"><param name="path">/tmp</param></tool_test>"""
            )

        assertEquals("daxkro1vn", calls!!.getJSONObject(0).getString("id"))
    }

    @Test
    fun `tool result id is parsed independently from completion order`() {
        val (_, results) =
            provider.parseXmlToolResults(
                """<tool_result_test name="list_files" status="success" call_id="daxkro1vn"><content>files</content></tool_result_test>"""
            )

        assertEquals("daxkro1vn", results!!.single().first)
        assertEquals("files", results.single().second)
    }
}
