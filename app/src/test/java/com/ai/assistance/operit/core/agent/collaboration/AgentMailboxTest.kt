package com.ai.assistance.operit.core.agent.collaboration

import org.junit.Assert.*
import org.junit.Test

class AgentMailboxTest {
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
