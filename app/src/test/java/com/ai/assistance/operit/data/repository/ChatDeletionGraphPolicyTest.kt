package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDeletionGraphPolicyTest {
    @Test
    fun lockedNodeProtectsItsWholeGraph() {
        val parent = chat("parent", locked = true)
        val child = chat("child", parentId = parent.id)
        val sibling = chat("sibling", parentId = parent.id)

        val selected =
            ChatDeletionGraphPolicy.selectDeletableChats(
                listOf(parent, child, sibling),
                setOf(parent.id, child.id, sibling.id),
            )

        assertEquals(emptyList<ChatEntity>(), selected)
    }

    @Test
    fun nodeOutsideScopeProtectsItsWholeGraph() {
        val parent = chat("parent")
        val child = chat("child", parentId = parent.id)

        val selected =
            ChatDeletionGraphPolicy.selectDeletableChats(
                listOf(parent, child),
                setOf(parent.id),
            )

        assertEquals(emptyList<ChatEntity>(), selected)
    }

    @Test
    fun completeUnlockedGraphIsOrderedChildFirst() {
        val parent = chat("parent")
        val child = chat("child", parentId = parent.id)
        val grandchild = chat("grandchild", parentId = child.id)
        val selected =
            ChatDeletionGraphPolicy.selectDeletableChats(
                listOf(parent, child, grandchild),
                setOf(parent.id, child.id, grandchild.id),
            )

        assertEquals(
            listOf("grandchild", "child", "parent"),
            ChatDeletionGraphPolicy.orderChildFirst(selected).map(ChatEntity::id),
        )
    }

    private fun chat(
        id: String,
        parentId: String? = null,
        locked: Boolean = false,
    ) =
        ChatEntity(
            id = id,
            title = id,
            parentChatId = parentId,
            chatKind = if (parentId == null) ChatKind.NORMAL.name else ChatKind.SUBAGENT.name,
            locked = locked,
        )
}
