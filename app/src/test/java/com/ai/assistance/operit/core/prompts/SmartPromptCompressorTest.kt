package com.ai.assistance.operit.core.prompts

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SmartPromptCompressor.
 */
class SmartPromptCompressorTest {

    private lateinit var compressor: SmartPromptCompressor

    @Before
    fun setUp() {
        compressor = SmartPromptCompressor(enabled = true)
    }

    @Test
    fun `compress removes redundant phrases`() {
        val input = "Please note that the system is important to remember."
        val result = compressor.compress(input)
        assertFalse(result.contains("Please note that"))
        assertFalse(result.contains("important to"))
    }

    @Test
    fun `compress collapses excessive blank lines`() {
        val input = "Line 1\n\n\n\n\nLine 2"
        val result = compressor.compress(input)
        assertEquals("Line 1\n\nLine 2", result)
    }

    @Test
    fun `compress detects repeated lines`() {
        val input = "Important instruction\nImportant instruction\nImportant instruction\nOther line"
        val result = compressor.compress(input)
        assertTrue(result.contains("repeated 3 times"))
    }

    @Test
    fun `compress summarizes long log blocks`() {
        val logs = (1..10).joinToString("\n") { "2026-08-30T10:00:0$it INFO Processing step $it" }
        val input = "Before logs\n$logs\nAfter logs"
        val result = compressor.compress(input)
        assertTrue(result.contains("log entries"))
        assertTrue(result.contains("Before logs"))
        assertTrue(result.contains("After logs"))
    }

    @Test
    fun `compress preserves short content`() {
        val input = "Hello world"
        val result = compressor.compress(input)
        assertEquals("Hello world", result)
    }

    @Test
    fun `disabled compressor returns original`() {
        val disabledCompressor = SmartPromptCompressor(enabled = false)
        val input = "Please note that this is important."
        val result = disabledCompressor.compress(input)
        assertEquals(input, result)
    }

    @Test
    fun `estimateTokens returns reasonable count`() {
        val text = "Hello world, this is a test prompt with some tokens."
        val tokens = compressor.estimateTokens(text)
        // ~56 chars / 4 = ~14 tokens
        assertTrue(tokens in 10..20)
    }

    @Test
    fun `isEnabled returns correct value`() {
        assertTrue(compressor.isEnabled())
        assertFalse(SmartPromptCompressor(enabled = false).isEnabled())
    }
}
