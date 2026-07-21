package com.ai.assistance.operit.core.tools.calculator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionParserEdgeCaseTest {

    @After
    fun tearDown() {
        ExpressionContext.clearVariables()
    }

    private fun eval(expr: String): Double = ExpressionParser(expr).parse().evaluate()

    @Test fun `division by zero returns infinity`() {
        assertTrue(eval("1/0").isInfinite())
    }

    @Test fun `zero divided by zero returns nan`() {
        assertTrue(eval("0/0").isNaN())
    }

    @Test fun `negative number squared`() {
        assertEquals(9.0, eval("(-3) ** 2"), 0.001)
    }

    @Test fun `negative number times negative`() {
        assertEquals(4.0, eval("-2 * -2"), 0.001)
    }

    @Test fun `chained exponentiation`() {
        assertEquals(512.0, eval("2 ** (3 + 6)"), 0.001)
    }

    @Test fun `nested ternary`() {
        assertEquals(3.0, eval("1 ? (2 ? 3 : 4) : 5"), 0.001)
    }

    @Test fun `ternary with arithmetic in branches`() {
        assertEquals(15.0, eval("1 ? (5 + 10) : 0"), 0.001)
    }

    @Test fun `comparison chained with and`() {
        assertEquals(1.0, eval("1 < 2 && 2 < 3"), 0.001)
    }

    @Test fun `comparison chained with or`() {
        assertEquals(1.0, eval("1 > 2 || 2 < 3"), 0.001)
    }

    @Test fun `mixed logical operators`() {
        assertEquals(1.0, eval("(1 && 1) || (0 && 1)"), 0.001)
    }

    @Test fun `unary not with comparison`() {
        assertEquals(1.0, eval("!(5 > 10)"), 0.001)
    }

    @Test fun `unary not with false comparison`() {
        assertEquals(0.0, eval("!(5 > 3)"), 0.001)
    }

    @Test fun `variable assignment in expression`() {
        eval("a=5")
        eval("b=10")
        assertEquals(15.0, eval("a + b"), 0.001)
    }

    @Test fun `computed variable assignment`() {
        assertEquals(15.0, eval("x=5 + 10"), 0.001)
        assertEquals(15.0, eval("x"), 0.001)
    }

    @Test fun `compound assignment chain`() {
        eval("x=10")
        eval("x+=5")
        eval("x*=2")
        assertEquals(30.0, eval("x"), 0.001)
    }

    @Test fun `pow with zero exponent`() {
        assertEquals(1.0, eval("2 ** 0"), 0.001)
    }

    @Test fun `pow with exponent of one`() {
        assertEquals(5.0, eval("5 ** 1"), 0.001)
    }

    @Test fun `sqrt of perfect square`() {
        assertEquals(5.0, eval("sqrt(25)"), 0.001)
    }

    @Test fun `abs of positive number`() {
        assertEquals(10.0, eval("abs(10)"), 0.001)
    }

    @Test fun `round half up`() {
        assertEquals(4.0, eval("round(3.5)"), 0.001)
    }

    @Test fun `floor of decimal`() {
        assertEquals(3.0, eval("floor(3.9)"), 0.001)
    }

    @Test fun `ceil of decimal`() {
        assertEquals(4.0, eval("ceil(3.1)"), 0.001)
    }

    @Test fun `max with three arguments`() {
        assertEquals(100.0, eval("max(10, 50, 100)"), 0.001)
    }

    @Test fun `min with three arguments`() {
        assertEquals(10.0, eval("min(10, 50, 100)"), 0.001)
    }

    @Test fun `percent sign as modulo`() {
        assertEquals(2.0, eval("14 % 3"), 0.001)
    }

    @Test fun `percent with negative numbers`() {
        assertEquals(-1.0, eval("-10 % 3"), 0.001)
    }

    @Test fun `addition of multiple numbers`() {
        assertEquals(10.0, eval("1 + 2 + 3 + 4"), 0.001)
    }

    @Test fun `multiplication chain`() {
        assertEquals(24.0, eval("2 * 3 * 4"), 0.001)
    }

    @Test fun `complex expression`() {
        assertEquals(3.5, eval("(2 + 5) / 2"), 0.001)
    }

    @Test fun `expression with all operators`() {
        assertEquals(30.0, eval("10 + 2 * 3 ** 2 - 4 / 2 + (5 - 1)"), 0.001)
    }

    @Test fun `chained subtraction`() {
        assertEquals(-4.0, eval("5 - 3 - 6"), 0.001)
    }

    @Test fun `unary minus with negative result`() {
        assertEquals(-10.0, eval("-(5 + 5)"), 0.001)
    }

    @Test fun `string index access as variable`() {
        // String literals become VariableNode
        val node = ExpressionParser("'hello'").parse()
        assertTrue(node is VariableNode)
        assertEquals("'hello'", (node as VariableNode).name)
    }

    @Test fun `nested function calls`() {
        assertEquals(5.0, eval("max(1, min(10, 5))"), 0.001)
    }

    @Test fun `function call with parentheses in args`() {
        assertEquals(10.0, eval("max((1 + 2) * 2, 10)"), 0.001)
    }

    @Test fun `ternary with comparison in condition`() {
        assertEquals(1.0, eval("(2 + 3 == 5) ? 1 : 0"), 0.001)
    }

    @Test fun `ternary false case`() {
        assertEquals(0.0, eval("(2 + 3 == 6) ? 1 : 0"), 0.001)
    }

    @Test fun `comparison equality with different types`() {
        // 5.0 == 5.0 should be 1.0
        assertEquals(1.0, eval("(2.5 + 2.5) == 5.0"), 0.001)
    }

    @Test fun `negative exponentiation`() {
        assertEquals(0.0625, eval("4 ** -2"), 0.001)
    }

    @Test fun `fractional exponent`() {
        assertEquals(2.0, eval("4 ** 0.5"), 0.001)
    }

    @Test fun `modulo preserves sign of dividend`() {
        assertEquals(-1.0, eval("-10 % 3"), 0.001)
    }

    @Test fun `modulo with decimal`() {
        assertEquals(1.5, eval("5.5 % 2"), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty expression throws`() {
        eval("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `just whitespace throws`() {
        eval("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid character throws`() {
        eval("2 # 3")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing operand throws`() {
        eval("2 +")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unclosed template string throws`() {
        val parser = ExpressionParser("`hello")
        parser.parse()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid function call missing paren`() {
        eval("abs")
    }

    @Test fun `modulo of zero`() {
        assertEquals(0.0, eval("0 % 5"), 0.001)
    }

    @Test fun `addition of large numbers`() {
        assertEquals(2000000.0, eval("1000000 + 1000000"), 0.001)
    }

    @Test fun `multiplication by zero`() {
        assertEquals(0.0, eval("999 * 0"), 0.001)
    }

    @Test fun `not equals comparison`() {
        assertEquals(1.0, eval("5 != 3"), 0.001)
    }

    @Test fun `great or equal true when equal`() {
        assertEquals(1.0, eval("5 >= 5"), 0.001)
    }

    @Test fun `great or equal true when greater`() {
        assertEquals(1.0, eval("6 >= 5"), 0.001)
    }

    @Test fun `less or equal true when equal`() {
        assertEquals(1.0, eval("5 <= 5"), 0.001)
    }

    @Test fun `less or equal true when less`() {
        assertEquals(1.0, eval("4 <= 5"), 0.001)
    }
}
