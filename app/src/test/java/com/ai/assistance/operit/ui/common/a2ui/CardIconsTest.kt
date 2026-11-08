package com.ai.assistance.operit.ui.common.a2ui

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.VectorPath
import com.ai.assistance.operit.ui.common.icons.LucideIconData
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardIconsTest {
    @Test fun everyPinnedIconParsesWithoutDroppingPaths() {
        assertEquals(1535, LucideIconData.entries.size)
        LucideIconData.entries.forEach { (name, data) ->
            val vector = requireNotNull(CardIcons.get("lucide:$name"))
            val groups = data.split('\t', limit = 3).take(2)
            assertEquals(name, groups.sumOf { it.split('|').count(String::isNotBlank) }, vector.root.size)
            for (node in vector.root) {
                val path = node as VectorPath
                assertFalse(path.pathData.isEmpty())
                assertNotNull(path.stroke)
                assertEquals(2f, path.strokeLineWidth, 0f)
                assertEquals(StrokeCap.Round, path.strokeLineCap)
            }
        }
        val filled = LucideIconData.entries.entries.first { it.value.split('\t', limit = 3)[1].isNotBlank() }
        assertTrue(CardIcons.get("lucide:${filled.key}")!!.root.any { (it as VectorPath).fill != null })
    }

    @Test fun legacyNamesKeepArtworkAndUnknownNamesDoNotAllocate() {
        cardFlowIcons.forEach { (name, vector) -> assertSame(vector, CardIcons.get(name)) }
        assertSame(CardIcons.get("lucide:orbit"), CardIcons.get("orbit"))
        assertNotSame(CardIcons.get("check"), CardIcons.get("lucide:check"))
        assertNull(CardIcons.get("../../arbitrary.svg"))
        assertFalse(CardIcons.contains("lucide:not-an-icon"))
    }

    @Test fun discoverySearchesTagsAndPaginatesWithoutDumpingTheCatalog() {
        fun search(query: String, limit: Int = 30, offset: Int = 0) =
            Json.parseToJsonElement(CardIcons.search(query, limit, offset)).jsonObject
        assertEquals("lucide:orbit", search("orbit")["icons"]!!.jsonArray.first().jsonPrimitive.content)
        assertTrue(search("lucide:brain")["icons"]!!.jsonArray.any { it.jsonPrimitive.content == "lucide:brain" })
        assertTrue(search("font size")["icons"]!!.jsonArray.isNotEmpty())
        val first = search("", 10)
        val second = search("", 10, first["next_offset"]!!.jsonPrimitive.int)
        assertEquals(10, first["icons"]!!.jsonArray.size)
        assertEquals(1535, first["total"]!!.jsonPrimitive.int)
        assertTrue(first["icons"]!!.jsonArray.intersect(second["icons"]!!.jsonArray.toSet()).isEmpty())
        assertEquals(JsonNull, search("", 10, 1535)["next_offset"])
    }

    @Test fun iconsSupportSemanticColorAndBoundedSizeInTheCompilerAndRenderer() {
        for (size in listOf(12, 24, 48, 96)) {
            val report = CardExpress.report("""root=Icon(name="lucide:orbit",tone="accent",size=$size)""")
            assertEquals(report.toString(), JsonPrimitive(true), report["valid"])
        }
        val dynamicTone = "${'$'}/tone=\"info\"\nroot=Icon(name=\"lucide:brain\",tone=${'$'}/tone,size=32)"
        assertEquals(JsonPrimitive(true), CardExpress.report(dynamicTone)["valid"])
        for (bad in listOf("size=0", "size=97", "size=24.5", "tone=\"pink\"")) {
            assertEquals(JsonPrimitive(false), CardExpress.report("""root=Icon(name="lucide:orbit",$bad)""")["valid"])
        }
        assertEquals(JsonPrimitive(true), CardExpress.report(
            """root=Flow(label="Plan",steps=[{title:"Think",icon:"lucide:brain"}])"""
        )["valid"])
    }
}
