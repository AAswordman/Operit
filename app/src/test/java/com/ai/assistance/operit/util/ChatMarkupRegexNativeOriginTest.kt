package com.ai.assistance.operit.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkupRegexNativeOriginTest {

    @Test
    fun isNativeToolCallOrigin_detectsMarkerInOpeningTag() {
        val xml = "<tool_abc name=\"read_file\" data-origin=\"native_tool_call\"><param name=\"path\">/tmp</param></tool_abc>"
        assertTrue(ChatMarkupRegex.isNativeToolCallOrigin(xml))
    }

    @Test
    fun isNativeToolCallOrigin_returnsFalseWithoutMarker() {
        val xml = "<tool_abc name=\"read_file\"><param name=\"path\">/tmp</param></tool_abc>"
        assertFalse(ChatMarkupRegex.isNativeToolCallOrigin(xml))
    }

    @Test
    fun isNativeToolCallOrigin_ignoresMarkerInParamContent() {
        // AI writes the marker string inside a param value — should NOT match
        val xml = "<tool_abc name=\"echo\"><param name=\"text\">data-origin=\"native_tool_call\"</param></tool_abc>"
        assertFalse(ChatMarkupRegex.isNativeToolCallOrigin(xml))
    }

    @Test
    fun isNativeToolCallOrigin_isCaseInsensitive() {
        val xml = "<tool_abc name=\"read_file\" DATA-ORIGIN=\"NATIVE_TOOL_CALL\">x</tool_abc>"
        assertTrue(ChatMarkupRegex.isNativeToolCallOrigin(xml))
    }

    @Test
    fun isNativeToolCallOrigin_acceptsSingleQuotes() {
        val xml = "<tool_abc name=\"read_file\" data-origin='native_tool_call'>x</tool_abc>"
        assertTrue(ChatMarkupRegex.isNativeToolCallOrigin(xml))
    }

    @Test
    fun isNativeToolCallOrigin_returnsFalseForEmptyInput() {
        assertFalse(ChatMarkupRegex.isNativeToolCallOrigin(""))
    }

    @Test
    fun isNativeToolCallOrigin_returnsFalseForPlainText() {
        assertFalse(ChatMarkupRegex.isNativeToolCallOrigin("just some text without tags"))
    }
}