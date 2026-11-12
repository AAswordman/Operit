package com.ai.assistance.operit.core.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentToolPolicyTest {
    @Test
    fun rejectsDirectAndIndirectNestedAiTurnTools() {
        assertTrue(SubagentToolPolicy.isForbidden("task"))
        assertTrue(SubagentToolPolicy.isForbidden("create_new_chat"))
        assertTrue(SubagentToolPolicy.isForbidden("send_message_to_ai"))
        assertTrue(SubagentToolPolicy.isForbidden("send_message_to_ai_streaming"))
    }

    @Test
    fun permitsOrdinaryTools() {
        assertFalse(SubagentToolPolicy.isForbidden("read_file"))
        assertFalse(SubagentToolPolicy.isForbidden("search_files"))
    }
}
