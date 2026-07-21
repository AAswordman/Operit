package com.ai.assistance.operit.core.tools.calculator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionContextTest {

    @After
    fun tearDown() {
        ExpressionContext.clearVariables()
    }

    @Test fun `getVariable returns set value`() {
        ExpressionContext.setVariable("x", 42.0)
        assertEquals(42.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `setVariable overwrites existing value`() {
        ExpressionContext.setVariable("x", 10.0)
        ExpressionContext.setVariable("x", 20.0)
        assertEquals(20.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `clearVariables resets all variables`() {
        ExpressionContext.setVariable("x", 42.0)
        ExpressionContext.clearVariables()
        // PI and E should still exist
        assertEquals(Math.PI, ExpressionContext.getVariable("PI"), 0.001)
        assertEquals(Math.E, ExpressionContext.getVariable("E"), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getVariable throws for undefined variable`() {
        ExpressionContext.getVariable("nonexistent")
    }

    @Test fun `constant PI is predefined`() {
        assertEquals(Math.PI, ExpressionContext.getVariable("PI"), 0.001)
    }

    @Test fun `constant E is predefined`() {
        assertEquals(Math.E, ExpressionContext.getVariable("E"), 0.001)
    }

    @Test fun `coerceToNumber returns zero for null`() {
        assertEquals(0.0, ExpressionContext.coerceToNumber(null), 0.001)
    }

    @Test fun `coerceToNumber returns double for integer`() {
        assertEquals(5.0, ExpressionContext.coerceToNumber(5), 0.001)
    }

    @Test fun `coerceToNumber returns double for float`() {
        assertEquals(3.14, ExpressionContext.coerceToNumber(3.14f), 0.001)
    }

    @Test fun `coerceToNumber returns one for true`() {
        assertEquals(1.0, ExpressionContext.coerceToNumber(true), 0.001)
    }

    @Test fun `coerceToNumber returns zero for false`() {
        assertEquals(0.0, ExpressionContext.coerceToNumber(false), 0.001)
    }

    @Test fun `coerceToNumber parses numeric string`() {
        assertEquals(42.5, ExpressionContext.coerceToNumber("42.5"), 0.001)
    }

    @Test fun `coerceToNumber returns one for true string`() {
        assertEquals(1.0, ExpressionContext.coerceToNumber("true"), 0.001)
    }

    @Test fun `coerceToNumber returns zero for false string`() {
        assertEquals(0.0, ExpressionContext.coerceToNumber("false"), 0.001)
    }

    @Test fun `coerceToNumber returns zero for null string`() {
        assertEquals(0.0, ExpressionContext.coerceToNumber("null"), 0.001)
    }

    @Test fun `coerceToNumber returns zero for undefined string`() {
        assertEquals(0.0, ExpressionContext.coerceToNumber("undefined"), 0.001)
    }

    @Test fun `coerceToNumber returns nan for invalid string`() {
        assertTrue(ExpressionContext.coerceToNumber("hello").isNaN())
    }

    @Test fun `coerceToNumber returns size for list`() {
        assertEquals(3.0, ExpressionContext.coerceToNumber(listOf(1, 2, 3)), 0.001)
    }

    @Test fun `coerceToNumber returns nan for object`() {
        assertTrue(ExpressionContext.coerceToNumber(mapOf("a" to 1)).isNaN())
    }

    @Test fun `coerceToNumber parses negative numeric string`() {
        assertEquals(-10.0, ExpressionContext.coerceToNumber("-10"), 0.001)
    }

    @Test fun `coerceToNumber parses scientific notation string`() {
        assertEquals(1e10, ExpressionContext.coerceToNumber("1e10"), 0.001)
    }

    @Test fun `coerceToNumber returns infinity for infinity string`() {
        assertTrue(ExpressionContext.coerceToNumber("infinity").isInfinite())
    }

    @Test fun `coerceToNumber returns nan for nan string`() {
        assertTrue(ExpressionContext.coerceToNumber("nan").isNaN())
    }

    @Test fun `abs returns absolute value`() {
        assertEquals(5.0, ExpressionContext.callFunction("abs", listOf(-5.0)), 0.001)
    }

    @Test fun `sqrt returns square root`() {
        assertEquals(3.0, ExpressionContext.callFunction("sqrt", listOf(9.0)), 0.001)
    }

    @Test fun `sqrt returns nan for negative input`() {
        assertTrue(ExpressionContext.callFunction("sqrt", listOf(-1.0)).isNaN())
    }

    @Test fun `sin returns sine`() {
        assertEquals(Math.sin(0.0), ExpressionContext.callFunction("sin", listOf(0.0)), 0.001)
    }

    @Test fun `cos returns cosine`() {
        assertEquals(Math.cos(0.0), ExpressionContext.callFunction("cos", listOf(0.0)), 0.001)
    }

    @Test fun `tan returns tangent`() {
        assertEquals(Math.tan(0.0), ExpressionContext.callFunction("tan", listOf(0.0)), 0.001)
    }

    @Test fun `asin returns arcsine`() {
        assertEquals(Math.asin(1.0), ExpressionContext.callFunction("asin", listOf(1.0)), 0.001)
    }

    @Test fun `acos returns arccosine`() {
        assertEquals(Math.acos(0.0), ExpressionContext.callFunction("acos", listOf(0.0)), 0.001)
    }

    @Test fun `atan returns arctangent`() {
        assertEquals(Math.atan(1.0), ExpressionContext.callFunction("atan", listOf(1.0)), 0.001)
    }

    @Test fun `log returns base-10 logarithm`() {
        assertEquals(Math.log10(100.0), ExpressionContext.callFunction("log", listOf(100.0)), 0.001)
    }

    @Test fun `ln returns natural logarithm`() {
        assertEquals(Math.log(Math.E), ExpressionContext.callFunction("ln", listOf(Math.E)), 0.001)
    }

    @Test fun `round returns nearest integer`() {
        assertEquals(4.0, ExpressionContext.callFunction("round", listOf(3.7)), 0.001)
    }

    @Test fun `round down returns nearest integer`() {
        assertEquals(3.0, ExpressionContext.callFunction("round", listOf(3.2)), 0.001)
    }

    @Test fun `floor returns floor`() {
        assertEquals(3.0, ExpressionContext.callFunction("floor", listOf(3.7)), 0.001)
    }

    @Test fun `ceil returns ceiling`() {
        assertEquals(4.0, ExpressionContext.callFunction("ceil", listOf(3.2)), 0.001)
    }

    @Test fun `pow returns power`() {
        assertEquals(8.0, ExpressionContext.callFunction("pow", listOf(2.0, 3.0)), 0.001)
    }

    @Test fun `max returns maximum`() {
        assertEquals(10.0, ExpressionContext.callFunction("max", listOf(3.0, 10.0, 5.0)), 0.001)
    }

    @Test fun `min returns minimum`() {
        assertEquals(3.0, ExpressionContext.callFunction("min", listOf(3.0, 10.0, 5.0)), 0.001)
    }

    @Test fun `random returns value between zero and one`() {
        val result = ExpressionContext.callFunction("random", listOf())
        assertTrue(result >= 0.0 && result < 1.0)
    }

    @Test fun `factorial of zero`() {
        assertEquals(1.0, ExpressionContext.callFunction("fact", listOf(0.0)), 0.001)
    }

    @Test fun `factorial of one`() {
        assertEquals(1.0, ExpressionContext.callFunction("fact", listOf(1.0)), 0.001)
    }

    @Test fun `factorial of five`() {
        assertEquals(120.0, ExpressionContext.callFunction("fact", listOf(5.0)), 0.001)
    }

    @Test fun `factorial of ten`() {
        assertEquals(3628800.0, ExpressionContext.callFunction("fact", listOf(10.0)), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `factorial of negative throws`() {
        ExpressionContext.callFunction("fact", listOf(-1.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `factorial too large throws`() {
        ExpressionContext.callFunction("fact", listOf(21.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown function throws`() {
        ExpressionContext.callFunction("unknown", listOf(1.0))
    }

    @Test fun `function names are case insensitive`() {
        assertEquals(5.0, ExpressionContext.callFunction("ABS", listOf(-5.0)), 0.001)
        assertEquals(5.0, ExpressionContext.callFunction("Sqrt", listOf(25.0)), 0.001)
    }

    @Test fun `stats mean calculates average`() {
        assertEquals(5.0, ExpressionContext.callFunction("stats.mean", listOf(2.0, 4.0, 6.0, 8.0)), 0.001)
    }

    @Test fun `stats mean of single value`() {
        assertEquals(5.0, ExpressionContext.callFunction("stats.mean", listOf(5.0)), 0.001)
    }

    @Test fun `stats median with odd count`() {
        assertEquals(5.0, ExpressionContext.callFunction("stats.median", listOf(1.0, 5.0, 10.0)), 0.001)
    }

    @Test fun `stats median with even count`() {
        assertEquals(7.5, ExpressionContext.callFunction("stats.median", listOf(5.0, 10.0)), 0.001)
    }

    @Test fun `stats min returns minimum`() {
        assertEquals(1.0, ExpressionContext.callFunction("stats.min", listOf(3.0, 1.0, 5.0)), 0.001)
    }

    @Test fun `stats max returns maximum`() {
        assertEquals(5.0, ExpressionContext.callFunction("stats.max", listOf(3.0, 1.0, 5.0)), 0.001)
    }

    @Test fun `stats sum returns sum`() {
        assertEquals(15.0, ExpressionContext.callFunction("stats.sum", listOf(1.0, 2.0, 3.0, 4.0, 5.0)), 0.001)
    }

    @Test fun `stats min with empty list`() {
        assertEquals(0.0, ExpressionContext.callFunction("stats.min", listOf()), 0.001)
    }

    @Test fun `stats max with empty list`() {
        assertEquals(0.0, ExpressionContext.callFunction("stats.max", listOf()), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `convert with fewer than three args throws`() {
        ExpressionContext.callFunction("convert", listOf(10.0))
    }

    @Test fun `formatResult integer returns without decimal`() {
        assertEquals("42", ExpressionContext.formatResult(42.0))
    }

    @Test fun `formatResult decimal returns with precision`() {
        assertEquals("3.14", ExpressionContext.formatResult(3.14))
    }

    @Test fun `formatResult trailing zeros trimmed`() {
        assertEquals("3.5", ExpressionContext.formatResult(3.500000))
    }

    @Test fun `formatResult zero returns zero`() {
        assertEquals("0", ExpressionContext.formatResult(0.0))
    }

    @Test fun `formatResult negative integer`() {
        assertEquals("-5", ExpressionContext.formatResult(-5.0))
    }

    @Test fun `formatResult negative decimal`() {
        assertEquals("-3.14", ExpressionContext.formatResult(-3.14))
    }

    @Test fun `formatResult nan returns nan`() {
        assertEquals("NaN", ExpressionContext.formatResult(Double.NaN))
    }

    @Test fun `formatResult infinity returns infinity`() {
        assertEquals("Infinity", ExpressionContext.formatResult(Double.POSITIVE_INFINITY))
    }

    @Test fun `formatResult large number`() {
        assertEquals("1000000", ExpressionContext.formatResult(1000000.0))
    }

    @Test fun `formatResult very small decimal`() {
        assertEquals("0.000001", ExpressionContext.formatResult(0.000001))
    }

    @Test fun `getArrayElement with list out of bounds returns nan`() {
        val result = ExpressionContext.getArrayElement(NumberNode(0.0), NumberNode(99.0))
        assertTrue(result.isNaN())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getArrayElement with non array non string throws`() {
        // Set variable to a Double
        ExpressionContext.setVariable("x", 42.0)
        // 42.0 is neither List nor String, so getArrayElement should throw
        ExpressionContext.getArrayElement(VariableNode("x"), NumberNode(0.0))
    }

    @Test fun `coerceToNumber handles true string case insensitive`() {
        assertEquals(1.0, ExpressionContext.coerceToNumber("True"), 0.001)
        assertEquals(1.0, ExpressionContext.coerceToNumber("TRUE"), 0.001)
    }

    @Test fun `coerceToNumber handles false string case insensitive`() {
        assertEquals(0.0, ExpressionContext.coerceToNumber("False"), 0.001)
        assertEquals(0.0, ExpressionContext.coerceToNumber("FALSE"), 0.001)
    }
}
