package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.util.AppLogger
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class OpenAIRequestBodyObjectTest {
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
    fun `request object keeps complete text and the existing chat payload`() {
        val content = "完整用户上下文原文\n".repeat(20_000).trimEnd()
        val history = listOf(
            PromptTurn(kind = PromptTurnKind.SYSTEM, content = "系统提示原文"),
            PromptTurn(kind = PromptTurnKind.USER, content = content),
        )
        val provider = object : OpenAIProvider(
            apiEndpoint = "https://example.test/v1/chat/completions",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "test-model",
            client = OkHttpClient(),
        ) {
            fun build(context: Context): JSONObject = createRequestBodyObject(context, history, stream = false)
        }
        val body = provider.build(mock<Context>())
        val expected = JSONObject().put("model", "test-model").put("stream", false)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", "系统提示原文"))
                .put(JSONObject().put("role", "user").put("content", content)))

        // 按 JSON 结构比较完整请求，避免调用 Android JSONObject 未提供的 similar。
        assertEquals(
            Json.parseToJsonElement(expected.toString()),
            Json.parseToJsonElement(body.toString()),
        )
        assertEquals(content, body.getJSONArray("messages").getJSONObject(1).getString("content"))
    }
}
