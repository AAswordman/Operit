package com.ai.assistance.operit.core.tools.packTool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ToolPkgApiCompatibilityTest {
    @Test
    fun `blank declared API version is legacy version`() {
        assertEquals(
            ToolPkgApiVersion.parse("1.0.0"),
            ToolPkgApiCompatibility.parseDeclaredApiVersion(" ")
        )
    }

    @Test
    fun `unsupported declared API version reports host support`() {
        expectIllegalArgument("Supported ToolPkg API versions: 1.0.0") {
            ToolPkgApiCompatibility.requireSupported(
                apiVersion = "1.0.1",
                operitVersion = "1.12.1+3"
            )
        }
    }

    private fun expectIllegalArgument(
        messageFragment: String,
        block: () -> Unit
    ) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains(messageFragment))
        }
    }
}
