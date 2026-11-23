package com.ai.assistance.operit.core.tools.calculator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `convert(value, fromUnit, toUnit)` 是计算器对外公布的换算能力
 * （见 [JsCalculator.getSupportedUnits]），必须能从公开入口 [JsCalculator.evaluate] 执行。
 *
 * 修复前解析器把单位和数值一起吞掉，只留下第一个实参，运行到求值阶段恒定抛出
 * “convert requires 3 parameters”，任何换算都拿不到结果。
 */
class UnitConversionTest {

    @After
    fun tearDown() {
        JsCalculator.clearVariables()
    }

    @Test
    fun `celsius to fahrenheit`() {
        assertEquals(212.0, JsCalculator.evaluate("convert(100, \"c\", \"f\")"), 0.001)
    }

    @Test
    fun `fahrenheit to celsius`() {
        assertEquals(0.0, JsCalculator.evaluate("convert(32, \"f\", \"c\")"), 0.001)
    }

    @Test
    fun `celsius to kelvin`() {
        assertEquals(373.15, JsCalculator.evaluate("convert(100, \"c\", \"k\")"), 0.001)
    }

    @Test
    fun `kilometers to miles`() {
        assertEquals(0.621371, JsCalculator.evaluate("convert(1, \"km\", \"mi\")"), 0.001)
    }

    @Test
    fun `kilograms to pounds`() {
        assertEquals(2.20462, JsCalculator.evaluate("convert(1, \"kg\", \"lb\")"), 0.001)
    }

    @Test
    fun `units may be bare names without quotes`() {
        assertEquals(212.0, JsCalculator.evaluate("convert(100, c, f)"), 0.001)
    }

    @Test
    fun `value argument is evaluated as an expression`() {
        assertEquals(212.0, JsCalculator.evaluate("convert(50 * 2, \"c\", \"f\")"), 0.001)
    }

    @Test
    fun `same unit returns the value unchanged`() {
        assertEquals(7.5, JsCalculator.evaluate("convert(7.5, \"km\", \"km\")"), 0.001)
    }

    @Test
    fun `unsupported conversion reports the unit pair`() {
        val error = runCatching { JsCalculator.evaluate("convert(1, \"kg\", \"zz\")") }
            .exceptionOrNull()

        val message = generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" | ")

        assertEquals(true, message.contains("Unsupported conversion: kg to zz"))
    }

    @Test
    fun `conversion leaves no bookkeeping variables in the calculator state`() {
        JsCalculator.evaluate("convert(100, \"c\", \"f\")")

        val leaked = runCatching { JsCalculator.getVariable("_convert_from") }.isSuccess

        assertEquals(false, leaked)
    }
}
