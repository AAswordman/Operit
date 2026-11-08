package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class A2uiCalculatorTest {
    private fun press(vararg keys: String): Map<String, Any> =
        keys.fold(CardCalculator.initial()) { state, key -> requireNotNull(CardCalculator.step(state, key)) }

    @Test fun decimalArithmeticAndChaining() {
        assertEquals("408", press("1", "2", "*", "3", "4", "=")["display"])
        assertEquals("0.3", press("0", ".", "1", "+", "0", ".", "2", "=")["display"])
        assertEquals("20", press("2", "+", "3", "*", "4", "=")["display"])
        assertEquals("6", press("2", "+", "*", "3", "=")["display"])
        assertEquals("7.033", press("3", ".", "2", "1", "9", "+", "3", ".", "8", "1", "4", "=")["display"])
    }

    @Test fun editingAndErrorRecovery() {
        assertEquals("1.5", press("1", ".", ".", "5")["display"])
        assertEquals("12", press("1", "2", "3", "backspace")["display"])
        assertEquals("-5", press("5", "sign")["display"])
        assertEquals(true, press("1", "/", "0", "=")["error"])
        assertEquals(true, press("1", "/", "0", "=", "sign")["error"])
        assertEquals("4", press("1", "/", "0", "=", "4")["display"])
        assertEquals(CardCalculator.initial(), press("1", "/", "0", "=", "AC"))
        assertEquals("4", press("1", "+", "3", "=", "=")["display"])
    }

    @Test fun boundedReducerNeverExecutesUnknownActions() {
        assertNull(CardCalculator.step(CardCalculator.initial(), "exec"))
        assertNull(CardCalculator.step(emptyMap<String, Any>(), "1"))
        val hostile = CardCalculator.initial() + ("display" to "1e-999999999")
        assertEquals(true, CardCalculator.step(hostile, "+")!!["error"])
        val tiny = CardCalculator.initial() + mapOf("display" to "1000000000000", "stored" to "0.000000000000000000000001", "op" to "/")
        assertEquals(true, CardCalculator.step(tiny, "=")!!["error"])
        val state = press("9", "9", "9", "9", "9", "9", "9", "9", "9", "9", "9", "9", "*", "9", "=")
        assertEquals(true, state["error"])
        assertTrue(A2uiProtocol.toJson(state).toString().length < 256)
    }

    @Test fun nativeValidatorExplainsActualDeviceFailureWithoutEchoingSource() {
        val source = javaClass.getResource("/a2ui/device-malformed-calculator.ndjson")!!.readText()
        val report = A2uiProtocol.validationReport(source)
        assertEquals(false, report["valid"]!!.jsonPrimitive.boolean)
        assertEquals(2, report["line"]!!.jsonPrimitive.int)
        assertEquals("invalid_json", report["code"]!!.jsonPrimitive.content)
        assertFalse(report.toString().contains("/calc/"))
        assertEquals(4, A2uiProtocol.validationReport("\n\n$source")["line"]!!.jsonPrimitive.int)
        val keypad = javaClass.getResource("/a2ui/calculator.ndjson")!!.readText()
        assertEquals(true, A2uiProtocol.validationReport(keypad)["valid"]!!.jsonPrimitive.boolean)
        assertEquals(false, A2uiProtocol.validationReport(keypad.replace("\"columnCount\":4", "\"columnCount\":5"))["valid"]!!.jsonPrimitive.boolean)
    }
}
