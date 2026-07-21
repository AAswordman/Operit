package com.ai.assistance.operit.core.tools.calculator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsCalculatorTest {

    @After
    fun tearDown() {
        JsCalculator.clearVariables()
    }

    @Test fun `evaluate simple addition`() {
        assertEquals(5.0, JsCalculator.evaluate("2 + 3"), 0.001)
    }

    @Test fun `evaluate complex expression`() {
        assertEquals(14.0, JsCalculator.evaluate("2 + 3 * 4"), 0.001)
    }

    @Test fun `evaluate with parentheses`() {
        assertEquals(20.0, JsCalculator.evaluate("(2 + 3) * 4"), 0.001)
    }

    @Test fun `evaluate function call`() {
        assertEquals(5.0, JsCalculator.evaluate("abs(-5)"), 0.001)
    }

    @Test fun `evaluate trigonometric function`() {
        assertEquals(Math.sin(0.0), JsCalculator.evaluate("sin(0)"), 0.001)
    }

    @Test fun `evaluate with variable assignment`() {
        assertEquals(10.0, JsCalculator.evaluate("x=10"), 0.001)
    }

    @Test fun `evaluate with variable usage`() {
        JsCalculator.setVariable("x", 5.0)
        assertEquals(8.0, JsCalculator.evaluate("x + 3"), 0.001)
    }

    @Test fun `evaluate using stored variable`() {
        JsCalculator.evaluate("x=7")
        assertEquals(11.0, JsCalculator.evaluate("x + 4"), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `evaluate invalid expression throws`() {
        JsCalculator.evaluate("2 +")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `evaluate undefined variable throws`() {
        JsCalculator.evaluate("undefined_var")
    }

    @Test fun `calc returns formatted string`() {
        assertEquals("5", JsCalculator.calc("2 + 3"))
    }

    @Test fun `calc returns decimal formatted`() {
        assertEquals("3.14", JsCalculator.calc("1.14 + 2"))
    }

    @Test fun `calc integer result`() {
        assertEquals("42", JsCalculator.calc("6 * 7"))
    }

    @Test fun `calc negative result`() {
        assertEquals("-5", JsCalculator.calc("2 - 7"))
    }

    @Test fun `formatResult delegates correctly`() {
        assertEquals("42", JsCalculator.formatResult(42.0))
    }

    @Test fun `formatResult with decimal`() {
        assertEquals("3.14", JsCalculator.formatResult(3.14))
    }

    @Test fun `setVariable and getVariable round trip`() {
        JsCalculator.setVariable("test", 99.0)
        assertEquals(99.0, JsCalculator.getVariable("test"), 0.001)
    }

    @Test fun `getVariable returns set value`() {
        JsCalculator.setVariable("value", 3.14)
        assertEquals(3.14, JsCalculator.getVariable("value"), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getVariable undefined throws`() {
        JsCalculator.getVariable("nonexistent")
    }

    @Test fun `clearVariables resets state`() {
        JsCalculator.setVariable("temp", 100.0)
        JsCalculator.clearVariables()
        // PI should still be accessible
        assertEquals(Math.PI, JsCalculator.getVariable("PI"), 0.001)
    }

    @Test fun `getSupportedUnits returns categories`() {
        val units = JsCalculator.getSupportedUnits()
        assertTrue(units.containsKey("Temperature"))
        assertTrue(units.containsKey("Length"))
        assertTrue(units.containsKey("Weight"))
        assertTrue(units.containsKey("Volume"))
        assertTrue(units.containsKey("Speed"))
        assertEquals(5, units.size)
    }

    @Test fun `getSupportedUnits temperature list`() {
        val units = JsCalculator.getSupportedUnits()
        val tempUnits = units["Temperature"] ?: emptyList()
        assertTrue(tempUnits.any { it.contains("c") })
        assertTrue(tempUnits.any { it.contains("f") })
        assertTrue(tempUnits.any { it.contains("k") })
    }

    @Test fun `getSupportedDateFunctions returns list`() {
        val funcs = JsCalculator.getSupportedDateFunctions()
        assertTrue(funcs.any { it.startsWith("today()") })
        assertTrue(funcs.any { it.startsWith("now()") })
        assertTrue(funcs.any { it.startsWith("date_diff") })
        assertTrue(funcs.any { it.startsWith("date_add") })
    }

    @Test fun `getSupportedStatFunctions returns list`() {
        val funcs = JsCalculator.getSupportedStatFunctions()
        assertTrue(funcs.any { it.startsWith("stats.mean") })
        assertTrue(funcs.any { it.startsWith("stats.median") })
        assertTrue(funcs.any { it.startsWith("stats.min") })
        assertTrue(funcs.any { it.startsWith("stats.max") })
        assertTrue(funcs.any { it.startsWith("stats.sum") })
        assertTrue(funcs.any { it.startsWith("stats.stdev") })
    }

    @Test fun `getSupportedJsFeatures returns list`() {
        val features = JsCalculator.getSupportedJsFeatures()
        assertTrue(features.any { it.contains("Ternary") })
        assertTrue(features.any { it.contains("Math.") })
    }

    @Test fun `evaluate with chained operations`() {
        assertEquals(10.0, JsCalculator.evaluate("1 + 2 + 3 + 4"), 0.001)
    }

    @Test fun `evaluate division`() {
        assertEquals(3.0, JsCalculator.evaluate("9 / 3"), 0.001)
    }

    @Test fun `evaluate modulo`() {
        assertEquals(1.0, JsCalculator.evaluate("10 % 3"), 0.001)
    }

    @Test fun `evaluate exponent`() {
        assertEquals(8.0, JsCalculator.evaluate("2 ** 3"), 0.001)
    }

    @Test fun `evaluate comparison`() {
        assertEquals(1.0, JsCalculator.evaluate("5 > 3"), 0.001)
    }

    @Test fun `evaluate logical`() {
        assertEquals(1.0, JsCalculator.evaluate("1 && 1"), 0.001)
    }

    @Test fun `evaluate ternary`() {
        assertEquals(10.0, JsCalculator.evaluate("1 ? 10 : 20"), 0.001)
    }

    @Test fun `evaluate compound assignment`() {
        JsCalculator.evaluate("x=10")
        assertEquals(15.0, JsCalculator.evaluate("x+=5"), 0.001)
    }

    @Test fun `evaluate pi constant`() {
        assertEquals(Math.PI, JsCalculator.evaluate("PI"), 0.001)
    }

    @Test fun `evaluate e constant`() {
        assertEquals(Math.E, JsCalculator.evaluate("E"), 0.001)
    }
}
