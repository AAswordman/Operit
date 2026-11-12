package com.ai.assistance.operit.ui.features.chat.components.part

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolResultSemanticPreviewTest {
    @Test fun preservesNormalizedPreviewWithoutProcessingWholeResult() {
        val samples = listOf(
            "", " \n\t ", " short\n\t result ", "x".repeat(20),
            "x".repeat(20) + " \n\t", "x".repeat(21),
            "\u2003中文\t结果\u2003", "a\u2003\u2003b",
            "x".repeat(20) + "\u2003".repeat(1000),
            "x".repeat(20) + "\u2003".repeat(1000) + "z",
            "file result: " + "abcdef\n".repeat(10000),
        )
        samples.forEach { input ->
            val normalized = input.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
            val expected = if (normalized.length <= 20) normalized else normalized.take(20) + "..."
            assertEquals(expected, toolResultSemanticPreview(input))
        }
    }
}
