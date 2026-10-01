package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class DeepseekPureThinkingHistoryTest {

    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true

    @Before
    fun disableAndroidLogging() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLogging() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    @Test
    fun `status warning after a tool call is not treated as a tool result`() {
        val messages =
            buildMessages(
                listOf(
                    PromptTurn(kind = PromptTurnKind.USER, content = "Read the file."),
                    PromptTurn(
                        kind = PromptTurnKind.ASSISTANT,
                        content = toolCall("read_file", "path" to "a.txt")
                    ),
                    PromptTurn(
                        kind = PromptTurnKind.TOOL_RESULT,
                        content = "<status type=\"warning\">警告：请输出正文内容，禁止仅输出思考内容。</status>"
                    )
                )
            )

        assertEquals(listOf("user", "assistant", "tool", "user"), messages.roles())
        assertTrue(messages.at(2).getString("content").contains("工具结果缺失"))
        assertTrue(messages.at(3).getString("content").contains("请输出正文内容"))
        assertFalse(messages.at(3).has("tool_call_id"))
    }

    @Test
    fun `pure thinking continuation keeps reasoning_content on the assistant turn`() {
        val messages =
            buildMessages(
                listOf(
                    PromptTurn(kind = PromptTurnKind.USER, content = "Continue the previous work."),
                    PromptTurn(
                        kind = PromptTurnKind.ASSISTANT,
                        content = "<think>I already inspected a.txt and should keep going.</think>"
                    ),
                    PromptTurn(
                        kind = PromptTurnKind.USER,
                        content = "<status type=\"warning\">警告：请输出正文内容，禁止仅输出思考内容。</status>"
                    )
                )
            )

        assertEquals(listOf("user", "assistant", "user"), messages.roles())
        assertEquals(
            "I already inspected a.txt and should keep going.",
            messages.at(1).getString("reasoning_content")
        )
        assertEquals("[Empty]", messages.at(1).getString("content"))
        assertFalse(messages.at(1).has("tool_calls"))
        assertTrue(messages.at(2).getString("content").contains("请输出正文内容"))
        assertFalse(messages.at(2).has("tool_call_id"))
    }

    private fun buildMessages(history: List<PromptTurn>): JSONArray {
        val request = buildRequestJson(history)
        return request.getJSONArray("messages")
    }

    private fun buildRequestJson(history: List<PromptTurn>): JSONObject {
        val provider =
            DeepseekProvider(
                apiEndpoint = "https://example.test/v1/chat/completions",
                apiKeyProvider = SingleApiKeyProvider("test-key"),
                modelName = "deepseek-test",
                client = OkHttpClient(),
                enableToolCall = true
            )
        val method =
            DeepseekProvider::class.java.declaredMethods.single {
                it.name == "createRequestBody" && it.parameterCount == 7
            }
        method.isAccessible = true
        val body =
            method.invoke(
                provider,
                mock<Context>(),
                history,
                emptyList<ModelParameter<*>>(),
                true,
                false,
                listOf(ToolPrompt(name = "read_file", description = "Read a file")),
                true
            ) as RequestBody
        val buffer = Buffer()
        body.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    private fun JSONArray.roles(): List<String> =
        (0 until length()).map { getJSONObject(it).getString("role") }

    private fun JSONArray.at(index: Int): JSONObject = getJSONObject(index)

    private fun toolCall(name: String, vararg params: Pair<String, String>): String {
        val body =
            params.joinToString("") { (key, value) ->
                "<param name=\"$key\">$value</param>"
            }
        return "<tool name=\"$name\">$body</tool>"
    }
}
