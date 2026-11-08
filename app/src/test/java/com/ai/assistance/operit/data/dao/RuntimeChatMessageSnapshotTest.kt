package com.ai.assistance.operit.data.dao

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.data.model.MessageVariantEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeChatMessageSnapshotTest {
    @Test
    fun `empty history stays empty`() {
        assertEquals(emptyList<Any>(), RuntimeChatMessageSnapshot(emptyList(), emptyList(), emptyList()).toChatMessages())
    }

    @Test
    fun `selected-only snapshot preserves the previous hydrated history`() {
        val summary = message(100, "summary", "总结原文")
        val user = message(200, "user", "用户原文").copy(displayMode = ChatMessageDisplayMode.HIDDEN_PLACEHOLDER.name)
        val original = message(300, "ai", "<think>原始思考</think>原始正文")
        val regenerated = message(400, "ai", "基础正文").copy(selectedVariantIndex = 2, isFavorite = true)
        val selected = variant(400, 2, "<think>选中思考</think><tool name=\"read_file\">完整调用</tool>选中正文")
            .copy(provider = "selected-provider", modelName = "selected-model", inputTokens = 123, outputTokens = 456,
                cachedInputTokens = 78, sentAt = 1_000, outputDurationMs = 2_000, waitDurationMs = 300, completedAt = 4_000)
        val actual = RuntimeChatMessageSnapshot(
            messages = listOf(summary, user, original, regenerated),
            selectedVariants = listOf(selected),
            variantCounts = listOf(MessageVariantCount(300, 3), MessageVariantCount(400, 4)),
        ).toChatMessages()

        val expected = listOf(
            summary.toChatMessage(), user.toChatMessage(),
            original.toChatMessage().copy(variantCount = 4), selected.applyTo(regenerated.toChatMessage(), 5),
        )
        // 渲染思考片段会生成随机标记，完整比较结构化正文和其他所有消息字段。
        assertEquals(expected.map { it.copy(content = "") }, actual.map { it.copy(content = "") })
        expected.zip(actual).forEach { (expectedMessage, actualMessage) ->
            assertEquals(expectedMessage.sections, MessageSectionCodec.parse(actualMessage.content))
        }
    }

    @Test
    fun `large selected text is not truncated by runtime hydration`() {
        val content = "完整上下文原文\n".repeat(30_000)
        val base = message(500, "ai", "基础正文").copy(selectedVariantIndex = 3)
        val result = RuntimeChatMessageSnapshot(
            listOf(base), listOf(variant(500, 3, content)), listOf(MessageVariantCount(500, 3)),
        ).toChatMessages().single()
        assertEquals(content, result.content)
        assertEquals(3, result.selectedVariantIndex)
        assertEquals(4, result.variantCount)
        assertEquals("原始角色", result.roleName)
    }

    @Test(expected = NoSuchElementException::class)
    fun `missing selected variant keeps the existing error behavior`() {
        RuntimeChatMessageSnapshot(
            listOf(message(600, "ai", "基础正文").copy(selectedVariantIndex = 2)),
            emptyList(), listOf(MessageVariantCount(600, 1)),
        ).toChatMessages()
    }

    private fun message(timestamp: Long, sender: String, content: String) = MessageEntity.fromChatMessage(
        chatId = "chat",
        message = ChatMessage(sender = sender, content = content, timestamp = timestamp, roleName = "原始角色"),
        orderIndex = timestamp.toInt(),
    )

    private fun variant(timestamp: Long, index: Int, content: String) = MessageVariantEntity.fromChatMessage(
        chatId = "chat", messageTimestamp = timestamp, variantIndex = index,
        message = ChatMessage(sender = "ai", content = content, timestamp = timestamp),
    )
}
