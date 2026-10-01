package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.ChatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowBreakdownEstimatorTest {

    @Test
    fun splitSystemPromptAndSkillCatalog_extractsAvailablePackages() {
        val systemPrompt =
                """
                You are Operit.
                Available packages:
                - github : GitHub tools
                - memory_skill : Remember facts

                To use a package:
                <tool name="use_package"></tool>
                PACKAGE SYSTEM
                extra
                """.trimIndent()

        val (system, catalog) =
                ContextWindowBreakdownEstimator.splitSystemPromptAndSkillCatalog(systemPrompt)

        assertTrue(system.contains("You are Operit."))
        assertTrue(system.contains("To use a package:"))
        assertTrue(system.contains("PACKAGE SYSTEM"))
        assertTrue(catalog.contains("github"))
        assertTrue(catalog.contains("memory_skill"))
        assertTrue(!catalog.contains("To use a package:"))
    }

    @Test
    fun splitSystemPromptAndSkillCatalog_ignoresEmptyCatalog() {
        val systemPrompt =
                """
                You are Operit.
                No packages are currently available.

                To use a package:
                <tool name="use_package"></tool>
                """.trimIndent()

        val (system, catalog) =
                ContextWindowBreakdownEstimator.splitSystemPromptAndSkillCatalog(systemPrompt)

        assertEquals("", catalog)
        assertTrue(system.contains("You are Operit."))
    }

    @Test
    fun estimateToolTokens_splitsResidentAndPackageTools() {
        val resident = ToolPrompt(name = "sleep", description = "Pause briefly")
        val mcp = ToolPrompt(name = "github:create_issue", description = "Create an issue")

        val (residentTokens, mcpTokens) =
                ContextWindowBreakdownEstimator.estimateToolTokens(listOf(resident, mcp))

        assertEquals(ChatUtils.estimateTokenCount(resident.toString()), residentTokens)
        assertEquals(ChatUtils.estimateTokenCount(mcp.toString()), mcpTokens)
    }

    @Test
    fun fromSnapshot_classifiesTurnsAndComputesRemaining() {
        val system =
                """
                System prompt body
                Available packages:
                - skill_a : Demo skill
                To use a package:
                use_package
                """.trimIndent()
        val snapshot =
                RequestWindowSnapshot(
                        windowTokens = 7000L,
                        history =
                                listOf(
                                        PromptTurn(PromptTurnKind.SYSTEM, system),
                                        PromptTurn(PromptTurnKind.SUMMARY, "Earlier chat was about tokens."),
                                        PromptTurn(PromptTurnKind.USER, "hello world"),
                                        PromptTurn(PromptTurnKind.ASSISTANT, "hi there"),
                                        PromptTurn(PromptTurnKind.TOOL_CALL, "sleep"),
                                        PromptTurn(PromptTurnKind.TOOL_RESULT, "ok")
                                ),
                        tools =
                                listOf(
                                        ToolPrompt(name = "sleep", description = "Pause"),
                                        ToolPrompt(name = "github:comment_issue", description = "Comment")
                                )
                )

        val breakdown =
                ContextWindowBreakdownEstimator.fromSnapshot(
                        snapshot = snapshot,
                        maxWindowTokens = 10_000L,
                        autoSummaryThreshold = 0.8f,
                        enableSummary = true
                )

        assertEquals(7000L, breakdown.windowTokens)
        assertEquals(10_000L, breakdown.maxWindowTokens)
        assertEquals(3000L, breakdown.remainingTokens)
        assertEquals(1000L, breakdown.autoSummaryBufferTokens)
        assertEquals(
                ChatUtils.estimateTokenCount("hello world"),
                breakdown.userMessageTokens
        )
        assertEquals(
                ChatUtils.estimateTokenCount("hi there"),
                breakdown.assistantMessageTokens
        )
        assertEquals(
                ChatUtils.estimateTokenCount("sleep") + ChatUtils.estimateTokenCount("ok"),
                breakdown.toolResultTokens
        )
        assertEquals(
                breakdown.userMessageTokens +
                        breakdown.assistantMessageTokens +
                        breakdown.toolResultTokens,
                breakdown.messageHistoryTokens
        )
        assertEquals(
                ChatUtils.estimateTokenCount("Earlier chat was about tokens."),
                breakdown.summaryTokens
        )
        assertTrue(breakdown.systemPromptTokens > 0L)
        assertTrue(breakdown.skillTokens > 0L)
        assertTrue(breakdown.residentToolTokens > 0L)
        assertTrue(breakdown.mcpToolTokens > 0L)
        val classified =
                breakdown.messageHistoryTokens +
                        breakdown.systemPromptTokens +
                        breakdown.residentToolTokens +
                        breakdown.mcpToolTokens +
                        breakdown.skillTokens +
                        breakdown.summaryTokens
        assertEquals((7000L - classified).coerceAtLeast(0L), breakdown.otherTokens)
    }

    @Test
    fun splitAssistantContent_extractsEmbeddedToolResults() {
        val toolResult = "<tool_result name=\"sleep\" status=\"success\"><content>ok</content></tool_result>"
        val content =
                "Let me look that up.\n" +
                        toolResult +
                        "\nDone."

        val split = ContextWindowBreakdownEstimator.splitAssistantContent(content)

        assertTrue(split.assistant.contains("Let me look that up."))
        assertTrue(split.assistant.contains("Done."))
        assertTrue(!split.assistant.contains("tool_result"))
        assertTrue(split.toolResult.contains("<tool_result"))
        assertTrue(split.toolResult.contains("ok"))
    }

    @Test
    fun estimateMessageHistoryParts_splitsUserAssistantAndToolResults() {
        val embedded = "calling now <tool_result name=\"sleep\" status=\"success\"><content>slept</content></tool_result>"
        val history =
                listOf(
                        PromptTurn(PromptTurnKind.USER, "please run sleep"),
                        PromptTurn(PromptTurnKind.ASSISTANT, embedded),
                        PromptTurn(PromptTurnKind.TOOL_RESULT, "extra result")
                )

        val parts = ContextWindowBreakdownEstimator.estimateMessageHistoryParts(history)
        val assistantSplit =
                ContextWindowBreakdownEstimator.splitAssistantContent(history[1].content)

        assertEquals(ChatUtils.estimateTokenCount("please run sleep"), parts.user)
        assertEquals(ChatUtils.estimateTokenCount(assistantSplit.assistant), parts.assistant)
        assertEquals(
                ChatUtils.estimateTokenCount(assistantSplit.toolResult) +
                        ChatUtils.estimateTokenCount("extra result"),
                parts.toolResult
        )
    }

    @Test
    fun formatTokenCount_usesCompactUnits() {
        assertEquals("12", ContextWindowBreakdownEstimator.formatTokenCount(12L))
        assertEquals("1.5k", ContextWindowBreakdownEstimator.formatTokenCount(1500L))
        assertEquals("12k", ContextWindowBreakdownEstimator.formatTokenCount(12_000L))
        assertEquals("1.2M", ContextWindowBreakdownEstimator.formatTokenCount(1_200_000L))
    }
}
