package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.api.chat.enhance.OrderedToolResults
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ClaudeOrderedToolHistoryTest {
    private var systemLog = true
    private var fileLog = true

    @Before fun disableAndroidLogging() {
        systemLog = AppLogger.enableSystemLog
        fileLog = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After fun restoreLogging() {
        AppLogger.enableSystemLog = systemLog
        AppLogger.enableFileLogging = fileLog
    }

    @Test
    fun `Claude tool history is identical after ordered stream is split into saved turns`() = runBlocking {
        val stream = StringBuilder()
        val ordered = OrderedToolResults(3) { stream.append(ToolExecutionManager.ensureOwnLine(it)) }
        ordered.complete(2, ToolResult("read_file", false, StringResultData(""), "permission denied"))
        ordered.complete(1, ToolResult("read_file", true, StringResultData("content-b")))
        ordered.complete(0, ToolExecutionManager.aggregateToolResults(
            "read_file", flowOf(
                ToolResult("read_file", true, StringResultData("content-a1")),
                ToolResult("read_file", true, StringResultData("content-a2"))
            )
        ))
        val batch = ordered.finish()
        val calls = listOf("a", "b", "b").map { path ->
            """<tool name="read_file"><param name="path">$path</param></tool>"""
        }
        val user = PromptTurn(PromptTurnKind.USER, "Read files")
        val live = buildMessages(listOf(
            user,
            PromptTurn(PromptTurnKind.ASSISTANT, calls.joinToString("\n")),
            PromptTurn(PromptTurnKind.TOOL_RESULT, batch.message)
        ))
        // Exercise the production Claude serializer with the per-block turns used by saved history.
        // This is not a database or native XML splitter test.
        val savedResultTurns = ChatMarkupRegex.toolResultAnyPattern.findAll(stream.toString()).map {
            PromptTurn(PromptTurnKind.TOOL_RESULT, it.value)
        }.toList()
        val rebuilt = buildMessages(
            listOf(user) + calls.map { PromptTurn(PromptTurnKind.TOOL_CALL, it) } + savedResultTurns +
                listOf(PromptTurn(PromptTurnKind.ASSISTANT, "Done"), PromptTurn(PromptTurnKind.USER, "Continue"))
        )
        assertEquals(3, savedResultTurns.size)
        for (i in 0 until live.length()) {
            assertEquals(live.getJSONObject(i).toString(), rebuilt.getJSONObject(i).toString())
        }
        val uses = live.getJSONObject(1).getJSONArray("content")
        val results = live.getJSONObject(2).getJSONArray("content")
        assertEquals(3, results.length())
        for (i in 0 until 3) {
            assertEquals(uses.getJSONObject(i).getString("id"), results.getJSONObject(i).getString("tool_use_id"))
        }
        assertEquals("content-a1\ncontent-a2", results.getJSONObject(0).getString("content"))
        assertEquals("content-b", results.getJSONObject(1).getString("content"))
        assertEquals("<error>permission denied</error>", results.getJSONObject(2).getString("content"))
    }

    private fun buildMessages(history: List<PromptTurn>): JSONArray {
        val provider = ClaudeProvider(
            apiEndpoint = "https://example.test/v1/messages",
            apiKeyProvider = SingleApiKeyProvider("test"),
            modelName = "test",
            client = OkHttpClient(),
            enableToolCall = true
        )
        val serialize = ClaudeProvider::class.java.getDeclaredMethod(
            "buildSerializedHistory", List::class.java, Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val serialized = checkNotNull(serialize.invoke(provider, history, false))
        val messages = serialized.javaClass.getDeclaredField("messagesArray").apply { isAccessible = true }
        return messages.get(serialized) as JSONArray
    }
}
