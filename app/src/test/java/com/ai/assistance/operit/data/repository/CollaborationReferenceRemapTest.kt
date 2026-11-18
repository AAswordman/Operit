package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CollaborationReferenceRemapTest {
    private val ids = mapOf("old-child" to "new-child")

    @Test fun remapsOnlyTheHostSenderIdAndNotQuotedPayload() {
        val prefix = "Message ID: 12345678-1234-1234-1234-123456789abc\nMessage Type: MESSAGE\nTask name: /root\nSender: /root/a\nSender ID: "
        val body = "\nPayload:\nSender ID: old-child\n"
        val original = prefix + "old-child" + body
        assertEquals(prefix + "new-child" + body, remapCollaborationReferences(original, ids, true, false))
        assertEquals(original, remapCollaborationReferences(original, ids, false, false))
    }

    @Test fun remapsCollaborationResultsAndPreservesOtherToolOutput() {
        fun result(name: String, id: String) =
            """<tool_result name="$name" status="success"><content>{"agent_id":"$id"}</content></tool_result>"""
        val unrelated = result("read_file", "old-child")
        val original = result("followup_task", "old-child") + unrelated
        assertEquals(result("followup_task", "new-child") + unrelated, remapCollaborationReferences(original, ids, false, true))
        assertEquals(original, remapCollaborationReferences(original, ids, false, false))
    }
}
