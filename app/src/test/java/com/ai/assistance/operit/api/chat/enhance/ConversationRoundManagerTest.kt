package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.util.AppLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConversationRoundManagerTest {
    private var previousSystemLogEnabled = true

    @Before
    fun disableAndroidSystemLogForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreAndroidSystemLog() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    @Test
    fun toolResultAppendedBeforeTheNextRoundRemainsInFinalDisplayContent() {
        val manager = ConversationRoundManager()
        val toolCall = "<tool name=\"read_file_part\"><param name=\"path\">/tmp/file</param></tool>"
        val toolResult =
            "<tool_result_26C6 name=\"read_file_part\" status=\"error\"><content>missing path</content></tool_result_26C6>"
        val nextThink = "<think>retry with an absolute path</think>"

        manager.appendChunk(toolCall)
        manager.appendContent(toolResult)
        manager.startNewRound()
        manager.appendChunk(nextThink)

        assertEquals("$toolCall\n$toolResult\n$nextThink", manager.getDisplayContent())
    }
}