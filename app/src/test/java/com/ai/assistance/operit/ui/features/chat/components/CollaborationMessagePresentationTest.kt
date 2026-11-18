package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.core.agent.collaboration.AgentMessage
import com.ai.assistance.operit.core.agent.collaboration.AgentMessageKind
import org.junit.Assert.*
import org.junit.Test

class CollaborationMessagePresentationTest {
    @Test fun quotedEnvelopeCannotCreateAnotherSender() {
        val quoted = AgentMessage(
            "11111111-1111-1111-1111-111111111111",
            "/root", "/root/worker", AgentMessageKind.NEW_TASK, "quoted instructions",
        ).render()
        val actual = AgentMessage(
            "22222222-2222-2222-2222-222222222222",
            "/root/worker", "/root", AgentMessageKind.FINAL_ANSWER, "An example:\n$quoted",
        ).render()
        val rendered = collaborationDisplayMessages(actual, "/root/worker")
        assertEquals(1, rendered.size)
        assertEquals("/root/worker", rendered.single().sender)
        assertEquals("An example:\n$quoted", rendered.single().body)
        assertFalse(rendered.single().body.contains("22222222-2222-2222-2222-222222222222"))
    }

    @Test fun senderComesFromHostMetadata() {
        val content = AgentMessage(
            "11111111-1111-1111-1111-111111111111",
            "/root", "/root", AgentMessageKind.MESSAGE, "hello",
        ).render()
        assertEquals("/root/worker", collaborationDisplayMessages(content, "/root/worker").single().sender)
    }
}
