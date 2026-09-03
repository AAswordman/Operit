package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.data.model.ConversationSummaryConfig
import com.ai.assistance.operit.data.model.SummarySectionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalPromptsSummaryTest {
    @Test
    fun buildSummarySystemPrompt_appliesGlobalRulesAndOnlyEnabledSections() {
        val prompt =
            FunctionalPrompts.buildSummarySystemPrompt(
                previousSummary = "Historical assistant plan",
                useEnglish = true,
                summaryConfig =
                    ConversationSummaryConfig(
                        globalRules = "Do not record unverified plans.",
                        sections =
                            listOf(
                                SummarySectionConfig(
                                    id = "core_task",
                                    title = "Engineering State",
                                    instruction = "Record branch and verification state."
                                ),
                                SummarySectionConfig(
                                    id = "interaction",
                                    enabled = false
                                )
                            )
                    )
            )

        assertTrue(prompt.contains("<global_summary_rules>"))
        assertTrue(prompt.contains("Do not record unverified plans."))
        assertTrue(prompt.contains("[Engineering State]"))
        assertTrue(prompt.contains("Record branch and verification state."))
        assertTrue(prompt.contains("<historical_summary>"))
        assertTrue(prompt.contains("Historical assistant plan"))
        assertFalse(prompt.contains("[Interaction & Scenario]"))
        assertFalse(prompt.contains("You MUST follow the fixed output format below"))
    }

    @Test
    fun resolveSummarySections_keepsStableDefaultSlotsAndCustomValues() {
        val sections =
            FunctionalPrompts.resolveSummarySections(
                configuredSections =
                    listOf(
                        SummarySectionConfig(
                            id = "core_task",
                            title = "Active Work",
                            instruction = "Record only active user work."
                        ),
                        SummarySectionConfig(id = "interaction", enabled = false)
                    ),
                useEnglish = true
            )

        assertEquals(4, sections.size)
        assertEquals("Active Work", sections.first { it.id == "core_task" }.title)
        assertFalse(sections.first { it.id == "interaction" }.enabled)
        assertEquals("Conversation Progress & Overview", sections.first { it.id == "progress" }.title)
    }
}
