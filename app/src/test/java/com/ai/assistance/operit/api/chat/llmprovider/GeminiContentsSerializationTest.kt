package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Serialization regressions for the Gemini native request body.
 *
 * Gemini rejects a Part whose `data` oneof is uninitialized (an empty `text` reaches the wire as
 * an unset field) and a Content whose `parts` array is empty, answering HTTP 400 with
 * "required oneof field 'data' must have one initialized field" or
 * "contents.parts must not be empty". A history message that only carried thinking text, or only
 * a media link that no longer resolves, must therefore not be serialized as an empty Part.
 */
class GeminiContentsSerializationTest {
    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true
    private var imagePoolRoot: File? = null

    @Before
    fun disableAndroidLogging() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLogging() {
        imagePoolRoot?.let { root ->
            ImagePoolManager.clear()
            root.deleteRecursively()
        }
        imagePoolRoot = null
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    @Test
    fun `thinking-only assistant turn does not become an empty text part`() {
        val contents =
            buildContents(
                history = listOf(
                    PromptTurn(PromptTurnKind.USER, "hello"),
                    PromptTurn(PromptTurnKind.ASSISTANT, "<think>internal reasoning only</think>"),
                    PromptTurn(PromptTurnKind.USER, "next question"),
                )
            )

        assertProtocolValid(contents)
        assertEquals(2, contents.length())
        assertEquals(listOf("user", "user"), roles(contents))
        assertFalse(contents.toString().contains("internal reasoning"))
    }

    @Test
    fun `thinking-only assistant turn is dropped in the tool-call protocol too`() {
        val contents =
            buildContents(
                history = listOf(
                    PromptTurn(PromptTurnKind.USER, "hello"),
                    PromptTurn(PromptTurnKind.ASSISTANT, "<thinking>internal reasoning only</thinking>"),
                    PromptTurn(PromptTurnKind.USER, "next question"),
                ),
                enableToolCall = true,
            )

        assertProtocolValid(contents)
        assertEquals(listOf("user", "user"), roles(contents))
    }

    @Test
    fun `plain text turns keep their text and roles`() {
        val contents =
            buildContents(
                history = listOf(
                    PromptTurn(PromptTurnKind.USER, "hello"),
                    PromptTurn(PromptTurnKind.ASSISTANT, "hi there"),
                    PromptTurn(PromptTurnKind.USER, "next question"),
                )
            )

        assertProtocolValid(contents)
        assertEquals(listOf("user", "model", "user"), roles(contents))
        assertEquals("hello", firstText(contents, 0))
        assertEquals("hi there", firstText(contents, 1))
        assertEquals("next question", firstText(contents, 2))
    }

    @Test
    fun `preserved thinking text is still serialized when the caller keeps it`() {
        val contents =
            buildContents(
                history = listOf(
                    PromptTurn(PromptTurnKind.USER, "hello"),
                    PromptTurn(PromptTurnKind.ASSISTANT, "<think>kept reasoning</think>answer"),
                ),
                preserveThinkInHistory = true,
            )

        assertProtocolValid(contents)
        assertEquals(listOf("user", "model"), roles(contents))
        assertTrue(firstText(contents, 1).contains("kept reasoning"))
        assertTrue(firstText(contents, 1).contains("answer"))
    }

    @Test
    fun `function call and function response survive an empty assistant body`() {
        val contents =
            buildContents(
                history = listOf(
                    PromptTurn(PromptTurnKind.USER, "read the file"),
                    PromptTurn(
                        PromptTurnKind.TOOL_CALL,
                        """<tool name="read_file"><param name="path">/tmp/a.txt</param></tool>"""
                    ),
                    PromptTurn(
                        PromptTurnKind.TOOL_RESULT,
                        """<tool_result name="read_file"><content>alpha</content></tool_result>"""
                    ),
                ),
                enableToolCall = true,
            )

        assertProtocolValid(contents)
        assertEquals(listOf("user", "model", "user"), roles(contents))

        val callParts = parts(contents, 1)
        assertEquals(1, callParts.length())
        val functionCall = callParts.getJSONObject(0).getJSONObject("functionCall")
        assertEquals("read_file", functionCall.getString("name"))
        assertEquals("/tmp/a.txt", functionCall.getJSONObject("args").getString("path"))

        val resultParts = parts(contents, 2)
        assertEquals(1, resultParts.length())
        val functionResponse = resultParts.getJSONObject(0).getJSONObject("functionResponse")
        assertEquals("read_file", functionResponse.getString("name"))
        assertEquals("alpha", functionResponse.getJSONObject("response").getString("result"))
    }

    @Test
    fun `assistant text next to a function call stays in the same content`() {
        val contents =
            buildContents(
                history = listOf(
                    PromptTurn(PromptTurnKind.USER, "read the file"),
                    PromptTurn(
                        PromptTurnKind.ASSISTANT,
                        """reading now<tool name="read_file"><param name="path">/tmp/a.txt</param></tool>"""
                    ),
                    PromptTurn(
                        PromptTurnKind.TOOL_RESULT,
                        """<tool_result name="read_file"><content>alpha</content></tool_result>"""
                    ),
                ),
                enableToolCall = true,
            )

        assertProtocolValid(contents)
        val callParts = parts(contents, 1)
        assertEquals(2, callParts.length())
        assertEquals("reading now", callParts.getJSONObject(0).getString("text"))
        assertTrue(callParts.getJSONObject(1).has("functionCall"))
    }

    @Test
    fun `stale thought-signature metadata leaves no part behind`() {
        val contents =
            buildContents(
                history = listOf(
                    PromptTurn(PromptTurnKind.USER, "hello"),
                    PromptTurn(
                        PromptTurnKind.ASSISTANT,
                        """<meta provider="gemini:thought_signature">c2lnbmF0dXJl</meta>"""
                    ),
                    PromptTurn(PromptTurnKind.USER, "next question"),
                ),
                enableToolCall = false,
            )

        assertProtocolValid(contents)
        assertEquals(listOf("user", "user"), roles(contents))
        assertFalse(contents.toString().contains("thought_signature"))
    }

    @Test
    fun `image and text stay together in one multimodal content`() {
        val imageId = registerImage("image-present")

        val contents =
            buildContents(
                history = listOf(
                    PromptTurn(
                        PromptTurnKind.USER,
                        """look at this<link type="image" id="$imageId">Image</link>"""
                    )
                )
            )

        assertProtocolValid(contents)
        assertEquals(1, contents.length())
        val imageParts = parts(contents, 0)
        assertEquals(2, imageParts.length())
        val inlineData = imageParts.getJSONObject(0).getJSONObject("inline_data")
        assertEquals("image/png", inlineData.getString("mime_type"))
        assertEquals(IMAGE_BASE64, inlineData.getString("data"))
        assertEquals("look at this", imageParts.getJSONObject(1).getString("text"))
    }

    @Test
    fun `message whose media link no longer resolves is dropped instead of emptied`() {
        // No image is registered under this id, so the link resolves to nothing and the whole
        // message would otherwise serialize as a content with an empty parts array.
        ImagePoolManager.initialize(tempImagePoolRoot(), preloadNow = false)

        val contents =
            buildContents(
                history = listOf(
                    PromptTurn(
                        PromptTurnKind.USER,
                        """<link type="image" id="image-missing">Image</link>"""
                    )
                )
            )

        assertEquals(0, contents.length())
    }

    @Test
    fun `blank system prompt produces no system instruction`() {
        val (contents, systemInstruction) =
            buildContentsAndSystemInstruction(
                history = listOf(
                    PromptTurn(PromptTurnKind.SYSTEM, "   "),
                    PromptTurn(PromptTurnKind.USER, "hello"),
                )
            )

        assertProtocolValid(contents)
        assertNull(systemInstruction)
        assertEquals(1, contents.length())
    }

    @Test
    fun `system prompt is kept when it carries text`() {
        val (contents, systemInstruction) =
            buildContentsAndSystemInstruction(
                history = listOf(
                    PromptTurn(PromptTurnKind.SYSTEM, "you are a helpful assistant"),
                    PromptTurn(PromptTurnKind.USER, "hello"),
                )
            )

        assertProtocolValid(contents)
        assertNotNull(systemInstruction)
        val systemParts = systemInstruction!!.getJSONArray("parts")
        assertEquals(1, systemParts.length())
        assertEquals(
            "you are a helpful assistant",
            systemParts.getJSONObject(0).getString("text")
        )
        assertEquals(1, contents.length())
    }

    @Test
    fun `request body is rejected locally when nothing is sendable`() {
        val history =
            listOf(PromptTurn(PromptTurnKind.ASSISTANT, "<think>internal reasoning only</think>"))

        try {
            buildRequestBody(history)
            fail("a history without sendable content must not be sent")
        } catch (expected: InvocationTargetException) {
            val cause = expected.targetException
            assertTrue(
                "unexpected failure: $cause",
                cause is GeminiProvider.EmptyContentsException
            )
        }
    }

    @Test
    fun `request body carries contents without empty parts`() {
        val request =
            buildRequestBody(
                listOf(
                    PromptTurn(PromptTurnKind.SYSTEM, "you are a helpful assistant"),
                    PromptTurn(PromptTurnKind.USER, "hello"),
                    PromptTurn(PromptTurnKind.ASSISTANT, "<think>internal reasoning only</think>"),
                    PromptTurn(PromptTurnKind.USER, "next question"),
                )
            )

        val contents = request.getJSONArray("contents")
        assertProtocolValid(contents)
        assertEquals(listOf("user", "user"), roles(contents))
        assertEquals(
            "you are a helpful assistant",
            request
                .getJSONObject("systemInstruction")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        )
    }

    private fun buildContents(
        history: List<PromptTurn>,
        enableToolCall: Boolean = false,
        preserveThinkInHistory: Boolean = false,
    ): JSONArray = buildContentsAndSystemInstruction(history, enableToolCall, preserveThinkInHistory).first

    private fun buildContentsAndSystemInstruction(
        history: List<PromptTurn>,
        enableToolCall: Boolean = false,
        preserveThinkInHistory: Boolean = false,
    ): Pair<JSONArray, JSONObject?> {
        val method =
            GeminiProvider::class.java.declaredMethods.single {
                it.name == "buildContentsAndCountTokens" && it.parameterCount == 3
            }
        method.isAccessible = true
        val result = method.invoke(provider(enableToolCall), history, null, preserveThinkInHistory)
        val contentsAndSystem = (result as Pair<*, *>).first as Pair<*, *>
        return (contentsAndSystem.first as JSONArray) to (contentsAndSystem.second as JSONObject?)
    }

    private fun buildRequestBody(history: List<PromptTurn>): JSONObject {
        val method =
            GeminiProvider::class.java.declaredMethods.single {
                it.name == "createRequestBody" && it.parameterCount == 6
            }
        method.isAccessible = true
        val context = mock<Context>()
        whenever(context.getString(any<Int>())).thenReturn("request body")
        whenever(context.getString(any<Int>(), any())).thenReturn("request body")
        val body =
            method.invoke(
                provider(enableToolCall = false),
                context,
                history,
                emptyList<ModelParameter<*>>(),
                false,
                null,
                false,
            ) as RequestBody
        val buffer = Buffer()
        body.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    private fun provider(enableToolCall: Boolean) =
        GeminiProvider(
            apiEndpoint = "https://example.test",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "gemini-test",
            client = OkHttpClient(),
            enableToolCall = enableToolCall,
        )

    /**
     * Every Content needs a Part, and every Part needs one initialized `data` field. An empty
     * `text` is the exact shape the endpoint answers with HTTP 400.
     */
    private fun assertProtocolValid(contents: JSONArray) {
        assertTrue("request must keep at least one content", contents.length() > 0)
        for (index in 0 until contents.length()) {
            val content = contents.getJSONObject(index)
            val parts = content.getJSONArray("parts")
            assertTrue("contents[$index] has no parts", parts.length() > 0)
            for (partIndex in 0 until parts.length()) {
                val part = parts.getJSONObject(partIndex)
                val hasDataField =
                    DATA_FIELDS.any { field -> part.has(field) && !part.isNull(field) }
                assertTrue("contents[$index].parts[$partIndex] has no data field", hasDataField)
                if (part.has("text")) {
                    assertFalse(
                        "contents[$index].parts[$partIndex] has empty text",
                        part.getString("text").isEmpty()
                    )
                }
            }
        }
    }

    private fun roles(contents: JSONArray): List<String> =
        (0 until contents.length()).map { contents.getJSONObject(it).getString("role") }

    private fun parts(contents: JSONArray, index: Int): JSONArray =
        contents.getJSONObject(index).getJSONArray("parts")

    private fun firstText(contents: JSONArray, index: Int): String =
        parts(contents, index).getJSONObject(0).getString("text")

    private fun registerImage(id: String): String {
        val poolDirectory = OperitPaths.imagePoolDir(tempImagePoolRoot())
        File(poolDirectory, "$id.dat").writeText(IMAGE_BASE64)
        File(poolDirectory, "$id.meta").writeText("""{"mimeType":"image/png","width":1,"height":1}""")
        return id
    }

    private fun tempImagePoolRoot(): File {
        val existing = imagePoolRoot
        if (existing != null) return existing
        val created = Files.createTempDirectory("gemini-contents-test").toFile()
        ImagePoolManager.initialize(created, preloadNow = false)
        imagePoolRoot = created
        return created
    }

    private companion object {
        const val IMAGE_BASE64 = "aW1hZ2U="

        val DATA_FIELDS =
            listOf(
                "text",
                "inline_data",
                "inlineData",
                "file_data",
                "fileData",
                "functionCall",
                "function_call",
                "functionResponse",
                "function_response",
                "executableCode",
                "executable_code",
                "codeExecutionResult",
                "code_execution_result",
            )
    }
}
