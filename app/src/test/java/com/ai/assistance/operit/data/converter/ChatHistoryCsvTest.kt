package com.ai.assistance.operit.data.converter

import java.io.StringReader
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ChatHistoryCsvTest {
    @Test
    fun rowWriterAndReaderRoundTripQuotedAndMultilineFields() {
        val writer = StringWriter()
        ChatHistoryCsv.writeRow(
            writer,
            listOf("plain", "with,comma", "with\"quote", "line one\nline two", ""),
        )

        val reader = ChatHistoryCsv.RowReader(StringReader(writer.toString()))
        assertEquals(
            listOf("plain", "with,comma", "with\"quote", "line one\nline two", ""),
            reader.nextRow(),
        )
        assertNull(reader.nextRow())
    }

    @Test
    fun rowReaderAcceptsCrLfAndKeepsEmptyFields() {
        val reader = ChatHistoryCsv.RowReader(StringReader("a,,\"b\r\nc\"\r\n"))

        assertEquals(listOf("a", "", "b\r\nc"), reader.nextRow())
        assertNull(reader.nextRow())
    }

    @Test
    fun headerContainsVersionAndSeparateRecordTypes() {
        assertEquals("format_version", ChatHistoryCsv.HEADER.first())
        assertEquals("record_type", ChatHistoryCsv.HEADER[1])
        assertEquals(53, ChatHistoryCsv.HEADER.size)
        assertEquals(ChatHistoryCsv.HEADER.size, ChatHistoryCsv.HEADER.distinct().size)
        assertTrue(ChatHistoryCsv.HEADER.containsAll(listOf("message_sections", "variant_sections", "folder_id", "last_used_at", "folder_path", "usage_breakdown_json")))
    }

    @Test
    fun recognizesLegacyMultilineCsvWithoutFolderColumns() {
        val legacyHeader = ChatHistoryCsv.HEADER
            .filterNot { it in setOf("folder_id", "last_used_at", "folder_path", "archived", "usage_breakdown_json") }
            .map { when (it) {
                "message_sections" -> "message_content"
                "variant_sections" -> "variant_content"
                else -> it
            } }
        assertTrue(ChatHistoryCsv.supportsHeader(legacyHeader))
        val multilineContent = legacyHeader.joinToString(",") + "\n\"first line\nsecond line\nthird line\nlast line\""
        assertEquals(ChatFormat.CSV, ChatFormatDetector.detectFormat(multilineContent))
    }

    @Test
    fun rejectsDuplicateHeadersAndMissingVariantContent() {
        assertFalse(ChatHistoryCsv.supportsHeader(ChatHistoryCsv.HEADER + "message_sections"))
        assertFalse(ChatHistoryCsv.supportsHeader(ChatHistoryCsv.HEADER - "variant_sections"))
    }
}
