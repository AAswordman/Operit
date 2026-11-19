package com.ai.assistance.operit.ui.features.chat.viewmodel
import org.junit.Assert.assertNull

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingMessageQueueStoreTest {
    @Test
    fun rejectedDeferredSendDoesNotRestartAfterSummaryReturnsToIdle() {
        val store = PendingMessageQueueStore()
        store.enqueue("chat-a", "first", true)
        store.enqueue("chat-a", "second", true)
        val inFlight = store.takeNextAutoDequeue("chat-a")!!
        assertFalse(store.consumeAutoDequeueSignal("chat-a", true))
        store.restore("chat-a", inFlight, pauseAutoSend = true)
        assertFalse(store.consumeAutoDequeueSignal("chat-a", false))
        assertFalse(store.consumeAutoDequeueSignal("chat-a", false))
        assertEquals(listOf("first", "second"), store.states.value.getValue("chat-a").messages.map { it.text })
        assertEquals(inFlight, store.remove("chat-a", inFlight.id))
    }

    @Test
    fun attachmentsRemainWithTheirMessageThroughSteerReturnAndDequeue() {
        val store = PendingMessageQueueStore()
        val attachment = com.ai.assistance.operit.data.model.AttachmentInfo(
            "/tmp/picture.png", "picture.png", "image/png", 123L)
        val source = mutableListOf(attachment)
        assertTrue(store.enqueue("chat-a", "", true, source) != null)
        source.clear()
        store.enqueue("chat-b", "other chat", true)
        val original = store.states.value.getValue("chat-a").messages.single()
        assertEquals(listOf(attachment), original.attachments)
        assertTrue(store.states.value.getValue("chat-b").messages.single().attachments.isEmpty())
        store.remove("chat-a", original.id)
        store.restore("chat-a", original.copy(isSteering = true))
        store.returnSteer("chat-a", original)
        assertEquals(listOf(attachment), store.states.value.getValue("chat-a").messages.single().attachments)
        assertFalse(store.consumeAutoDequeueSignal("chat-a", false))
        store.consumeAutoDequeueSignal("chat-a", true)
        assertEquals(listOf(attachment), store.takeNextAutoDequeue("chat-a")!!.attachments)
    }

    @Test
    fun returningMiddleSteerPreservesOriginalQueueOrder() {
        val store = PendingMessageQueueStore()
        listOf("A", "B", "C").forEach { store.enqueue("chat-a", it, true) }
        val middle = store.states.value.getValue("chat-a").messages[1]
        store.remove("chat-a", middle.id)
        store.restore("chat-a", middle.copy(isSteering = true))
        store.returnSteer("chat-a", middle)
        assertEquals(listOf("A", "B", "C"),
            store.states.value.getValue("chat-a").messages.map { it.text })
    }
    @Test
    fun submittedSteerIsNotSentAgainByTheNextTurnQueue() {
        val store = PendingMessageQueueStore()
        store.enqueue("chat-a", "correction", true)
        val original = store.states.value.getValue("chat-a").messages.single()
        store.remove("chat-a", original.id)
        store.restore("chat-a", original.copy(isSteering = true))
        assertFalse(store.consumeAutoDequeueSignal("chat-a", false))
        assertNull(store.takeNextAutoDequeue("chat-a"))
    }

    @Test
    fun cancelledSteerReturnsToQueueWithoutAutomaticallyStartingAnotherTurn() {
        val store = PendingMessageQueueStore()
        store.enqueue("chat-a", "correction", true)
        val original = store.states.value.getValue("chat-a").messages.single()
        store.remove("chat-a", original.id)
        store.restore("chat-a", original.copy(isSteering = true))
        store.returnSteer("chat-a", original)
        assertEquals(original, store.states.value.getValue("chat-a").messages.single())
        assertFalse(store.consumeAutoDequeueSignal("chat-a", false))
    }

    @Test
    fun queueRemainsAvailableWhenAnotherChatIsVisited() {
        val store = PendingMessageQueueStore()

        store.enqueue(chatId = "chat-a", text = "send after reply", isQueueBlocked = true)

        assertFalse(store.consumeAutoDequeueSignal(chatId = "chat-b", isQueueBlocked = false))
        assertEquals(listOf("send after reply"), store.states.value.getValue("chat-a").messages.map { it.text })
        assertTrue(store.consumeAutoDequeueSignal(chatId = "chat-a", isQueueBlocked = false))
    }

    @Test
    fun cancellingCurrentTurnConsumesOnlyTheNextAutoDequeue() {
        val store = PendingMessageQueueStore()

        store.enqueue(chatId = "chat-a", text = "first", isQueueBlocked = true)
        store.suppressNextAutoDequeue("chat-a")

        assertFalse(store.consumeAutoDequeueSignal(chatId = "chat-a", isQueueBlocked = false))
        assertFalse(store.consumeAutoDequeueSignal(chatId = "chat-a", isQueueBlocked = false))
    }

    @Test
    fun deletingThenReimportingSameIdRejectsOldRestoreButAllowsNewQueue() {
        val store = PendingMessageQueueStore()
        store.syncExistingChatIds(setOf("chat-a", "chat-b"))
        store.enqueue(chatId = "chat-a", text = "discard", isQueueBlocked = true)
        store.enqueue(chatId = "chat-b", text = "keep", isQueueBlocked = true)
        val inFlight =
            store.remove(
                chatId = "chat-a",
                messageId = store.states.value.getValue("chat-a").messages.single().id,
            )!!

        store.removeChat("chat-a")
        store.restore("chat-a", inFlight)
        store.enqueue(chatId = "chat-a", text = "stale enqueue", isQueueBlocked = false)

        assertFalse("chat-a" in store.states.value)
        assertTrue(store.isChatInactive("chat-a"))
        assertEquals(listOf("keep"), store.states.value.getValue("chat-b").messages.map { it.text })

        store.syncExistingChatIds(setOf("chat-b"))
        store.syncExistingChatIds(setOf("chat-a", "chat-b"))
        store.restore("chat-a", inFlight)
        store.enqueue(chatId = "chat-a", text = "new instance", isQueueBlocked = false)
        val newInstanceItem = store.states.value.getValue("chat-a").messages.single()

        assertFalse(store.isChatInactive("chat-a"))
        assertFalse(store.isCurrentGeneration("chat-a", inFlight.chatGeneration))
        assertTrue(store.isCurrentGeneration("chat-a", newInstanceItem.chatGeneration))
        assertEquals(
            listOf("new instance"),
            store.states.value.getValue("chat-a").messages.map { it.text },
        )
    }

    @Test
    fun absenceBeforeDeletionCallbackStillAllowsSameIdReimport() {
        val store = PendingMessageQueueStore()
        store.syncExistingChatIds(setOf("chat-a"))
        store.syncExistingChatIds(emptySet())

        store.removeChat("chat-a")
        store.syncExistingChatIds(setOf("chat-a"))
        store.enqueue(chatId = "chat-a", text = "restored instance", isQueueBlocked = false)

        assertFalse(store.isChatInactive("chat-a"))
        assertEquals(
            listOf("restored instance"),
            store.states.value.getValue("chat-a").messages.map { it.text },
        )
    }
}
