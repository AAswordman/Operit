package com.ai.assistance.operit.core.tools.packTool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ToolPkgMarketOriginCodecTest {
    @Test
    fun `encodes an ascii xor marker without readable author data`() {
        val origin =
            ToolPkgMarketOrigin(
                market = "Operit",
                toolpkgId = "uuid-generator",
                version = "1.0.2",
                author = listOf("Original Author")
            )

        val marker = ToolPkgMarketOriginCodec.encode(origin)

        assertFalse(marker.contains("Original Author"))
        assertFalse(marker.contains("uuid-generator"))
        assertEquals("ToolPkg._m", marker.substringBefore('('))
    }

    @Test
    fun `parses and validates marketplace origin for matching package`() {
        val origin =
            ToolPkgMarketOriginCodec.parse(
                "{\"market\":\"Operit\",\"toolpkgId\":\"uuid-generator\",\"version\":\" 1.0.2 \",\"author\":[\" Original Author \",\"\"]}"
            )

        assertEquals(
            ToolPkgMarketOrigin(
                market = "Operit",
                toolpkgId = "uuid-generator",
                version = "1.0.2",
                author = listOf("Original Author")
            ),
            ToolPkgMarketOriginCodec.validateForPackage(origin, "uuid-generator")
        )
    }

    @Test
    fun `does not trust an invalid marker or a marker for another package`() {
        val invalid = ToolPkgMarketOriginCodec.parse("{\"market\":\"Operit\"}")
        val mismatched =
            ToolPkgMarketOriginCodec.parse(
                "{\"market\":\"Operit\",\"toolpkgId\":\"other\",\"version\":\"1.0.2\",\"author\":[]}"
            )

        assertNull(invalid)
        assertNull(ToolPkgMarketOriginCodec.validateForPackage(mismatched, "uuid-generator"))
    }

    @Test
    fun `round trips metadata marker without readable author data`() {
        val origin =
            ToolPkgMarketOrigin(
                market = "Operit",
                toolpkgId = "uuid-generator",
                version = "1.0.2",
                author = listOf("Original Author")
            )

        val marker = ToolPkgMarketOriginCodec.encodeForMetadata(origin)

        assertFalse(marker.contains("Original Author"))
        assertEquals(origin, ToolPkgMarketOriginCodec.decodeMetadata(marker))
    }
}
