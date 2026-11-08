package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class A2uiCalculationsTest {
    @Test fun invalidCalculationsRecoverWithoutThrowing() {
        assertNull(CardFunctions.evaluate("divide", mapOf("a" to 12, "b" to 0)))
        assertEquals(4.0, CardFunctions.evaluate("divide", mapOf("a" to 12, "b" to 3)))
        assertNull(CardFunctions.evaluate("multiply", mapOf("a" to 1e12, "b" to 1e12)))
        assertNull(CardFunctions.evaluate("equals", mapOf("a" to Double.NaN, "b" to Double.NaN)))
        assertEquals(true, CardFunctions.evaluate("equals", mapOf("a" to 4, "b" to 4.0)))
        assertNull(CardFunctions.evaluate("concat", mapOf("values" to listOf("a".repeat(8192), "b"))))
        assertNull(CardFunctions.evaluate("formatNumber", mapOf("value" to 5, "decimals" to 9)))
        assertNull(CardFunctions.evaluate("openUrl", mapOf("value" to "https://example.com")))
    }

    @Test fun actualExamplesAreBoundedAndOnlyPureCallsAreAccepted() {
        listOf("calculator", "cost_calculator", "charts").forEach { name ->
            val text = javaClass.getResource("/a2ui/$name.ndjson")!!.readText()
            val batch = A2uiProtocol.parse(text, true)
            assertEquals(if (name == "charts") 4 else 1, batch.surfaceIds.size)
        }
        val source = javaClass.getResource("/a2ui/cost_calculator.ndjson")!!.readText()
        assertTrue(runCatching { A2uiProtocol.parse(source.replace("\"multiply\"", "\"exec\""), true) }.isFailure)
        val malformed = source + """{"version":"v0.9","updataDataModelPlaceholder":false}"""
        assertTrue(runCatching { A2uiProtocol.parse(malformed, true) }.exceptionOrNull()!!.message!!.contains("Line 4"))
        val stateCall = source + """{"version":"v0.9","updateDataModel":{"surfaceId":"calculator","path":"/bad","value":{"call":"add","args":{"a":1,"b":2}}}}"""
        assertTrue(runCatching { A2uiProtocol.parse(stateCall, true) }.isFailure)
    }

    @Test fun graphAndMapDataCannotIntroduceNonFiniteGeometry() {
        assertEquals(1, cardChartSeries(listOf(mapOf("values" to listOf(4))), listOf("A"), "line").single().points.size)
        assertTrue(runCatching { cardChartSeries(listOf(mapOf("values" to listOf(Double.POSITIVE_INFINITY))), emptyList(), "bar") }.isFailure)
        assertTrue(runCatching { cardChartSeries(listOf(mapOf("values" to listOf(-1))), emptyList(), "pie") }.isFailure)
        assertTrue(runCatching { cardChartSeries(listOf(mapOf("values" to listOf(1, 2))), listOf("A"), "line") }.isFailure)
        assertEquals(0.0, cardMapMarkers(listOf(mapOf("latitude" to 0, "longitude" to 0))).single().x, 0.0)
        assertTrue(runCatching { cardMapMarkers(listOf(mapOf("latitude" to 91, "longitude" to 0))) }.isFailure)
        assertEquals("—", cardNumberLabel(Float.POSITIVE_INFINITY))
        assertEquals("1.25", cardNumberLabel(1.25f))
    }

    @Test fun confirmationUsesFieldLabelsAndPreservesEverySubmittedValue() {
        val rows = cardSubmissionRows(
            Json.parseToJsonElement("""{"form":{"years":4,"discount":false},"note":"demo"}"""),
            Json.parseToJsonElement("""{"form":{"path":"/form"},"note":"demo"}"""),
            listOf(
                Json.parseToJsonElement("""{"label":"购买年限","number":{"path":"/form/years"}}""").jsonObject,
                Json.parseToJsonElement("""{"label":"模拟优惠","value":{"path":"/form/discount"}}""").jsonObject,
            ),
        )
        assertEquals(listOf("购买年限", "模拟优惠", "note"), rows.map { it.first })
        assertEquals(listOf(JsonPrimitive(4), JsonPrimitive(false), JsonPrimitive("demo")), rows.map { it.second })
    }
}
