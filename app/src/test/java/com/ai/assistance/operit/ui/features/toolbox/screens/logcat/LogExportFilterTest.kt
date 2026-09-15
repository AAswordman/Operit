package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogExportFilterTest {
    private val sample = """
        2026-09-07 16:51:00.001 D/AIService: send start
        2026-09-07 16:51:00.002 I/ToolPkg: package loaded
        2026-09-07 16:51:00.003 D/WebView: cookie dump
        2026-09-07 16:51:00.004 W/ChatViewModel: slow path
        2026-09-07 16:51:00.005 E/GeminiProvider: request failed
        java.lang.IllegalStateException: boom
            at GeminiProvider.send(GeminiProvider.kt:1)
        2026-09-07 16:51:00.006 I/StreamFramework: done
    """.trimIndent()

    @Test
    fun identityKeepsEverything() {
        val result = LogExportFilter.filterText(sample, LogExportOptions())
        assertEquals(6, result.exportedRecordCount)
        assertTrue(result.lines.any { it.contains("D/AIService") })
        assertTrue(result.lines.any { it.contains("java.lang.IllegalStateException") })
    }

    @Test
    fun excludeDebugDropsVerboseAndDebug() {
        val result = LogExportFilter.filterText(
            sample,
            LogExportOptions(excludeDebug = true)
        )
        assertFalse(result.lines.any { it.contains("D/AIService") })
        assertFalse(result.lines.any { it.contains("D/WebView") })
        assertTrue(result.lines.any { it.contains("I/ToolPkg") })
        assertTrue(result.lines.any { it.contains("E/GeminiProvider") })
    }

    @Test
    fun excludeSystemDropsSystemTagsOnly() {
        val result = LogExportFilter.filterText(
            sample,
            LogExportOptions(excludeSystem = true)
        )
        assertFalse(result.lines.any { it.contains("D/WebView") })
        assertTrue(result.lines.any { it.contains("D/AIService") })
    }

    @Test
    fun errorContextKeepsLastErrorAndTenPercentPrefix() {
        val lines = (1..10).map { index ->
            val level = if (index == 10) "E" else "I"
            "2026-09-07 16:51:00.0${index.toString().padStart(2, '0')} $level/Tag$index: line $index"
        }
        val result = LogExportFilter.filterText(
            lines.joinToString(separator = "\n"),
            LogExportOptions(errorContextOnly = true)
        )
        assertEquals(2, result.exportedRecordCount)
        assertTrue(result.lines[0].contains("Tag9"))
        assertTrue(result.lines[1].contains("Tag10"))
    }

    @Test
    fun errorContextWithoutErrorsExportsNothing() {
        val noErrorLog = listOf(
            "2026-09-07 16:51:00.001 I/ToolPkg: ok",
            "2026-09-07 16:51:00.002 D/AIService: dbg"
        ).joinToString(separator = "\n")
        val result = LogExportFilter.filterText(
            noErrorLog,
            LogExportOptions(errorContextOnly = true)
        )
        assertEquals(0, result.exportedRecordCount)
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun hideSensitiveRedactsPromptAndRequestBody() {
        val raw = """
            2026-09-07 16:51:00.001 D/GeminiProvider: 发现系统消息: # Role Configuration secret prompt
            2026-09-07 16:51:00.002 D/GeminiProvider: 请求体JSON: Part 1/2: {"systemInstruction":"hidden"}
            extra continuation that still belongs to the dump
            2026-09-07 16:51:00.003 I/ToolPkg: package loaded
        """.trimIndent()
        val result = LogExportFilter.filterText(
            raw,
            LogExportOptions(hideSensitive = true)
        )
        val joined = result.lines.joinToString(separator = "\n")
        assertFalse(joined.contains("secret prompt"))
        assertFalse(joined.contains("hidden"))
        assertTrue(joined.contains("[redacted,"))
        assertTrue(joined.contains("package loaded"))
    }

    @Test
    fun stripTimestampRemovesClockPrefix() {
        val result = LogExportFilter.filterText(
            "2026-09-07 16:51:00.001 I/ToolPkg: package loaded",
            LogExportOptions(stripTimestamp = true)
        )
        assertEquals(listOf("I/ToolPkg: package loaded"), result.lines)
    }
}

