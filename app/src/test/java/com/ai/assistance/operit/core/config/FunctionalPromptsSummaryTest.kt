package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.data.model.ConversationSummaryConfig
import com.ai.assistance.operit.data.model.SummarySectionOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun buildSummarySectionOverrides_persistsEnabledForEverySection() {
        val sections = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)

        val overrides = FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)

        // 全量持久化后每个分段都有条目，enabled 恒写入，未改动的 title 与 instruction 保持 null，
        // 渲染侧据此直接拼接模板原文，默认提示词逐字不变。
        assertEquals(
            listOf("core_task", "interaction", "progress", "key_info"),
            overrides.map { it.id }
        )
        assertTrue(overrides.all { it.enabled == true })
        assertTrue(overrides.all { it.title == null })
        assertTrue(overrides.all { it.instruction == null })
    }

    @Test
    fun buildSummarySectionOverrides_disabledSectionSurvivesLaterEdits() {
        val sections = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
            .map { section ->
                when (section.id) {
                    "interaction" -> section.copy(enabled = false)
                    // 关闭互动段之后又改了别的分段：旧实现会把互动段整体丢弃，导致它重新出现在提示词里
                    "progress" -> section.copy(title = "演进记录")
                    else -> section
                }
            }

        val overrides = FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)
        val interaction = overrides.first { it.id == "interaction" }
        val progress = overrides.first { it.id == "progress" }

        assertEquals(false, interaction.enabled)
        assertEquals("演进记录", progress.title)

        val prompt = FunctionalPrompts.buildSummarySystemPrompt(
            previousSummary = null,
            useEnglish = false,
            summaryConfig = ConversationSummaryConfig(sectionOverrides = overrides)
        )
        assertFalse(prompt.contains("【互动情节与设定】"))
        assertTrue(prompt.contains("【演进记录】"))
    }

    @Test
    fun buildSummarySectionOverrides_onlyPersistsChangedFields() {
        val sections = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
            .map { section ->
                if (section.id == "core_task") section.copy(title = "工程状态") else section
            }

        val overrides = FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)

        // title 与默认一致的写 null，与默认不同的写规范化文本；四个分段都不会被整体丢弃。
        assertEquals(
            listOf(
                SummarySectionOverride(id = "core_task", enabled = true, title = "工程状态"),
                SummarySectionOverride(id = "interaction", enabled = true),
                SummarySectionOverride(id = "progress", enabled = true),
                SummarySectionOverride(id = "key_info", enabled = true)
            ),
            overrides
        )
    }

    @Test
    fun buildSummarySectionOverrides_keepsUserClearedInstruction() {
        val sections = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
            .map { section ->
                if (section.id == "key_info") section.copy(instruction = "") else section
            }

        val overrides = FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)
        val keyInfo = overrides.first { it.id == "key_info" }

        // 用户清空指令是明确意图：空串必须原样持久化，不能被当成"与默认相同"丢弃。
        assertEquals("", keyInfo.instruction)

        val prompt = FunctionalPrompts.buildSummarySystemPrompt(
            previousSummary = null,
            useEnglish = false,
            summaryConfig = ConversationSummaryConfig(sectionOverrides = overrides)
        )
        assertTrue(prompt.contains("【关键信息与上下文】"))
        // 关键信息段的默认指令以信息点列表开头，被清空后标题与分隔线之间不应残留任何指令正文
        val keyInfoSectionStart = prompt.indexOf("【关键信息与上下文】")
        val separatorIndex = prompt.indexOf("============================", keyInfoSectionStart)
        val sectionBody = prompt.substring(keyInfoSectionStart, separatorIndex)
        assertFalse(sectionBody.contains("信息点"))
    }

    @Test
    fun buildSummarySectionOverrides_whitespaceOnlyEditNormalizesToDefault() {
        val defaults = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
        val coreTaskDefault = defaults.first { it.id == "core_task" }
        val sections = defaults.map { section ->
            if (section.id == "core_task") {
                section.copy(
                    title = "  ${coreTaskDefault.title}  ",
                    instruction = "  ${coreTaskDefault.instruction}  "
                )
            } else {
                section
            }
        }

        val overrides = FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)
        val coreTask = overrides.first { it.id == "core_task" }

        // 只有空白差异的编辑规范化后与默认一致，写 null，渲染保持模板原文。
        assertNull(coreTask.title)
        assertNull(coreTask.instruction)
    }

    @Test
    fun buildSummarySystemPrompt_fullDefaultOverridesRenderTemplateVerbatim() {
        val sections = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
        val overrides = FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)

        val prompt = FunctionalPrompts.buildSummarySystemPrompt(
            previousSummary = null,
            useEnglish = false,
            summaryConfig = ConversationSummaryConfig(sectionOverrides = overrides)
        )

        // 用户没改任何内容、仅落盘一次全量覆盖后，提示词必须与默认模板逐字一致。
        assertEquals(FunctionalPrompts.SUMMARY_PROMPT.trimIndent(), prompt)
    }

    @Test
    fun buildSummarySystemPrompt_disablingAllSectionsRemovesEverySection() {
        val sections = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
            .map { it.copy(enabled = false) }
        val overrides = FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)

        val prompt = FunctionalPrompts.buildSummarySystemPrompt(
            previousSummary = null,
            useEnglish = false,
            summaryConfig = ConversationSummaryConfig(sectionOverrides = overrides)
        )

        assertFalse(prompt.contains("【核心任务状态】"))
        assertFalse(prompt.contains("【互动情节与设定】"))
        assertFalse(prompt.contains("【对话历程与概要】"))
        assertFalse(prompt.contains("【关键信息与上下文】"))
        // 全关时不能保留模板原文的格式要求清单：它要求"必须使用上述固定格式"，
        // 与上方已无任何分段自相矛盾，会把模型逼成自由发挥的默认多段格式。
        assertFalse(prompt.contains("必须使用上述固定格式，包括分隔线"))
        assertFalse(prompt.contains("结尾使用等号分隔线"))
        // 换成明确可执行的自由格式指令，开头说明与标题标记仍保留
        assertTrue(prompt.contains("==========对话摘要=========="))
        assertTrue(prompt.contains("改用自由格式输出"))
    }

    @Test
    fun buildSummarySystemPrompt_allSectionsDisabledUsesFreeFormInstructionWithRules() {
        val sections = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
            .map { it.copy(enabled = false) }
        val overrides = FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)

        val prompt = FunctionalPrompts.buildSummarySystemPrompt(
            previousSummary = null,
            useEnglish = false,
            summaryConfig = ConversationSummaryConfig(
                globalRules = "只总结工程结论，忽略寒暄。",
                sectionOverrides = overrides
            )
        )

        // 规则必须带指令头出现，而不是裸拼在末尾被当成待总结内容
        assertTrue(prompt.contains("额外规则（必须严格遵守"))
        assertTrue(prompt.trimEnd().endsWith("只总结工程结论，忽略寒暄。"))
        assertFalse(prompt.contains("【核心任务状态】"))
        assertFalse(prompt.contains("必须使用上述固定格式，包括分隔线"))
    }

    @Test
    fun buildSummarySystemPrompt_partiallyDisabledSectionsForbidRestoringUnlisted() {
        val sections = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
            .map { section ->
                if (section.id == "interaction") section.copy(enabled = false) else section
            }
        val overrides = FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)

        val prompt = FunctionalPrompts.buildSummarySystemPrompt(
            previousSummary = null,
            useEnglish = false,
            summaryConfig = ConversationSummaryConfig(sectionOverrides = overrides)
        )

        assertFalse(prompt.contains("【互动情节与设定】"))
        // 模板原文的格式要求写死了全部分段，必须显式禁止模型把关掉的分段补回
        assertTrue(prompt.contains("分段锁定"))
        assertTrue(prompt.contains("严禁新增、改写或补回任何未列出的分段标题"))
    }

    @Test
    fun buildSummarySystemPrompt_fullDefaultOverridesAddsNoLockHint() {
        val sections = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
        val overrides = FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)

        val prompt = FunctionalPrompts.buildSummarySystemPrompt(
            previousSummary = null,
            useEnglish = false,
            summaryConfig = ConversationSummaryConfig(sectionOverrides = overrides)
        )

        // 没有任何分段被关闭时不追加锁定约束，未编辑用户首次落盘仍与默认模板逐字一致
        assertFalse(prompt.contains("分段锁定"))
    }

    @Test
    fun resolveSummarySections_roundTripPreservesUserEdits() {
        val edited = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
            .map { section ->
                when (section.id) {
                    "core_task" -> section.copy(enabled = false)
                    "interaction" -> section.copy(title = "剧情推进", instruction = "记录人物关系变化。")
                    "progress" -> section.copy(instruction = "")
                    else -> section
                }
            }

        val once = FunctionalPrompts.buildSummarySectionOverrides(edited, useEnglish = false)
        val resolved = FunctionalPrompts.resolveSummarySections(once, useEnglish = false)
        val twice = FunctionalPrompts.buildSummarySectionOverrides(resolved, useEnglish = false)

        // 序列化-解析-再序列化必须幂等，避免每次保存都把用户编辑改写一遍。
        assertEquals(once, twice)

        val byId = resolved.associateBy { it.id }
        assertFalse(byId.getValue("core_task").enabled)
        assertEquals("剧情推进", byId.getValue("interaction").title)
        assertEquals("记录人物关系变化。", byId.getValue("interaction").instruction)
        assertEquals("", byId.getValue("progress").instruction)
    }

    @Test
    fun resolveSummarySections_emptyInstructionStaysEmptyInEditor() {
        val overrides = listOf(
            SummarySectionOverride(id = "key_info", enabled = true, instruction = "")
        )

        val resolved = FunctionalPrompts.resolveSummarySections(overrides, useEnglish = false)
        val keyInfo = resolved.first { it.id == "key_info" }

        // 界面回显也要尊重空指令：不能把默认指令又填回编辑器，否则用户以为清空无效。
        assertEquals("", keyInfo.instruction)
    }

    @Test
    fun buildSummarySystemPrompt_appliesGlobalRulesAfterOverrides() {
        val sections = FunctionalPrompts.resolveSummarySections(emptyList(), useEnglish = false)
            .map { section ->
                if (section.id == "interaction") section.copy(enabled = false) else section
            }
        val overrides = FunctionalPrompts.buildSummarySectionOverrides(sections, useEnglish = false)

        val prompt = FunctionalPrompts.buildSummarySystemPrompt(
            previousSummary = null,
            useEnglish = false,
            summaryConfig = ConversationSummaryConfig(
                globalRules = "只保留最近三次工具调用的参数。",
                sectionOverrides = overrides
            )
        )

        assertFalse(prompt.contains("【互动情节与设定】"))
        assertTrue(prompt.contains("额外规则（必须严格遵守"))
        assertTrue(prompt.trimEnd().endsWith("只保留最近三次工具调用的参数。"))
    }
}
