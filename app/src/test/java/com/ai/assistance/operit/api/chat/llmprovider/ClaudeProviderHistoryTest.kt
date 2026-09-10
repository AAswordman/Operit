package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ClaudeProviderHistoryTest {

    private fun createProvider(enableToolCall: Boolean = true): ClaudeProvider {
        return ClaudeProvider(
            apiEndpoint = "https://api.anthropic.com/v1/messages",
            apiKeyProvider = SingleApiKeyProvider("sk-ant-test"),
            modelName = "claude-3-5-sonnet-20241022",
            client = OkHttpClient(),
            enableToolCall = enableToolCall
        )
    }

    private fun toolCall(name: String, vararg params: Pair<String, String>): String {
        val body = params.joinToString("") { (key, value) ->
            "<" + "param name=\"$key\">" + value + "</" + "param>"
        }
        return "<" + "tool name=\"$name\">" + body + "</" + "tool>"
    }

    private fun toolResult(name: String, content: String): String =
        "<" + "tool_result name=\"$name\" status=\"success\"><" + "content>" + content + "</" + "content></" + "tool_result>"

    @Test
    fun parallel_tool_results_are_ordered_by_tool_use_definition_order_to_preserve_prompt_cache() {
        val provider = createProvider(enableToolCall = true)

        // Assistant calls 3 tools in order: read_file -> list_files -> grep_code
        val assistantTurn = PromptTurn(
            kind = PromptTurnKind.ASSISTANT,
            content = toolCall("read_file", "path" to "fileA.txt") +
                toolCall("list_files", "path" to "dirB") +
                toolCall("grep_code", "pattern" to "queryC")
        )

        // Due to async parallel execution race, the database recorded them in completion order:
        // list_files finished first, then grep_code, then read_file
        val toolResultTurn = PromptTurn(
            kind = PromptTurnKind.TOOL_RESULT,
            content = toolResult("list_files", "content_list_files") +
                toolResult("grep_code", "content_grep_code") +
                toolResult("read_file", "content_read_file")
        )

        val history = listOf(
            PromptTurn(kind = PromptTurnKind.USER, content = "Inspect project"),
            assistantTurn,
            toolResultTurn
        )

        val serialized = provider.buildSerializedHistory(history)
        val messages = serialized.messagesArray

        assertEquals(3, messages.length()) // user, then assistant, then user (tool_result)
        val assistantMsg = messages.getJSONObject(1)
        assertEquals("role", "assistant", assistantMsg.getString("role"))
        val assistantContent = assistantMsg.getJSONArray("content")
        assertEquals(3, assistantContent.length())

        val toolUse0Id = assistantContent.getJSONObject(0).getString("id")
        val toolUse1Id = assistantContent.getJSONObject(1).getString("id")
        val toolUse2Id = assistantContent.getJSONObject(2).getString("id")

        assertEquals("read_file", assistantContent.getJSONObject(0).getString("name"))
        assertEquals("list_files", assistantContent.getJSONObject(1).getString("name"))
        assertEquals("grep_code", assistantContent.getJSONObject(2).getString("name"))

        val userMsg = messages.getJSONObject(2)
        assertEquals("role", "user", userMsg.getString("role"))
        val userContent = userMsg.getJSONArray("content")
        assertEquals(3, userContent.length())

        // The tool_result blocks in the user message MUST be in the exact order of tool_use (read_file, list_files, grep_code)
        // rather than the out-of-order XML sequence (list_files, grep_code, read_file).
        assertEquals("tool_result", userContent.getJSONObject(0).getString("type"))
        assertEquals(toolUse0Id, userContent.getJSONObject(0).getString("tool_use_id"))
        assertEquals("historical content match", "content_read_file", userContent.getJSONObject(0).getString("content"))


        assertEquals("tool_result", userContent.getJSONObject(1).getString("type"))
        assertEquals(toolUse1Id, userContent.getJSONObject(1).getString("tool_use_id"))
        assertEquals("historical content match", "content_list_files", userContent.getJSONObject(1).getString("content"))


        assertEquals("tool_result", userContent.getJSONObject(2).getString("type"))
        assertEquals(toolUse2Id, userContent.getJSONObject(2).getString("tool_use_id"))
        assertEquals("historical content match", "content_grep_code", userContent.getJSONObject(2).getString("content"))
    }
}
