package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.data.model.ConversationSummaryConfig
import com.ai.assistance.operit.data.model.SummarySectionOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalPromptsSummaryTest {
    @Test
    fun buildSummarySystemPrompt_withoutOverridesKeepsLegacyPrompt() {
        val prompt = FunctionalPrompts.buildSummarySystemPrompt(
            previousSummary = null,
            useEnglish = false
        )

        assertEquals(FunctionalPrompts.SUMMARY_PROMPT.trimIndent(), prompt)
    }

    @Test
    fun buildSummarySystemPrompt_disablingMiddleSectionDoesNotRemoveLaterSections() {
        val prompt = FunctionalPrompts.buildSummarySystemPrompt(
            previousSummary = null,
            useEnglish = false,
            summaryConfig = ConversationSummaryConfig(
                sectionOverrides = listOf(
                    SummarySectionOverride(
                        id = "core_task",
                        title = "工程状态",
                        instruction = "仅记录已验证的工程变更。"
                    ),
                    SummarySectionOverride(id = "interaction", enabled = false)
                )
            )
        )

        assertTrue(prompt.contains("【工程状态】"))
        assertTrue(prompt.contains("仅记录已验证的工程变更。"))
        assertFalse(prompt.contains("【互动情节与设定】"))
        assertTrue(prompt.contains("【对话历程与概要】"))
        assertTrue(prompt.contains("【关键信息与上下文】"))
    }

    @Test
    fun buildSummarySystemPrompt_unknownOverrideKeepsLegacyTemplate() {
        val prompt = FunctionalPrompts.buildSummarySystemPrompt(
            previousSummary = null,
            useEnglish = true,
            summaryConfig = ConversationSummaryConfig(
                sectionOverrides = listOf(SummarySectionOverride(id = "unknown"))
            )
        )

        assertEquals(FunctionalPrompts.SUMMARY_PROMPT_EN.trimIndent(), prompt)
    }

    @Test
    fun buildSummarySectionOverrides_onlyPersistsChangedFields() {
        val sections = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
            .map { section ->
                if (section.id == "core_task") section.copy(title = "工程状态") else section
            }

        assertEquals(
            listOf(SummarySectionOverride(id = "core_task", title = "工程状态")),
            FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)
        )
    }
}
