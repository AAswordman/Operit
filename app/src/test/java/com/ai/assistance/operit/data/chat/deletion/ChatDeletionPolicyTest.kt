package com.ai.assistance.operit.data.chat.deletion

import com.ai.assistance.operit.data.model.ChatEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatDeletionPolicyTest {
    @Test
    fun lockingOneChatDoesNotBlockAnotherChat() {
        val locked = ChatEntity(id = "locked", title = "锁定会话", locked = true)
        val other = ChatEntity(id = "other", title = "其他会话")
        assertEquals(ChatDeletionResult.LOCKED, ChatDeletionPolicy.blockReason(locked, "other", true))
        assertNull(ChatDeletionPolicy.blockReason(other, "locked", true))
    }

    @Test
    fun unlockingChatImmediatelyRemovesItsLockBlock() {
        val locked = ChatEntity(id = "chat", title = "会话", locked = true)
        assertEquals(ChatDeletionResult.LOCKED, ChatDeletionPolicy.blockReason(locked, null))
        assertNull(ChatDeletionPolicy.blockReason(locked.copy(locked = false), null))
    }

    @Test
    fun currentChatCanPrepareDeletionButMustMoveAwayBeforeStorageDeletion() {
        val target = ChatEntity(id = "chat", title = "当前会话")
        assertNull(ChatDeletionPolicy.blockReason(target, "chat", allowCurrentChat = true))
        assertEquals(ChatDeletionResult.CURRENT_CHAT, ChatDeletionPolicy.blockReason(target, "chat"))
        assertNull(ChatDeletionPolicy.blockReason(target, "replacement"))
    }

    @Test
    fun preparingCurrentChatNeverBypassesItsLock() {
        val target = ChatEntity(id = "chat", title = "锁定的当前会话", locked = true)
        assertEquals(ChatDeletionResult.LOCKED, ChatDeletionPolicy.blockReason(target, "chat", true))
    }

    @Test
    fun missingChatIsReportedAsMissingRatherThanLocked() {
        assertEquals(ChatDeletionResult.NOT_FOUND, ChatDeletionPolicy.blockReason(null, "other", true))
    }
}