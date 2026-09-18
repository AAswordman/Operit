package com.ai.assistance.operit.core.chat.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PromptTurnAppendTest {

    @Test fun appendUserTurnIfMissing_doesNothingForBlankMessage() {
        val turns = listOf(PromptTurn(PromptTurnKind.USER, "hello"))
        assertEquals(turns, turns.appendUserTurnIfMissing(""))
    }

    @Test fun appendUserTurnIfMissing_doesNothingForDuplicateTrailingUserMessage() {
        val turns = listOf(PromptTurn(PromptTurnKind.USER, "hello"))
        assertEquals(turns, turns.appendUserTurnIfMissing("hello"))
    }

    @Test fun appendUserTurnIfMissing_appendsAfterAssistant() {
        val turns = listOf(PromptTurn(PromptTurnKind.ASSISTANT, "hello"))
        assertEquals(2, turns.appendUserTurnIfMissing("next").size)
    }

    @Test fun finalizedContinuationDoesNotAppendHookGeneratedUserInput() {
        listOf(PromptTurnKind.ASSISTANT, PromptTurnKind.TOOL_RESULT, PromptTurnKind.USER).forEach { kind ->
            val turns = listOf(PromptTurn(kind, "existing context"))
            assertSame(turns, applyFinalizedCurrentUserTurn(turns, "", "plugin injected text", historyOnly = true))
            assertSame(turns, applyFinalizedCurrentUserTurn(turns, "", ""))
        }
    }

    @Test fun finalizedOrdinarySendUpdatesExistingUserTurn() {
        val turns = listOf(PromptTurn(PromptTurnKind.USER, "draft"))
        assertEquals(
            listOf(PromptTurn(PromptTurnKind.USER, "processed draft")),
            applyFinalizedCurrentUserTurn(turns, "draft", "processed draft"),
        )
    }

    @Test fun finalizedOrdinarySendAppendsAfterAssistantWithoutDuplicating() {
        val turns = listOf(PromptTurn(PromptTurnKind.ASSISTANT, "reply"))
        val result = applyFinalizedCurrentUserTurn(turns, "draft", "processed draft")
        assertEquals(turns + PromptTurn(PromptTurnKind.USER, "processed draft"), result)
        assertSame(result, applyFinalizedCurrentUserTurn(result, "draft", "processed draft"))
        assertSame(turns, applyFinalizedCurrentUserTurn(turns, "draft", ""))
    }
}
