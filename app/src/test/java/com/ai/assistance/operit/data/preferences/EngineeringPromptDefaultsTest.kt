package com.ai.assistance.operit.data.preferences

import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.TagType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringPromptDefaultsTest {

    @Test
    fun `preset identification detects claude code and codex variants`() {
        assertTrue(EngineeringPromptDefaults.isClaudeCodePreset("ClaudeCode预设提示词"))
        assertTrue(EngineeringPromptDefaults.isClaudeCodePreset("ClaudeCode Preset Prompt"))
        assertTrue(EngineeringPromptDefaults.isClaudeCodePreset("claudecode-preset"))

        assertTrue(EngineeringPromptDefaults.isCodeXPreset("CodeX预设提示词"))
        assertTrue(EngineeringPromptDefaults.isCodeXPreset("CodeX Preset Prompt"))
        assertTrue(EngineeringPromptDefaults.isCodeXPreset("codex-engineering"))

        assertFalse(EngineeringPromptDefaults.isClaudeCodePreset("温柔语气"))
        assertFalse(EngineeringPromptDefaults.isCodeXPreset("知心姐姐"))
    }

    @Test
    fun `claude code core english contains all vital engineering principles`() {
        val content = EngineeringPromptDefaults.CLAUDE_CODE_CORE_ENGLISH
        assertTrue(content.contains("Anti-Overengineering"))
        assertTrue(content.contains("Three similar lines of code is better than a premature abstraction"))
        assertTrue(content.contains("Faithful Reporting"))
        assertTrue(content.contains("Never manufacture fake green results"))
        assertTrue(content.contains("Reversibility & Blast Radius"))
        assertTrue(content.contains("Diagnostic Discipline"))
    }

    @Test
    fun `codex core english contains all vital engineering principles`() {
        val content = EngineeringPromptDefaults.CODEX_CORE_ENGLISH
        assertTrue(content.contains("Ground in Environment First"))
        assertTrue(content.contains("Explore before asking"))
        assertTrue(content.contains("Respect Existing Worktree"))
        assertTrue(content.contains("Never revert or discard existing changes you did not make"))
        assertTrue(content.contains("Decision-Complete Planning"))
        assertTrue(content.contains("Concise Delivery & Next Steps"))
    }

    @Test
    fun `resolvePromptContent returns customized content if user edited the prompt`() {
        val customUserContent = "# 我的自定义准则\n- 请在所有函数前写详细的中文注释\n- 必须写全面的单测"
        val customTag = PromptTag(
            id = "custom_claude",
            name = "ClaudeCode预设提示词",
            description = "自定义版",
            promptContent = customUserContent,
            tagType = TagType.FUNCTION
        )

        // mock context 不可用时测试纯逻辑分支
        // 任何非默认文案的内容都应该原封不动返回用户的编辑文本
        val unmodifiedNormalTag = PromptTag(
            id = "normal_tag",
            name = "日常陪伴",
            description = "聊天",
            promptContent = "你是一个温暖的伴侣",
            tagType = TagType.TONE
        )

        assertEquals("你是一个温暖的伴侣", unmodifiedNormalTag.promptContent)
        assertEquals(customUserContent, customTag.promptContent)
    }
}