package com.ai.assistance.operit.services.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatDraftStoreTest {
    @Test fun backgroundAttachmentStaysWithItsOriginalChat() {
        val store = ChatDraftStore<List<String>>(emptyList())
        store.switchTo("a", emptyList())
        val target = store.captureTarget()
        store.switchTo("b", listOf("existing-a"))
        assertEquals(listOf("existing-b"), store.update(target, listOf("existing-b")) { it + "late-a" })
        assertEquals(listOf("existing-a", "late-a"), store.switchTo("a", listOf("existing-b")))
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun deletingChatRejectsItsPendingAttachmentEvenAfterIdIsReused() {
        val store = ChatDraftStore<List<String>>(emptyList())
        store.switchTo("a", emptyList())
        val target = store.captureTarget()
        store.remove("a")
        store.switchTo("a", emptyList())
        store.update(target, emptyList()) { it + "deleted-chat-attachment" }
    }

    @Test fun firstChatKeepsTheOwnerOfAnInFlightAttachment() {
        val store = ChatDraftStore<List<String>>(emptyList())
        val target = store.captureTarget()
        store.switchTo("a", emptyList())
        store.switchTo("b", emptyList())
        assertEquals(emptyList<String>(), store.update(target, emptyList()) { it + "photo" })
        assertEquals(listOf("photo"), store.switchTo("a", emptyList()))
    }

    @Test fun attachmentDraftsFollowTheChatAcrossSwitches() {
        val store = ChatDraftStore<List<String>>(emptyList())
        assertEquals(emptyList<String>(), store.switchTo("a", emptyList()))
        assertEquals(emptyList<String>(), store.switchTo("b", listOf("a.png")))
        assertEquals(listOf("a.png"), store.switchTo("a", listOf("b.pdf")))
        assertEquals(listOf("b.pdf"), store.switchTo("b", emptyList()))
        assertEquals(emptyList<String>(), store.switchTo("a", listOf("b.pdf")))
    }

    @Test fun replyDraftDoesNotLeakIntoAnotherChat() {
        val store = ChatDraftStore<String?>(null)
        assertNull(store.switchTo("a", null))
        assertNull(store.switchTo("b", "quote-from-a"))
        assertEquals("quote-from-a", store.switchTo("a", null))
    }

    @Test fun aDraftWrittenBeforeFirstChatIsKeptForThatChat() {
        val store = ChatDraftStore<List<String>>(emptyList())
        assertEquals(listOf("photo"), store.switchTo("new-chat", listOf("photo")))
        assertEquals(emptyList<String>(), store.switchTo(null, listOf("photo")))
        assertEquals(emptyList<String>(), store.switchTo("other-chat", emptyList()))
        assertEquals(listOf("photo"), store.switchTo("new-chat", emptyList()))
    }
}
