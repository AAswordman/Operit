package com.ai.assistance.operit.ui.common.a2ui

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class A2uiProtocolTest {
    private val create = """{"version":"v0.9","createSurface":{"surfaceId":"test","catalogId":"${A2uiProtocol.CATALOG}"}}"""
    private fun components(content: String) =
        """{"version":"v0.9","updateComponents":{"surfaceId":"test","components":[$content]}}"""
    private val text = """{"id":"root","component":"Text","text":"Hello"}"""
    private fun rejected(source: String) = assertTrue(runCatching { A2uiProtocol.parse(source, true) }.isFailure)

    @Test fun completeCardAndPartialLastEvent() {
        assertEquals(setOf("test"), A2uiProtocol.parse("$create\n${components(text)}", true).surfaceIds)
        assertEquals(listOf(create), A2uiProtocol.parse("$create\n{\"version", false).lines)
        rejected("$create\n{\"version")
    }

    @Test fun mergedCyclesAndMissingChildrenRejected() {
        rejected("$create\n${components("""{"id":"root","component":"Column","children":["a"]},{"id":"a","component":"Column","children":["root"]}""")}")
        rejected("$create\n${components("""{"id":"root","component":"Card","child":"missing"}""")}")
    }

    @Test fun repeatedChildrenAndBadWeightRejected() {
        rejected("$create\n${components("""{"id":"root","component":"Column","children":["x","x"]}""")}")
        rejected("$create\n${components("""{"id":"root","component":"Text","text":"x","weight":0}""")}")
    }

    @Test fun nativeSingleChildRowRemainsValidButCannotSupplyAmbiguousWeights() {
        val nodes = """{"id":"root","component":"Row","child":"t","wrap":false},{"id":"t","component":"Text","text":"Title"}"""
        assertEquals(JsonPrimitive(true), A2uiProtocol.validationReport("$create\n${components(nodes)}")["valid"])
        val ambiguous = nodes.replace("\"child\":\"t\"", "\"child\":\"t\",\"children\":[\"t\"],\"weights\":[1]")
        rejected("$create\n${components(ambiguous)}")
    }

    @Test fun rejectUnsafeExpressionsAndSparseArrays() {
        rejected("$create\n${components("""{"id":"root","component":"TextField","value":{"path":"/a/99999999"}}""")}")
        rejected("$create\n${components("""{"id":"root","component":"Text","text":{"call":"openUrl"}}""")}")
        rejected("$create\n${components("""{"id":"root","component":"TextField","validationRegexp":"(a+)+"}""")}")
        rejected("$create\n${components("""{"id":"root","component":"Slider","min":0,"max":1000000000}""")}")
    }

    @Test fun exponentialDagRejected() {
        val nodes = (0..10).flatMap { level ->
            listOf("a", "b").map { suffix ->
                """{"id":"n$level$suffix","component":"Column","children":["n${level + 1}a","n${level + 1}b"]}"""
            }
        } + """{"id":"root","component":"Card","child":"n0a"}""" +
            """{"id":"n11a","component":"Text","text":"end"}""" +
            """{"id":"n11b","component":"Text","text":"end"}"""
        rejected("$create\n${components(nodes.joinToString(","))}")
    }

    @Test fun boundedStateAndSafeLinks() {
        assertTrue(A2uiProtocol.safeUrl("https://example.com/a"))
        assertFalse(A2uiProtocol.safeUrl("javascript:alert(1)"))
        assertFalse(A2uiProtocol.safeUrl("https://user:secret@example.com"))
        assertFalse(A2uiProtocol.safeUrl("file:///sdcard/x"))
        assertTrue(runCatching { A2uiProtocol.toJson(List(129) { 0 }) }.isFailure)
        assertEquals("""{"form":{"effort":"Medium"}}""",
            A2uiProtocol.toJson(mapOf("form" to mapOf("effort" to "Medium"))).toString())
    }

    @Test fun malformedFieldUpdateIsReportedInsteadOfCrashingUi() {
        var reported = false
        assertFalse(updateCardField({ reported = true }) { throw IllegalStateException("Invalid parent container") })
        assertTrue(reported)
        assertTrue(updateCardField({ fail("Successful update must not report an error") }) {})
        assertTrue(runCatching {
            updateCardField({}) { throw kotlinx.coroutines.CancellationException("disposed") }
        }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test fun missingClosingBraceReportsLineAndSafeReasonWhileNestedDataIsValid() {
        val initial = """{"version":"v0.9","updateDataModel":{"surfaceId":"test","path":"/","value":{"calculator":{"display":"private-value","error":false}}}}"""
        val source = "$create\n${components(text)}\n$initial"
        assertTrue(A2uiProtocol.validationReport(source)["valid"]!!.jsonPrimitive.boolean)
        val report = A2uiProtocol.validationReport(source.dropLast(1))
        assertFalse(report["valid"]!!.jsonPrimitive.boolean)
        assertEquals(3, report["line"]!!.jsonPrimitive.int)
        assertEquals("invalid_json", report["code"]!!.jsonPrimitive.content)
        assertTrue(report["message"]!!.jsonPrimitive.content.contains("line 3"))
        assertTrue(report["message"]!!.jsonPrimitive.content.contains("Nested initial data is supported"))
        assertFalse(report.toString().contains("private-value"))
    }

    @Test fun structuralFailureExplainsTheActualConstraint() {
        val report = A2uiProtocol.validationReport("$create\n${components("""{"id":"root","component":"Grid","columnCount":5,"children":[]}""")}")
        assertEquals(2, report["line"]!!.jsonPrimitive.int)
        assertEquals("invalid_card", report["code"]!!.jsonPrimitive.content)
        assertTrue(report["message"]!!.jsonPrimitive.content.contains("Grid columns must be 1 to 4"))
    }

    @Test fun gridSpansAreStaticBoundedAndMatchChildren() {
        val child = """{"id":"a","component":"Display","text":"42","label":"Result"}"""
        fun document(extra: String) = "$create\n${components("""{"id":"root","component":"Grid","children":["a"],$extra},$child""")}"
        assertTrue(A2uiProtocol.validationReport(document(""""columnCount":4,"columnSpans":[2]"""))["valid"]!!.jsonPrimitive.boolean)
        for (fields in listOf(
            """"columnSpans":[2]""",
            """"columnCount":4,"columnSpans":[]""",
            """"columnCount":4,"columnSpans":[5]""",
            """"columnCount":4,"columnSpans":[0]""",
            """"columnCount":4,"columnSpans":[1.5]""",
            """"columnCount":4,"columnSpans":["2"]""",
            """"columnCount":4,"columnSpans":{"path":"/spans"}""",
        )) rejected(document(fields))
    }
}
