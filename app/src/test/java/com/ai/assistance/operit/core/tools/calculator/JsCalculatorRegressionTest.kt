package com.ai.assistance.operit.core.tools.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JsCalculator 回归测试。
 *
 * 覆盖表达式求值的核心路径（运算符优先级、括号、幂、三元、变量、内置函数、
 * 统计函数、常量、无穷大与错误输入），防止后续对 core/tools 的重构破坏
 * 计算器工具的基础行为。
 *
 * 注意：JsCalculator / ExpressionContext 均为纯 JVM 实现（无 Android 依赖），
 * 因此可在单元测试中直接运行。
 */
class JsCalculatorRegressionTest {

    @Before
    fun setUp() {
        // ExpressionContext 持有全局可变变量，测试间需重置以避免污染
        JsCalculator.clearVariables()
    }

    @Test
    fun arithmeticPrecedence_multiplicationBeforeAddition() {
        assertEquals("7", JsCalculator.calc("1 + 2 * 3"))
    }

    @Test
    fun parentheses_overridePrecedence() {
        assertEquals("9", JsCalculator.calc("(1 + 2) * 3"))
    }

    @Test
    fun division_producesFractionalResult() {
        assertEquals("2.5", JsCalculator.calc("10 / 4"))
    }

    @Test
    fun power_operator() {
        assertEquals("1024", JsCalculator.calc("2 ^ 10"))
    }

    @Test
    fun ternary_operator_selectsBranch() {
        assertEquals("20", JsCalculator.calc("1 > 2 ? 10 : 20"))
        assertEquals("10", JsCalculator.calc("2 > 1 ? 10 : 20"))
    }

    @Test
    fun variable_assignmentAndReadback() {
        JsCalculator.setVariable("x", 5.0)
        assertEquals("10", JsCalculator.calc("x * 2"))
    }

    @Test
    fun builtinMathFunctions() {
        assertEquals("4", JsCalculator.calc("sqrt(16)"))
        assertEquals("7", JsCalculator.calc("abs(-7)"))
        assertEquals("9", JsCalculator.calc("max(3, 9, 4)"))
        assertEquals("3", JsCalculator.calc("min(3, 9, 4)"))
        assertEquals("3", JsCalculator.calc("round(2.6)"))
    }

    @Test
    fun statsMean_function() {
        assertEquals("2.5", JsCalculator.calc("stats.mean(1, 2, 3, 4)"))
    }

    @Test
    fun constants_piIsAvailable() {
        assertTrue(JsCalculator.calc("PI").startsWith("3.14159"))
    }

    @Test
    fun divisionByZero_yieldsInfinity() {
        assertEquals("Infinity", JsCalculator.calc("1 / 0"))
    }

    @Test
    fun malformedExpression_throws() {
        assertThrows(RuntimeException::class.java) {
            JsCalculator.evaluate("1 +")
        }
    }

    @Test
    fun evaluateReturnsDouble_forArithmetic() {
        assertEquals(7.0, JsCalculator.evaluate("1 + 2 * 3"), 0.0)
    }
}
