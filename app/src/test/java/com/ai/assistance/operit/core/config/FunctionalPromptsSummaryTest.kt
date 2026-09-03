package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.data.model.ConversationSummaryConfig
import com.ai.assistance.operit.data.model.SummarySectionOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalPromptsSummaryTest {
    @Test
    fun buildSummarySystemPrompt_withoutConfigKeepsLegacyPrompt() {
        val prompt =
            FunctionalPrompts.buildSummarySystemPrompt(
                previousSummary = null,
                useEnglish = false,
                summaryConfig = ConversationSummaryConfig()
            )

        assertEquals(FunctionalPrompts.SUMMARY_PROMPT.trimIndent(), prompt)
    }

    @Test
    fun buildSummarySystemPrompt_overridingOneSectionKeepsOtherLegacySections() {
        val prompt =
            FunctionalPrompts.buildSummarySystemPrompt(
                previousSummary = null,
                useEnglish = false,
                summaryConfig =
                    ConversationSummaryConfig(
                        sectionOverrides =
                            listOf(
                                SummarySectionOverride(
                                    id = "core_task",
                                    title = "工程状态",
                                    instruction = "只记录已验证的工程改动。"
                                )
                            )
                    )
            )

        assertTrue(prompt.contains("【工程状态】"))
        assertTrue(prompt.contains("只记录已验证的工程改动。"))
        assertFalse(prompt.contains("【核心任务状态】"))
        assertTrue(prompt.contains("【互动情节与设定】"))
        assertTrue(prompt.contains("如存在虚构或场景设定"))
        assertTrue(prompt.contains("【对话历程与概要】"))
        assertTrue(prompt.contains("【关键信息与上下文】"))
        assertTrue(prompt.contains("**格式要求：**"))
    }

    @Test
    fun buildSummarySystemPrompt_disablingOneSectionRemovesOnlyThatSection() {
        val prompt =
            FunctionalPrompts.buildSummarySystemPrompt(
                previousSummary = null,
                useEnglish = false,
                summaryConfig =
                    ConversationSummaryConfig(
                        sectionOverrides =
                            listOf(SummarySectionOverride(id = "interaction", enabled = false))
                    )
            )

        assertFalse(prompt.contains("【互动情节与设定】"))
        assertTrue(prompt.contains("【核心任务状态】"))
        assertTrue(prompt.contains("【对话历程与概要】"))
        assertTrue(prompt.contains("【关键信息与上下文】"))
    }

    @Test
    fun buildSummarySystemPrompt_globalRulesAreAppended() {
        val prompt =
            FunctionalPrompts.buildSummarySystemPrompt(
                previousSummary = null,
                useEnglish = false,
                summaryConfig = ConversationSummaryConfig(globalRules = "不要记录未验证的计划。")
            )

        assertTrue(prompt.contains("<global_summary_rules>"))
        assertTrue(prompt.contains("不要记录未验证的计划。"))
        assertTrue(prompt.contains("【核心任务状态】"))
    }

    @Test
    fun resolveSummarySections_keepsDefaultsForUntouchedSections() {
        val sections =
            FunctionalPrompts.resolveSummarySections(
                overrides =
                    listOf(
                        SummarySectionOverride(id = "core_task", title = "工程状态"),
                        SummarySectionOverride(id = "interaction", enabled = false)
                    ),
                useEnglish = false
            )

        assertEquals(4, sections.size)
        assertEquals("工程状态", sections.first { it.id == "core_task" }.title)
        assertFalse(sections.first { it.id == "interaction" }.enabled)
        assertEquals("对话历程与概要", sections.first { it.id == "progress" }.title)
    }

    @Test
    fun buildSummarySectionOverrides_persistsOnlyChangedFields() {
        val defaults = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
        val edited =
            defaults.map { section ->
                if (section.id == "core_task") section.copy(title = "工程状态") else section
            }

        assertEquals(
            listOf(SummarySectionOverride(id = "core_task", title = "工程状态")),
            FunctionalPrompts.buildSummarySectionOverrides(edited, useEnglish = false)
        )
    }
}