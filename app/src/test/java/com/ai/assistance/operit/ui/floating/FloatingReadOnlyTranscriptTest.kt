package com.ai.assistance.operit.ui.floating

import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingReadOnlyTranscriptTest {
    @Test fun onlySubagentChatsAreReadOnlyTranscripts() {
        val normal = ChatHistory(title = "写作", messages = emptyList())
        val subagent = normal.copy(chatKind = ChatKind.SUBAGENT.name, parentChatId = normal.id)
        val branch = normal.copy(chatKind = ChatKind.BRANCH.name)
        assertFalse(normal.isReadOnlyTranscript())
        assertTrue(subagent.isReadOnlyTranscript())
        assertFalse(branch.isReadOnlyTranscript())
        assertFalse((null as ChatHistory?).isReadOnlyTranscript())
    }
}
