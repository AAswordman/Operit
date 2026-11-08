package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.stream.withRollbackPrefix
import com.ai.assistance.operit.util.streamnative.nativeMarkdownSplitByInline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolCardStreamBoundaryAndroidTest {
    @get:Rule val composeRule = createComposeRule()

    private fun chars(text: String): Stream<Char> = stream { text.forEach { emit(it) } }
    // XML 属性含双引号，使用原始字符串避免测试样例被误解析为 Kotlin 代码。
    private fun tool(value: String): String =
        """<tool name="create_file"><param name="new">$value</param></tool>"""

    private fun render(prefix: String?, tail: String): StreamMarkdownRendererState {
        val state = StreamMarkdownRendererState()
        val input = if (prefix == null) chars(tail) else chars(tail).withRollbackPrefix(prefix)
        composeRule.setContent {
            MaterialTheme { StreamMarkdownRenderer(input, state = state, renderContent = false) }
        }
        composeRule.waitUntil(20_000) { state.streamParsingCompletedSuccessfully }
        return state
    }

    private fun assertBlocks(state: StreamMarkdownRendererState, expected: List<String>) {
        composeRule.runOnIdle {
            assertEquals(expected, state.nodes.map { it.content.toString() })
            assertEquals(expected, state.renderNodes.map { it.content })
            assertTrue(state.xmlNodeStreams.isEmpty())
        }
    }

    @Test fun ordinaryStreamKeepsConsecutiveToolsIndependent() {
        val calls = listOf(tool("首次"), tool("第二次"), tool("第三次"))
        assertBlocks(render(null, calls.joinToString("")), calls)
    }

    @Test fun closedToolPrefixKeepsFollowingRequestAndResultSeparate() {
        val first = tool("首次")
        val result = """<tool_result name="create_file" status="success"><content>已创建</content></tool_result>"""
        val next = tool("第二次")
        assertBlocks(render(first, result + next), listOf(first, result, next))
    }

    @Test fun unfinishedToolContinuesOnceAndNewToolsStartNewCards() {
        val prefix = """<tool name="create_file"><param name="new">开始"""
        val close = "继续</param></tool>"
        val next = listOf(tool("第二次"), tool("第三次"))
        assertBlocks(render(prefix, close + next.joinToString("")), listOf(prefix + close) + next)
    }

    @Test fun moreThanTwoHundredThousandBytesStayInIndependentCards() {
        val calls = List(120) { tool("$it:" + "x".repeat(2_100)) }
        assertTrue(calls.sumOf { it.toByteArray(Charsets.UTF_8).size } > 200_000)
        val state = render("", calls.joinToString(""))
        assertBlocks(state, calls)
        assertTrue(state.nodes.all { it.content.length < 3_000 })
    }

    @Test fun repeatedRollbackRemovesRevokedCardsWithoutMergingNewOnes() {
        val first = tool("保留")
        val revoked = tool("撤回")
        val replacement = tool("替换")
        val last = tool("末次")
        val state = StreamMarkdownRendererState()
        val input = mutableStateOf(chars(first + revoked))
        composeRule.setContent {
            MaterialTheme { StreamMarkdownRenderer(input.value, state = state, renderContent = false) }
        }
        composeRule.waitUntil(20_000) { state.streamParsingCompletedSuccessfully }
        composeRule.runOnIdle { input.value = chars(replacement).withRollbackPrefix(first) }
        composeRule.waitUntil(20_000) {
            state.streamParsingCompletedSuccessfully && state.collectedContent.toString() == first + replacement
        }
        assertBlocks(state, listOf(first, replacement))
        composeRule.runOnIdle { input.value = chars(last).withRollbackPrefix(first + replacement) }
        composeRule.waitUntil(20_000) {
            state.streamParsingCompletedSuccessfully && state.collectedContent.toString() == first + replacement + last
        }
        assertBlocks(state, listOf(first, replacement, last))
    }

    @Test fun xmlReplayIncludesRestoredPrefixAndEndsBeforeMessageCompletion() {
        val prefix = """<tool name="create_file"><param name="new">开始"""
        val body = "继续"
        val end = "</param></tool>"
        val source = MutableSharedStreamImpl<Char>(replay = Int.MAX_VALUE)
        val input = source.withRollbackPrefix(prefix)
        val state = StreamMarkdownRendererState()
        composeRule.setContent {
            MaterialTheme { StreamMarkdownRenderer(input, state = state, renderContent = false) }
        }
        try {
            composeRule.waitUntil(20_000) { source.subscriptionCount > 0 }
            runBlocking { body.forEach { source.emit(it) } }
            composeRule.waitUntil(20_000) {
                state.xmlNodeStreams.containsKey(0) && state.nodes[0].content.toString() == prefix + body
            }
            composeRule.runOnIdle {
                val replay = state.xmlNodeStreams.getValue(0) as SharedStream<String>
                assertEquals(prefix + body, replay.replayCache.joinToString(""))
            }
            runBlocking { end.forEach { source.emit(it) } }
            composeRule.waitUntil(20_000) {
                state.xmlNodeStreams.isEmpty() && state.renderNodes.singleOrNull()?.content == prefix + body + end
            }
            assertFalse(state.streamParsingCompletedSuccessfully)
        } finally { source.close() }
    }

    @Test fun restoredOpenCodeFenceDoesNotAbsorbTheNextCodeBlock() {
        val first = "```kotlin\nval first = 1\n```\n"
        val open = "```kotlin\nval second = "
        val end = "2\n```\n"
        val next = "```kotlin\nval third = 3\n```\n"
        val state = render(first + open, end + next)
        assertBlocks(state, listOf(first, open + end, next))
        assertTrue(state.nodes.all { it.type == MarkdownProcessorType.CODE_BLOCK })
    }

    @Test fun stringInlineSplitMarksOnlyTheRestoredUnfinishedCode() = runBlocking {
        val prefix = "前文 `ab"
        val input = stream { listOf("cd", "`后文 ", "`new`").forEach { emit(it) } }
        val groups = mutableListOf<Pair<MarkdownProcessorType?, Boolean>>()
        input.nativeMarkdownSplitByInline(null, null, prefix).collect { group ->
            groups.add(group.tag to group.continuesPrevious)
            group.stream.collect { }
        }
        assertEquals(MarkdownProcessorType.INLINE_CODE to true, groups.first())
        assertTrue(groups.drop(1).all { !it.second })
    }
}
