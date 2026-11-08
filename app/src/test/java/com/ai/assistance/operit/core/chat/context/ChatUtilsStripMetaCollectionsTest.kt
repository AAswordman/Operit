package com.ai.assistance.operit.core.chat.context

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUtilsStripMetaCollectionsTest {

    @Test fun stripMeta_pairs_preserveRoles() {
        val pairs = listOf("user" to "a", "assistant" to "<meta provider=\"gemini:thought_signature\">x</meta>b")
        val result = ChatUtils.stripGeminiThoughtSignatureMeta(pairs)
        assertEquals(listOf("user" to "a", "assistant" to "b"), result)
    }

    @Test fun stripMeta_turns_preserveKinds() {
        val turns = listOf(PromptTurn(PromptTurnKind.SYSTEM, "a"), PromptTurn(PromptTurnKind.ASSISTANT, "<meta provider=\"gemini:thought_signature\">x</meta>b"))
        val result = ChatUtils.stripGeminiThoughtSignatureMetaTurns(turns)
        assertEquals(PromptTurnKind.SYSTEM, result[0].kind)
        assertEquals(PromptTurnKind.ASSISTANT, result[1].kind)
    }
}
