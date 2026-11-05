package com.ai.assistance.operit.util.stream

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.plugins.StreamPlugin
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for issue #1375.
 *
 * Tool calls are emitted as `<tool_XXXX name="...">...</tool_XXXX>` markup that reaches the parser
 * as an arbitrary sequence of chunks. The parser must recognise the opening tag no matter where a
 * chunk boundary lands, including inside `<`, `<tool_`, the tag name, the attributes and the
 * closing tag. A boundary-dependent miss does not merely delay the call: the unterminated markup
 * is flushed as ordinary text, so the model's tool call disappears from the round.
 */
class ToolTagStreamParsingTest {

    private var previousSystemLogEnabled = true

    @Before
    fun disableAndroidSystemLogForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreAndroidSystemLog() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    private val toolBlock =
        "<tool_AB12 name=\"get_system_notifications\">\n" +
            "<param name=\"filter\">qq</param>\n" +
            "<param name=\"limit\">5</param>\n" +
            "</tool_AB12>"

    private val document = "前置说明文本\n$toolBlock\n尾随说明文本"

    @Test
    fun everyTwoChunkSplitKeepsToolBlockIntact() = runBlocking {
        for (splitAt in 0..document.length) {
            val chunks = listOf(document.substring(0, splitAt), document.substring(splitAt))
            assertParsed(chunks, splitAt)
        }
    }

    @Test
    fun everySingleCharacterSplitKeepsToolBlockIntact() = runBlocking {
        val chunks = document.map { it.toString() }
        assertParsed(chunks, splitPosition = -1)
    }

    @Test
    fun toolTagSplitAcrossManySmallChunksKeepsToolBlockIntact() = runBlocking {
        val chunkSizes = listOf(1, 2, 3, 5, 8)
        for (size in chunkSizes) {
            val chunks = document.chunked(size)
            assertParsed(chunks, splitPosition = size)
        }
    }

    private suspend fun assertParsed(chunks: List<String>, splitPosition: Int) {
        val groups = mutableListOf<Pair<StreamPlugin?, String>>()
        chunks.asStream().splitBy(listOf(StreamXmlPlugin())).collect { group ->
            val text = StringBuilder()
            group.stream.collect { piece -> text.append(piece) }
            groups += (group.tag as StreamPlugin?) to text.toString()
        }

        val reassembled = groups.joinToString(separator = "") { it.second }
        assertEquals(
            "chunk split at $splitPosition lost or reordered characters",
            document,
            reassembled,
        )

        val toolGroups = groups.filter { it.first is StreamXmlPlugin }
        assertEquals(
            "chunk split at $splitPosition produced ${toolGroups.size} tool groups",
            1,
            toolGroups.size,
        )
        assertEquals(
            "chunk split at $splitPosition truncated the tool block",
            toolBlock,
            toolGroups.single().second,
        )
        assertTrue(
            "chunk split at $splitPosition must keep the leading text as plain text",
            groups.first { it.first == null }.second.contains("前置说明文本"),
        )
    }
}
