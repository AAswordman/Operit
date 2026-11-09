package com.ai.assistance.operit.core.agent.collaboration

import org.junit.Assert.*
import org.junit.Test

class AgentMailboxTest {
    @Test fun archivedIdentityIsRetainedButItsNameCanBeReused() {
        val root = CollaborationAgent("main", "/root", "main")
        val old = CollaborationAgent("main", "/root/work", "old",
            systemPrompt = "large prompt", finalAnswer = "old result")
        val archived = CollaborationState(agents = listOf(root, old)).archive(setOf("old"))
        assertNull(archived.find("main", "/root/work"))
        assertEquals("", archived.agents.last().systemPrompt)
        assertNull(archived.agents.last().finalAnswer)
        val fresh = old.copy(chatId = "new", systemPrompt = "fresh", finalAnswer = null)
        val reused = archived.update(fresh)
        assertEquals("new", reused.find("main", "/root/work")!!.chatId)
        assertTrue(reused.agents.any { it.chatId == "old" && it.archived })
        assertThrows(IllegalArgumentException::class.java) {
            reused.update(reused.agents.first { it.chatId == "old" })
        }
    }

    @Test fun deletingArchivedNamesakeDoesNotInterruptTheNewGeneration() {
        val root = CollaborationAgent("main", "/root", "main")
        val old = CollaborationAgent("main", "/root/work", "old", archived = true)
        val fresh = old.copy(chatId = "new", archived = false, status = CollaborationStatus.RUNNING)
        val child = CollaborationAgent("main", "/root/work/child", "child", status = CollaborationStatus.RUNNING)
        val state = CollaborationState(agents = listOf(root, old, fresh, child))
        assertEquals(setOf("old"), state.chatsAffectedByDeletion(setOf("old")))
        assertEquals(setOf("new", "child"), state.chatsAffectedByDeletion(setOf("new")))
        assertEquals(setOf("main", "old", "new", "child"), state.chatsAffectedByDeletion(setOf("main")))
    }

    @Test fun multilinePayloadDoesNotIndentProtocolHeaders() {
        val message = AgentMessage("message-id", "/root", "/root/worker", AgentMessageKind.MESSAGE, "one\ntwo")
        assertTrue(message.render().startsWith("Message ID: message-id\nMessage Type: MESSAGE\n"))
        assertTrue(message.render().endsWith("Payload:\none\ntwo"))
    }
    private val initial = CollaborationState(agents = listOf(
        CollaborationAgent("chat-a", "/root", "chat-a"),
        CollaborationAgent("chat-a", "/root/worker", "child-a"),
        CollaborationAgent("chat-b", "/root", "chat-b"),
        CollaborationAgent("chat-b", "/root/worker", "child-b"),
    ))

    @Test fun queueOnlyDeliveryPreservesIdleStateAndIsolatesRoots() {
        val message = AgentMessage("1", "/root", "/root/worker", AgentMessageKind.MESSAGE, "hello")
        val queued = initial.enqueue("chat-a", message)
        assertEquals(listOf(message), queued.find("chat-a", "/root/worker")!!.messages)
        assertEquals(CollaborationStatus.IDLE, queued.find("chat-a", "/root/worker")!!.status)
        assertTrue(queued.find("chat-b", "/root/worker")!!.messages.isEmpty())
    }

    @Test fun acknowledgementDoesNotConsumeLaterMessages() {
        val first = AgentMessage("1", "/root", "/root/worker", AgentMessageKind.MESSAGE, "first")
        val second = first.copy(id = "2", text = "second")
        val queued = initial.enqueue("chat-a", first).enqueue("chat-a", second)
        val acknowledged = queued.acknowledge("chat-a", "/root/worker", setOf("1"))
        assertEquals(listOf(second), acknowledged.find("chat-a", "/root/worker")!!.messages)
    }

    @Test fun followupCannotWakeRootButOrdinaryMessageCanTargetIt() {
        val message = AgentMessage("1", "/root/worker", "/root", AgentMessageKind.NEW_TASK, "work")
        assertThrows(IllegalArgumentException::class.java) { initial.enqueue("chat-a", message) }
        val queued = initial.enqueue("chat-a", message.copy(kind = AgentMessageKind.MESSAGE))
        assertEquals(1, queued.find("chat-a", "/root")!!.messages.size)
    }
}
