package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.ui.common.markdown.DefaultXmlRenderer
import com.ai.assistance.operit.ui.common.markdown.NoopMarkdownNodeGrouper
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRendererState
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamingTimelineRefreshAndroidTest {
    @get:Rule val composeRule = createComposeRule()

    private fun meta() = """<meta provider="openai:responses_output_item">secret-payload</meta>"""
    private fun request(index: Int) = """<tool name="read_file"><param name="path">file-$index</param></tool>"""
    private fun result(index: Int, body: String) =
        """<tool_result name="read_file"><content>file-$index: $body</content></tool_result>"""

    private fun awaitVisible(text: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodes(hasContentDescription(text, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasContentDescription(text, substring = true), useUnmergedTree = true)
            .onFirst().assertIsDisplayed()
    }

    private fun verifyRefreshWhileStreaming(split: Boolean) {
        val source = MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE)
        val visible = mutableStateOf(true)
        val statusRevision = mutableStateOf(0)
        val renderer = AtomicReference<StreamMarkdownRendererState>()
        composeRule.setContent {
            MaterialTheme {
                if (visible.value) {
                    Column {
                        Text("工具状态：${statusRevision.value}")
                        val messages = remember {
                            listOf(ChatMessage(sender = "ai", timestamp = 100L, contentStream = source))
                        }
                        val entries = rememberChatTimelineEntries(
                            messages, "streaming-refresh", true, true, ToolCollapseMode.ALL,
                        )
                        SideEffect { renderer.set(entries.lastOrNull()?.slice?.state) }
                        for (entry in entries) key(entry.key) {
                            entry.slice?.fragment?.Render(
                                modifier = Modifier,
                                textColor = Color.Black,
                                fontSize = 14.sp,
                                onLinkClick = null,
                                xmlRenderer = DefaultXmlRenderer(),
                                nodeGrouper = NoopMarkdownNodeGrouper,
                                enableDialogs = false,
                                fillMaxWidth = true,
                            )
                        }
                    }
                } else {
                    Text("其他页面")
                }
            }
        }
        try {
            composeRule.waitUntil(20_000) { source.subscriptionCount > 0 }
            val prefix = buildString {
                repeat(if (split) 13 else 1) { index ->
                    append(request(index))
                    append(result(index, if (index == 0) "源码中的单个反引号 `" else "普通结果"))
                }
            }
            runBlocking { source.emit(prefix + meta() + "首轮正文已经显示。\n") }
            awaitVisible("首轮正文已经显示。")
            composeRule.runOnIdle {
                val state = requireNotNull(renderer.get())
                assertFalse("源流未结束时也必须可见", state.streamParsingCompletedSuccessfully)
                assertEquals(split, shouldSplitChatMessage(state.renderNodes.toList()))
                assertFalse(state.collectedContent.toString().contains("secret-payload"))
                visible.value = false
            }
            composeRule.waitUntil(20_000) { source.subscriptionCount == 0 }
            composeRule.runOnIdle { visible.value = true }
            awaitVisible("首轮正文已经显示。")

            // 重订阅后刷新外部状态，再执行下一轮工具，早期正文不能倒退或停在旧快照。
            composeRule.runOnIdle { statusRevision.value++ }
            awaitVisible("首轮正文已经显示。")
            runBlocking {
                source.emit(request(99) + result(99, "```text\n另一次源码结果") + meta() + "第二轮正文继续显示。\n")
            }
            awaitVisible("第二轮正文继续显示。")
            awaitVisible("首轮正文已经显示。")
            composeRule.runOnIdle { statusRevision.value++ }
            runBlocking { source.emit(meta() + "第三次刷新后仍在输出。\n") }
            awaitVisible("第三次刷新后仍在输出。")
            awaitVisible("第二轮正文继续显示。")
            composeRule.runOnIdle {
                assertFalse(requireNotNull(renderer.get()).streamParsingCompletedSuccessfully)
                assertTrue(source.subscriptionCount > 0)
            }
            source.close()
            composeRule.waitUntil(20_000) { renderer.get()?.streamParsingCompletedSuccessfully == true }
            awaitVisible("第三次刷新后仍在输出。")
        } finally {
            source.close()
        }
    }

    @Test fun anOrdinaryMessageKeepsUpdatingAfterLeavingAndReturning() {
        verifyRefreshWhileStreaming(split = false)
    }

    @Test fun aSplitMessageKeepsUpdatingAfterLeavingAndReturning() {
        verifyRefreshWhileStreaming(split = true)
    }
}