package com.ai.assistance.operit.core.tools.calculator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {

    @After
    fun tearDown() {
        Calculator.clearVariables()
    }

    @Test fun `evalExpression simple addition`() {
        assertEquals(5.0, Calculator.evalExpression("2 + 3"), 0.001)
    }

    @Test fun `evalExpression with multiplication`() {
        assertEquals(12.0, Calculator.evalExpression("3 * 4"), 0.001)
    }

    @Test fun `evalExpression with parentheses`() {
        assertEquals(20.0, Calculator.evalExpression("(2 + 3) * 4"), 0.001)
    }

    @Test fun `evalExpression with function call`() {
        assertEquals(5.0, Calculator.evalExpression("abs(-5)"), 0.001)
    }

    @Test fun `evalExpression with negative result`() {
        assertEquals(-1.0, Calculator.evalExpression("2 - 3"), 0.001)
    }

    @Test fun `evalExpression division`() {
        assertEquals(2.5, Calculator.evalExpression("5 / 2"), 0.001)
    }

    @Test fun `setVariable and getVariable round trip`() {
        Calculator.setVariable("x", 42.0)
        val result = Calculator.getVariable("x")
        assertEquals(42.0, result ?: 0.0, 0.001)
    }

    @Test fun `getVariable returns null for undefined`() {
        assertNull(Calculator.getVariable("nonexistent"))
    }

    @Test fun `clearVariables resets state`() {
        Calculator.setVariable("x", 100.0)
        Calculator.clearVariables()
        val pi = Calculator.getVariable("PI")
        assertEquals(Math.PI, pi ?: 0.0, 0.001)
    }

    @Test fun `formatResult integer`() {
        assertEquals("42", Calculator.formatResult(42.0))
    }

    @Test fun `formatResult decimal`() {
        assertEquals("3.14", Calculator.formatResult(3.14))
    }

    @Test fun `formatResult zero`() {
        assertEquals("0", Calculator.formatResult(0.0))
    }

    @Test fun `evalExpression with variable`() {
        Calculator.setVariable("x", 10.0)
        assertEquals(15.0, Calculator.evalExpression("x + 5"), 0.001)
    }

    @Test fun `evalExpression with assignment`() {
        assertEquals(7.0, Calculator.evalExpression("y=7"), 0.001)
        val y = Calculator.getVariable("y")
        assertEquals(7.0, y ?: 0.0, 0.001)
    }

    @Test fun `getSupportedUnits returns units map`() {
        val units = Calculator.getSupportedUnits()
        assertTrue(units.containsKey("Temperature"))
        assertTrue(units.containsKey("Length"))
        assertTrue(units.containsKey("Weight"))
        assertTrue(units.containsKey("Volume"))
        assertTrue(units.containsKey("Speed"))
    }

    @Test fun `getSupportedDateFunctions returns list`() {
        val funcs = Calculator.getSupportedDateFunctions()
        assertTrue(funcs.isNotEmpty())
        assertTrue(funcs.any { it.contains("today") })
    }

    @Test fun `getSupportedStatFunctions returns list`() {
        val funcs = Calculator.getSupportedStatFunctions()
        assertTrue(funcs.isNotEmpty())
        assertTrue(funcs.any { it.contains("stats.mean") })
    }

    @Test fun `getSupportedJsFeatures returns list`() {
        val features = Calculator.getSupportedJsFeatures()
        assertTrue(features.isNotEmpty())
        assertTrue(features.any { it.contains("Ternary") })
    }

    @Test fun `evalExpression ternary`() {
        assertEquals(10.0, Calculator.evalExpression("1 ? 10 : 20"), 0.001)
    }

    @Test fun `evalExpression comparison`() {
        assertEquals(1.0, Calculator.evalExpression("5 > 3"), 0.001)
    }

    @Test fun `evalExpression logical`() {
        assertEquals(1.0, Calculator.evalExpression("1 && 1"), 0.001)
    }
}
