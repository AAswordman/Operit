package com.ai.assistance.operit.data.converter

import java.io.StringReader
import java.io.StringWriter
import org.junit.Assert.assertEquals
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
        assertEquals(48, ChatHistoryCsv.HEADER.size)
    }
}
