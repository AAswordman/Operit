package com.ai.assistance.operit.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for issue #1346: grep context rendering must show the real
 * source line numbers at file start, file end, middle positions, merged
 * multi-match blocks and different context_lines values.
 *
 * The matchContext strings below mirror the native ripgrep output contract:
 * each context line is "<number>|<text>" and matched lines are "<number>|><text>".
 */
class GrepResultDataTest {

    private fun grepResult(
        lineNumber: Int,
        lineContent: String,
        matchContext: String?
    ): GrepResultData {
        return GrepResultData(
            searchPath = "/workspace",
            pattern = "needle",
            matches =
                listOf(
                    GrepResultData.FileMatch(
                        filePath = "/workspace/A.kt",
                        lineMatches =
                            listOf(
                                GrepResultData.LineMatch(
                                    lineNumber = lineNumber,
                                    lineContent = lineContent,
                                    matchContext = matchContext
                                )
                            )
                    )
                ),
            totalMatches = 1,
            filesSearched = 1
        )
    }

    /** Extracts (lineNumber, isMarked, text) for every rendered "N|" output line. */
    private fun renderedNumberedLines(output: String): List<Triple<Int, Boolean, String>> {
        return output.lines().mapNotNull { line ->
            val separator = line.indexOf('|')
            if (separator <= 0) return@mapNotNull null
            val number = line.substring(0, separator).trim().toIntOrNull() ?: return@mapNotNull null
            val rest = line.substring(separator + 1)
            if (rest.startsWith(">")) {
                Triple(number, true, rest.removePrefix(">"))
            } else {
                Triple(number, false, rest.removePrefix(" "))
            }
        }
    }

    private fun assertNoInvalidLineNumbers(output: String) {
        val rendered = renderedNumberedLines(output)
        assertTrue("expected numbered context lines in output", rendered.isNotEmpty())
        rendered.forEach { (number, _, _) ->
            assertTrue("line number must be >= 1, got $number", number >= 1)
        }
    }

    @Test
    fun `match near file start renders true line numbers`() {
        // context_lines = 3, match on line 1: the window is clamped to lines 1-4.
        val result =
            grepResult(
                lineNumber = 1,
                lineContent = "needle line 1",
                matchContext =
                    " 1|>needle line 1\n 2| line 2\n 3| line 3\n 4| line 4"
            )

        val output = result.toString()
        assertNoInvalidLineNumbers(output)
        val rendered = renderedNumberedLines(output)
        assertEquals(listOf(1, 2, 3, 4), rendered.map { it.first })
        assertEquals(Triple(1, true, "needle line 1"), rendered[0])
        assertEquals(Triple(4, false, "line 4"), rendered[3])
    }

    @Test
    fun `match near file end renders true line numbers`() {
        // context_lines = 3, match on the last line of a 20-line file: window 17-20.
        val result =
            grepResult(
                lineNumber = 20,
                lineContent = "needle line 20",
                matchContext =
                    "17| line 17\n18| line 18\n19| line 19\n20|>needle line 20"
            )

        val output = result.toString()
        assertNoInvalidLineNumbers(output)
        val rendered = renderedNumberedLines(output)
        assertEquals(listOf(17, 18, 19, 20), rendered.map { it.first })
        assertEquals(Triple(20, true, "needle line 20"), rendered.last())
    }

    @Test
    fun `match in file middle renders symmetric window`() {
        // context_lines = 3, match on line 10: full symmetric window 7-13.
        val context =
            (7..13).joinToString("\n") { n ->
                if (n == 10) "10|>needle line 10" else "$n| line $n"
            }
        val result = grepResult(lineNumber = 10, lineContent = "needle line 10", matchContext = context)

        val output = result.toString()
        assertNoInvalidLineNumbers(output)
        val rendered = renderedNumberedLines(output)
        assertEquals((7..13).toList(), rendered.map { it.first })
        val matchLine = rendered.single { it.second }
        assertEquals(Triple(10, true, "needle line 10"), matchLine)
    }

    @Test
    fun `different context line counts render correct numbers`() {
        // context_lines = 1 around line 10.
        val small =
            grepResult(
                lineNumber = 10,
                lineContent = "needle line 10",
                matchContext = " 9| line 9\n10|>needle line 10\n11| line 11"
            )
        val smallRendered = renderedNumberedLines(small.toString())
        assertEquals(listOf(9, 10, 11), smallRendered.map { it.first })

        // context_lines = 5 around line 10.
        val wideContext =
            (5..15).joinToString("\n") { n ->
                if (n == 10) "10|>needle line 10" else "$n| line $n"
            }
        val wide = grepResult(lineNumber = 10, lineContent = "needle line 10", matchContext = wideContext)
        val wideRendered = renderedNumberedLines(wide.toString())
        assertEquals((5..15).toList(), wideRendered.map { it.first })
    }

    @Test
    fun `merged multi match block keeps every match marked with true numbers`() {
        // Two matches at lines 2 and 25 with context_lines = 1: the merged
        // context is non-contiguous (1-3, 24-26), which used to break the
        // center-based line number reconstruction completely.
        val result =
            grepResult(
                lineNumber = 2,
                lineContent = "2 matches: needle line 2 | needle line 25...",
                matchContext =
                    " 1| line 1\n 2|>needle line 2\n 3| line 3\n24| line 24\n25|>needle line 25\n26| line 26"
            )

        val output = result.toString()
        assertNoInvalidLineNumbers(output)
        val rendered = renderedNumberedLines(output)
        assertEquals(listOf(1, 2, 3, 24, 25, 26), rendered.map { it.first })
        assertEquals(listOf(2, 25), rendered.filter { it.second }.map { it.first })
    }

    @Test
    fun `unmarked numbered context gets match marker on the match line`() {
        // read_file_part enriched contexts carry numbers but no match marker;
        // the renderer marks the line matching lineNumber.
        val result =
            grepResult(
                lineNumber = 10,
                lineContent = "needle line 10",
                matchContext = " 9| line 9\n10| needle line 10\n11| line 11"
            )

        val rendered = renderedNumberedLines(result.toString())
        assertEquals(listOf(10), rendered.filter { it.second }.map { it.first })
    }

    @Test
    fun `truncation marker line is rendered verbatim without breaking numbers`() {
        val result =
            grepResult(
                lineNumber = 1,
                lineContent = "needle line 1",
                matchContext = " 1|>needle line 1\n 2| line 2\n..."
            )

        val output = result.toString()
        assertNoInvalidLineNumbers(output)
        assertTrue(output.lines().any { it == "..." })
        assertEquals(listOf(1, 2), renderedNumberedLines(output).map { it.first })
    }

    @Test
    fun `match without context renders only the match line`() {
        val result = grepResult(lineNumber = 10, lineContent = "needle line 10", matchContext = null)

        val rendered = renderedNumberedLines(result.toString())
        assertEquals(listOf(Triple(10, false, "needle line 10")), rendered)
    }
}
