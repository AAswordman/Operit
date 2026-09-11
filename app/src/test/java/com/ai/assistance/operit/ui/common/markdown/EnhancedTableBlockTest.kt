package com.ai.assistance.operit.ui.common.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedTableBlockTest {

    @Test
    fun parseToolTableWithHeaderAndMultipleColumns() {
        val markdown = """
            | Col 1 | Col 2 | Col 3 |
            | ----- | -----2 | -----3 |
            | A1    | B1    | C1    |
            | A2    | B2    | C2    |
        """.trimIndent()

        val tableData = parseTable(markdown)
        assertTrue(tableData.hasHeader)
        assertEquals(3, tableData.rows.size) // header row + 2 data rows
        assertEquals(listOf("header", "Col 1", "Col 2", "Col 3"), listOf("header") + tableData.rows[0])
        assertEquals(3, tableData.rows[1].size)
        assertEquals("A1", tableData.rows[1][0])
        assertEquals("C1", tableData.rows[1][2])
    }
}
