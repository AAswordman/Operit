package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSourceHighlightingTest {
    private data class Token(val kind: ThemeSourceToken, val start: Int, val end: Int, val text: String)

    private fun tokens(source: String, json: Boolean = false): List<Token> = buildList {
        scanThemeSource(source, json) { kind, start, end ->
            assertTrue(start >= 0 && end > start && end <= source.length)
            add(Token(kind, start, end, source.substring(start, end)))
        }
    }

    @Test
    fun jsonHighlightsStringsWithoutTreatingUrlsOrEscapesAsComments() {
        val source = """{"label":"a\"b","url":"https://host/a//b","count":12,"enabled":true}"""
        val result = tokens(source, json = true)
        assertEquals(6, result.size)
        assertTrue(result.all { it.kind == ThemeSourceToken.String })
        assertEquals("\"a\\\"b\"", result[1].text)
        assertEquals("\"https://host/a//b\"", result[3].text)
    }

    @Test
    fun commentsDoNotHighlightQuotedKeywordsAndStringsDoNotStartComments() {
        val source = "const url = \"https://host//x\"; // return \"comment\"\n/* const x */ return url;"
        val result = tokens(source)
        assertEquals(listOf("const", "return"), result.filter { it.kind == ThemeSourceToken.Keyword }.map { it.text })
        assertEquals(2, result.count { it.kind == ThemeSourceToken.Comment })
        assertEquals(listOf("\"https://host//x\""), result.filter { it.kind == ThemeSourceToken.String }.map { it.text })
    }

    @Test
    fun javascriptHighlightsCommonJsMethodsAndNumbersWithoutAbsorbingArithmetic() {
        val source = "module.exports = { render(context) { return Math.round(0xff + 1.2e-3 - 2n); } };"
        val result = tokens(source)
        assertEquals(listOf("0xff", "1.2e-3", "2n"), result.filter { it.kind == ThemeSourceToken.Number }.map { it.text })
        assertEquals(listOf("render", "round"), result.filter { it.kind == ThemeSourceToken.Function }.map { it.text })
        assertTrue(result.any { it.kind == ThemeSourceToken.Context && it.text == "module" })
        assertTrue(result.any { it.kind == ThemeSourceToken.Keyword && it.text == "return" })
    }

    @Test
    fun unfinishedQuotedLinesDoNotHideTheNextLine() {
        val source = "const name = \"unfinished\nreturn 2;"
        val result = tokens(source)
        assertEquals("\"unfinished", result.single { it.kind == ThemeSourceToken.String }.text)
        assertTrue(result.any { it.kind == ThemeSourceToken.Keyword && it.text == "return" })
        assertEquals("2", result.single { it.kind == ThemeSourceToken.Number }.text)
    }

    @Test
    fun blockCommentsCannotCloseUsingTheirOpeningAsterisk() {
        for (source in listOf("/*/ return 1;", "/* unfinished", "/*")) {
            val result = tokens(source)
            assertEquals(1, result.size)
            assertEquals(ThemeSourceToken.Comment, result.single().kind)
            assertEquals(source, result.single().text)
        }
        assertEquals(listOf(ThemeSourceToken.Comment, ThemeSourceToken.Keyword), tokens("/**/ return").map { it.kind })
    }

    @Test
    fun unicodeAndMultilineTemplatesKeepOriginalUtf16Offsets() {
        val source = "// 中文🙂\nconst text = `你好🙂\nworld`;"
        val result = tokens(source)
        val template = result.single { it.kind == ThemeSourceToken.String }
        assertEquals(source.indexOf('`'), template.start)
        assertEquals(source.lastIndexOf('`') + 1, template.end)
        assertEquals("`你好🙂\nworld`", template.text)
        assertTrue(result.zipWithNext().all { (left, right) -> left.end <= right.start })
    }
}
