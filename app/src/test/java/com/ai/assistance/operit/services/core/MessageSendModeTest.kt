package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.appendUserTurnIfMissing
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSendModeTest {
    private val continuations = listOf(MessageSendMode.CONTINUE, MessageSendMode.AUTO_CONTINUE)

    @Test
    fun continuationsDoNotReadComposerEvenInForeground() {
        continuations.forEach { mode ->
            assertFalse(mode.shouldReadComposer(isBackgroundSend = false))
            assertFalse(mode.shouldReadComposer(isBackgroundSend = true))
        }
        assertTrue(MessageSendMode.USER.shouldReadComposer(isBackgroundSend = false))
        assertFalse(MessageSendMode.USER.shouldReadComposer(isBackgroundSend = true))
    }

    @Test
    fun onlyAutomaticContinuationReusesPreviousTurnConfiguration() {
        assertFalse(MessageSendMode.USER.usesPreviousTurnConfiguration)
        assertFalse(MessageSendMode.CONTINUE.usesPreviousTurnConfiguration)
        assertTrue(MessageSendMode.AUTO_CONTINUE.usesPreviousTurnConfiguration)
    }

    @Test
    fun manualContinuationSkipsInputHooksAndPrebuiltUserContent() = runTest {
        // The manual entry point must never turn an unsent draft, an attachment or a hook
        // generated block into model input: doing so would forge a user message.
        listOf(null, "unsent text and workspace attachment").forEach { prebuilt ->
            val content = MessageSendMode.CONTINUE.buildMessageContent(prebuilt) {
                error("Manual continuation must not run user input hooks or consume workspace changes")
            }
            assertEquals("", content)
        }
    }

    @Test
    fun automaticContinuationKeepsBuildingItsOwnContent() = runTest {
        // Auto continuation is driven by the summary continuation instruction, so its content
        // building stays untouched; changing it here would regress the existing behaviour.
        // Only the manual entry point is allowed to request a turn without new user input.
        var builds = 0
        val built = MessageSendMode.AUTO_CONTINUE.buildMessageContent(null) {
            builds++
            "summary continuation instruction"
        }
        assertEquals("summary continuation instruction", built)
        assertEquals(1, builds)
        assertEquals(
            "group input",
            MessageSendMode.AUTO_CONTINUE.buildMessageContent("group input") {
                error("Prebuilt content must be reused as is")
            },
        )
    }

    @Test
    fun ordinarySendBuildsContentOnceAndReusesPrebuiltGroupContent() = runTest {
        var calls = 0
        val content = MessageSendMode.USER.buildMessageContent(null) {
            calls++
            "draft with attachment and reply"
        }
        assertEquals("draft with attachment and reply", content)
        assertEquals(1, calls)
        assertEquals(
            "group input",
            MessageSendMode.USER.buildMessageContent("group input") {
                error("Group input must not run input hooks twice")
            },
        )
    }

    @Test
    fun continuationsNeverPersistUserMessages() {
        continuations.forEach { mode ->
            listOf(false, true).forEach { hasContent ->
                listOf(false, true).forEach { groupTurn ->
                    assertFalse(
                        mode.shouldAddUserMessage(
                            persistTurn = true,
                            suppressUserMessageInHistory = false,
                            isGroupOrchestrationTurn = groupTurn,
                            hasContent = hasContent,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun ordinarySendPreservesPersistenceAndGroupRules() {
        fun shouldPersist(persist: Boolean, suppress: Boolean, group: Boolean, hasContent: Boolean) =
            MessageSendMode.USER.shouldAddUserMessage(persist, suppress, group, hasContent)

        assertTrue(shouldPersist(true, false, false, true))
        assertTrue(shouldPersist(true, false, true, true))
        assertFalse(shouldPersist(true, false, true, false))
        assertFalse(shouldPersist(false, false, false, true))
        assertFalse(shouldPersist(true, true, false, true))
    }

    @Test
    fun manualContinuationKeepsAssistantAndToolResultHistoryWithoutAddingUserTurn() = runTest {
        val history = listOf(
            PromptTurn(PromptTurnKind.USER, "Read the file"),
            PromptTurn(PromptTurnKind.ASSISTANT, "Reading the file"),
            PromptTurn(PromptTurnKind.TOOL_RESULT, "file contents", toolName = "read_file"),
        )
        val content = MessageSendMode.CONTINUE.buildMessageContent(null) {
            error("Unexpected user input")
        }
        assertEquals("", content)
        assertSame(history, history.appendUserTurnIfMissing(content))
        val assistantHistory = history.dropLast(1)
        assertSame(assistantHistory, assistantHistory.appendUserTurnIfMissing(content))
        assertEquals(1, history.appendUserTurnIfMissing(content).count { it.kind == PromptTurnKind.USER })
    }
}
