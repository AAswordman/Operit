package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import org.junit.Assert.*
import org.junit.Test

class WorkspaceCommentSyntaxTest {
    @Test
    fun cssFamilyExposesBlockCommentSyntax() {
        for (extension in listOf("css", "scss", "sass", "less")) {
            val language = LanguageDetector.detectLanguage("sample.$extension")
            assertEquals("css", language)
            val syntax = CommentSyntaxRegistry.forLanguage(language)!!
            assertEquals("/*", syntax.blockStart)
            assertEquals("*/", syntax.blockEnd)
        }
    }

    @Test
    fun registeredPascalPerlAndErlangRulesAreReachableFromFileNames() {
        val expected = mapOf("pas" to "pascal", "pp" to "pascal", "pl" to "perl",
            "pm" to "perl", "erl" to "erlang", "hrl" to "erlang")
        for ((extension, language) in expected) {
            assertEquals(language, LanguageDetector.detectLanguage("file.$extension"))
            assertNotNull(CommentSyntaxRegistry.forLanguage(language))
        }
        assertNull(CommentSyntaxRegistry.forLanguage(LanguageDetector.detectLanguage("unknown.bin")))
    }
}