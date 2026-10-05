package com.ai.assistance.operit.core.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalPromptsWaifuTest {
    @Test
    fun waifuEmotionRuleExplainsXmlRendering() {
        val prompt = FunctionalPrompts.waifuEmotionRule("happy, custom")

        assertTrue(prompt.contains("<emotion>类别</emotion>"))
        assertTrue(prompt.contains("<emotion>happy</emotion>"))
        assertTrue(prompt.contains("最自然、最符合当前语义和语气的位置"))
        assertTrue(prompt.contains("句首、句中或句末"))
        assertFalse(prompt.contains("句末追加"))
        assertTrue(prompt.contains("客户端会把这个标签替换"))
        assertTrue(prompt.contains("不要输出图片 URL"))
    }

    @Test
    fun waifuEmotionRuleSupportsEnglish() {
        val prompt = FunctionalPrompts.waifuEmotionRule("happy, custom", useEnglish = true)

        assertTrue(prompt.contains("<emotion>category</emotion>"))
        assertTrue(prompt.contains("most natural position"))
        assertTrue(prompt.contains("beginning, middle, or end"))
        assertFalse(prompt.contains("Append exactly one XML tag at the end"))
        assertTrue(prompt.contains("The app replaces this tag"))
        assertTrue(prompt.contains("Do not output image URLs"))
    }
}
