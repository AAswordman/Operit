package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.nio.file.Files
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class DeepseekProviderMediaRoleTest {

    private lateinit var imagePoolRoot: File
    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true

    @Before
    fun setUpImagePool() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false

        imagePoolRoot = Files.createTempDirectory("deepseek-media-role-test").toFile()
        ImagePoolManager.initialize(imagePoolRoot, preloadNow = false)
        val poolDirectory = OperitPaths.imagePoolDir(imagePoolRoot)
        File(poolDirectory, "$IMAGE_ID.dat").writeText(IMAGE_BASE64)
        File(poolDirectory, "$IMAGE_ID.meta").writeText(
            """{"mimeType":"image/png","width":1,"height":1}"""
        )
    }

    @After
    fun tearDownImagePool() {
        ImagePoolManager.clear()
        imagePoolRoot.deleteRecursively()
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    @Test
    fun `DeepSeek keeps history images readable through user image inputs`() {
        val history =
            listOf(
                PromptTurn(
                    PromptTurnKind.USER,
                    "user text<link type=\"image\" id=\"$IMAGE_ID\">Image</link>"
                ),
                PromptTurn(
                    PromptTurnKind.ASSISTANT,
                    "assistant text<link id=\"$IMAGE_ID\" type=\"image\">Image</link>"
                ),
                PromptTurn(
                    PromptTurnKind.TOOL_RESULT,
                    "tool text<link type=\"image\" id=\"$IMAGE_ID\">Image</link>"
                )
            )

        val request = buildRequestJson(history)
        val messages = request.getJSONArray("messages")
        assertEquals(4, messages.length())

        val userContent = messages.getJSONObject(0).getJSONArray("content")
        assertEquals("image_url", userContent.getJSONObject(0).getString("type"))
        assertEquals(
            "data:image/png;base64,$IMAGE_BASE64",
            userContent.getJSONObject(0).getJSONObject("image_url").getString("url")
        )
        assertEquals("user text", userContent.getJSONObject(1).getString("text"))

        val assistantMessage = messages.getJSONObject(1)
        assertEquals("assistant", assistantMessage.getString("role"))
        assertEquals("assistant text", assistantMessage.getString("content"))

        val assistantImageMessage = messages.getJSONObject(2)
        assertEquals("user", assistantImageMessage.getString("role"))
        val assistantImageContent = assistantImageMessage.getJSONArray("content")
        assertEquals("text", assistantImageContent.getJSONObject(0).getString("type"))
        assertTrue(assistantImageContent.getJSONObject(0).getString("text").contains("assistant"))
        assertEquals("image_url", assistantImageContent.getJSONObject(1).getString("type"))
        assertEquals(
            "data:image/png;base64,$IMAGE_BASE64",
            assistantImageContent.getJSONObject(1).getJSONObject("image_url").getString("url")
        )

        val toolHistoryMessage = messages.getJSONObject(3)
        assertEquals("user", toolHistoryMessage.getString("role"))
        val toolHistoryContent = toolHistoryMessage.getJSONArray("content")
        assertEquals("image_url", toolHistoryContent.getJSONObject(0).getString("type"))
        assertEquals(
            "data:image/png;base64,$IMAGE_BASE64",
            toolHistoryContent.getJSONObject(0).getJSONObject("image_url").getString("url")
        )
        assertEquals("tool text", toolHistoryContent.getJSONObject(1).getString("text"))

        assertFalse(request.toString().contains("<link"))
        assertTrue(request.toString().contains("image_url"))
    }

    @Test
    fun `DeepSeek emits structured tool result images as readable user input`() {
        val history =
            listOf(
                PromptTurn(
                    PromptTurnKind.TOOL_CALL,
                    """<tool name="read_file"><param name="path">/tmp/image.png</param></tool>"""
                ),
                PromptTurn(
                    PromptTurnKind.TOOL_RESULT,
                    """<tool_result name="read_file"><content>tool text<link type="image" id="$IMAGE_ID">Image</link></content></tool_result>"""
                )
            )

        val request =
            buildRequestJson(
                history = history,
                enableToolCall = true,
                availableTools = listOf(ToolPrompt(name = "read_file", description = "Read a file"))
            )
        val messages = request.getJSONArray("messages")
        val toolMessage =
            (0 until messages.length())
                .map { messages.getJSONObject(it) }
                .single { it.getString("role") == "tool" }

        assertEquals("tool text", toolMessage.getString("content"))

        val imageMessage =
            (0 until messages.length())
                .map { messages.getJSONObject(it) }
                .single {
                    it.getString("role") == "user" &&
                        it.optJSONArray("content")?.toString()?.contains("image_url") == true
                }
        val imageContent = imageMessage.getJSONArray("content")
        assertEquals("text", imageContent.getJSONObject(0).getString("type"))
        assertTrue(imageContent.getJSONObject(0).getString("text").contains("tool result"))
        assertEquals("image_url", imageContent.getJSONObject(1).getString("type"))
        assertEquals(
            "data:image/png;base64,$IMAGE_BASE64",
            imageContent.getJSONObject(1).getJSONObject("image_url").getString("url")
        )

        assertFalse(request.toString().contains("<link"))
        assertTrue(request.toString().contains("image_url"))
    }

    @Test
    fun `OpenAI Responses keeps structured tool result images in function output`() {
        val history =
            listOf(
                PromptTurn(
                    PromptTurnKind.TOOL_CALL,
                    """<tool name="read_file"><param name="path">/tmp/image.png</param></tool>"""
                ),
                PromptTurn(
                    PromptTurnKind.TOOL_RESULT,
                    """<tool_result name="read_file"><content>tool text<link type="image" id="$IMAGE_ID">Image</link></content></tool_result>"""
                )
            )

        val request =
            buildResponsesRequestJson(
                history = history,
                availableTools = listOf(ToolPrompt(name = "read_file", description = "Read a file"))
            )
        val input = request.getJSONArray("input")
        val functionOutput =
            (0 until input.length())
                .map { input.getJSONObject(it) }
                .single { it.getString("type") == "function_call_output" }
        val output = functionOutput.getJSONArray("output")

        assertEquals("input_image", output.getJSONObject(0).getString("type"))
        assertEquals("data:image/png;base64,$IMAGE_BASE64", output.getJSONObject(0).getString("image_url"))
        assertEquals("input_text", output.getJSONObject(1).getString("type"))
        assertEquals("tool text", output.getJSONObject(1).getString("text"))
        assertFalse(request.toString().contains("<link"))
    }

    private fun buildRequestJson(
        history: List<PromptTurn>,
        enableToolCall: Boolean = false,
        availableTools: List<ToolPrompt>? = null
    ): JSONObject {
        val provider =
            DeepseekProvider(
                apiEndpoint = "https://example.test/v1/chat/completions",
                apiKeyProvider = SingleApiKeyProvider("test-key"),
                modelName = "deepseek-test",
                client = OkHttpClient(),
                supportsVision = true,
                enableToolCall = enableToolCall
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
                false,
                false,
                availableTools,
                false
            ) as RequestBody
        val buffer = Buffer()
        body.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    private fun buildResponsesRequestJson(
        history: List<PromptTurn>,
        availableTools: List<ToolPrompt>
    ): JSONObject {
        val provider =
            OpenAIResponsesProvider(
                responsesApiEndpoint = "https://example.test/v1/responses",
                apiKeyProvider = SingleApiKeyProvider("test-key"),
                modelName = "openai-responses-test",
                client = OkHttpClient(),
                supportsVision = true,
                enableToolCall = true
            )
        val method =
            OpenAIResponsesProvider::class.java.declaredMethods.single {
                it.name == "createRequestBody" && it.parameterCount == 7
            }
        method.isAccessible = true
        val body =
            method.invoke(
                provider,
                mock<Context>(),
                history,
                emptyList<ModelParameter<*>>(),
                false,
                false,
                availableTools,
                false
            ) as RequestBody
        val buffer = Buffer()
        body.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    private companion object {
        const val IMAGE_ID = "image-present"
        const val IMAGE_BASE64 = "aW1hZ2U="
    }
}
