package com.ai.assistance.operit.core.config

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemToolPromptsReadFileRangeTest {

    @Test
    fun `read_file exposes line range parameters in both schemas`() {
        listOf(SystemToolPrompts.fileSystemTools, SystemToolPrompts.fileSystemToolsCn).forEach { category ->
            val readFile = category.tools.single { it.name == "read_file" }
            val parameters = requireNotNull(readFile.parametersStructured)
            val startLine = parameters.single { it.name == "start_line" }
            val endLine = parameters.single { it.name == "end_line" }

            assertEquals("integer", startLine.type)
            assertEquals("1", startLine.default)
            assertEquals("integer", endLine.type)
            assertEquals("start_line + 99", endLine.default)
        }
    }
}
