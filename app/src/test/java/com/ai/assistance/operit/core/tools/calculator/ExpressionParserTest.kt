package com.ai.assistance.operit.core.tools.calculator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionParserTest {

    @After
    fun tearDown() {
        ExpressionContext.clearVariables()
    }

    private fun parseAndEval(expression: String): Double {
        val parser = ExpressionParser(expression)
        return parser.parse().evaluate()
    }

    private fun parse(expression: String): ExpressionNode {
        return ExpressionParser(expression).parse()
    }

    @Test fun `simple number`() {
        assertEquals(42.0, parseAndEval("42"), 0.001)
    }

    @Test fun `negative number`() {
        assertEquals(-10.0, parseAndEval("-10"), 0.001)
    }

    @Test fun `decimal number`() {
        assertEquals(3.14, parseAndEval("3.14"), 0.001)
    }

    @Test fun `number with leading decimal`() {
        assertEquals(0.5, parseAndEval(".5"), 0.001)
    }

    @Test fun `zero`() {
        assertEquals(0.0, parseAndEval("0"), 0.001)
    }

    @Test fun `addition`() {
        assertEquals(5.0, parseAndEval("2 + 3"), 0.001)
    }

    @Test fun `subtraction`() {
        assertEquals(3.0, parseAndEval("7 - 4"), 0.001)
    }

    @Test fun `multiplication`() {
        assertEquals(12.0, parseAndEval("3 * 4"), 0.001)
    }

    @Test fun `division`() {
        assertEquals(3.0, parseAndEval("9 / 3"), 0.001)
    }

    @Test fun `modulo`() {
        assertEquals(1.0, parseAndEval("10 % 3"), 0.001)
    }

    @Test fun `exponentiation double asterisk`() {
        assertEquals(8.0, parseAndEval("2 ** 3"), 0.001)
    }

    @Test fun `exponentiation caret`() {
        assertEquals(8.0, parseAndEval("2 ^ 3"), 0.001)
    }

    @Test fun `operator precedence multiplication before addition`() {
        assertEquals(14.0, parseAndEval("2 + 3 * 4"), 0.001)
    }

    @Test fun `operator precedence exponent before multiplication`() {
        assertEquals(12.0, parseAndEval("3 * 2 ** 2"), 0.001)
    }

    @Test fun `parentheses override precedence`() {
        assertEquals(20.0, parseAndEval("(2 + 3) * 4"), 0.001)
    }

    @Test fun `nested parentheses`() {
        assertEquals(14.0, parseAndEval("((2 + 3) * 4) - 6"), 0.001)
    }

    @Test fun `chained addition`() {
        assertEquals(15.0, parseAndEval("1 + 2 + 3 + 4 + 5"), 0.001)
    }

    @Test fun `chained multiplication`() {
        assertEquals(24.0, parseAndEval("2 * 3 * 4"), 0.001)
    }

    @Test fun `unary plus`() {
        assertEquals(5.0, parseAndEval("+5"), 0.001)
    }

    @Test fun `unary minus`() {
        assertEquals(-5.0, parseAndEval("-5"), 0.001)
    }

    @Test fun `double unary minus`() {
        assertEquals(5.0, parseAndEval("--5"), 0.001)
    }

    @Test fun `unary not`() {
        assertEquals(1.0, parseAndEval("!0"), 0.001)
    }

    @Test fun `unary not with non-zero`() {
        assertEquals(0.0, parseAndEval("!5"), 0.001)
    }

    @Test fun `comparison equals true`() {
        assertEquals(1.0, parseAndEval("5 == 5"), 0.001)
    }

    @Test fun `comparison equals false`() {
        assertEquals(0.0, parseAndEval("5 == 3"), 0.001)
    }

    @Test fun `comparison not equals true`() {
        assertEquals(1.0, parseAndEval("5 != 3"), 0.001)
    }

    @Test fun `comparison not equals false`() {
        assertEquals(0.0, parseAndEval("5 != 5"), 0.001)
    }

    @Test fun `comparison greater than true`() {
        assertEquals(1.0, parseAndEval("5 > 3"), 0.001)
    }

    @Test fun `comparison greater than false`() {
        assertEquals(0.0, parseAndEval("3 > 5"), 0.001)
    }

    @Test fun `comparison greater or equal true`() {
        assertEquals(1.0, parseAndEval("5 >= 5"), 0.001)
    }

    @Test fun `comparison less than true`() {
        assertEquals(1.0, parseAndEval("3 < 5"), 0.001)
    }

    @Test fun `comparison less or equal true`() {
        assertEquals(1.0, parseAndEval("5 <= 5"), 0.001)
    }

    @Test fun `logical and both true`() {
        assertEquals(1.0, parseAndEval("1 && 2"), 0.001)
    }

    @Test fun `logical and one false`() {
        assertEquals(0.0, parseAndEval("1 && 0"), 0.001)
    }

    @Test fun `logical or one true`() {
        assertEquals(1.0, parseAndEval("1 || 0"), 0.001)
    }

    @Test fun `logical or both false`() {
        assertEquals(0.0, parseAndEval("0 || 0"), 0.001)
    }

    @Test fun `ternary operator true condition`() {
        assertEquals(10.0, parseAndEval("1 ? 10 : 20"), 0.001)
    }

    @Test fun `ternary operator false condition`() {
        assertEquals(20.0, parseAndEval("0 ? 10 : 20"), 0.001)
    }

    @Test fun `ternary with comparison condition`() {
        assertEquals(1.0, parseAndEval("(5 > 3) ? 1 : 0"), 0.001)
    }

    @Test fun `variable assignment`() {
        assertEquals(42.0, parseAndEval("x=42"), 0.001)
        assertEquals(42.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `variable usage after assignment`() {
        parseAndEval("x=10")
        assertEquals(20.0, parseAndEval("x + 10"), 0.001)
    }

    @Test fun `compound assignment add`() {
        parseAndEval("x=10")
        assertEquals(15.0, parseAndEval("x+=5"), 0.001)
        assertEquals(15.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `compound assignment subtract`() {
        parseAndEval("x=10")
        assertEquals(4.0, parseAndEval("x-=6"), 0.001)
        assertEquals(4.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `compound assignment multiply`() {
        parseAndEval("x=10")
        assertEquals(20.0, parseAndEval("x*=2"), 0.001)
        assertEquals(20.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `compound assignment divide`() {
        parseAndEval("x=10")
        assertEquals(2.0, parseAndEval("x/=5"), 0.001)
        assertEquals(2.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `function call abs`() {
        assertEquals(5.0, parseAndEval("abs(-5)"), 0.001)
    }

    @Test fun `function call sqrt`() {
        assertEquals(3.0, parseAndEval("sqrt(9)"), 0.001)
    }

    @Test fun `function call sin`() {
        assertEquals(Math.sin(0.0), parseAndEval("sin(0)"), 0.001)
    }

    @Test fun `function call cos`() {
        assertEquals(Math.cos(0.0), parseAndEval("cos(0)"), 0.001)
    }

    @Test fun `function call tan`() {
        assertEquals(Math.tan(0.0), parseAndEval("tan(0)"), 0.001)
    }

    @Test fun `function call round`() {
        assertEquals(4.0, parseAndEval("round(3.7)"), 0.001)
    }

    @Test fun `function call floor`() {
        assertEquals(3.0, parseAndEval("floor(3.7)"), 0.001)
    }

    @Test fun `function call ceil`() {
        assertEquals(4.0, parseAndEval("ceil(3.2)"), 0.001)
    }

    @Test fun `function call pow`() {
        assertEquals(8.0, parseAndEval("pow(2, 3)"), 0.001)
    }

    @Test fun `function call max`() {
        assertEquals(10.0, parseAndEval("max(3, 10, 5)"), 0.001)
    }

    @Test fun `function call min`() {
        assertEquals(3.0, parseAndEval("min(3, 10, 5)"), 0.001)
    }

    @Test fun `function call factorial`() {
        assertEquals(120.0, parseAndEval("fact(5)"), 0.001)
    }

    @Test fun `function call log`() {
        assertEquals(Math.log10(100.0), parseAndEval("log(100)"), 0.001)
    }

    @Test fun `function call ln`() {
        assertEquals(Math.log(Math.E), parseAndEval("ln(E)"), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `math function call`() {
        parseAndEval("Math.sin(0)")
    }

    @Test fun `whitespace does not affect result`() {
        assertEquals(10.0, parseAndEval("   2   +   8   "), 0.001)
    }

    @Test fun `newline is whitespace`() {
        assertEquals(10.0, parseAndEval("2\n+\n8"), 0.001)
    }

    @Test fun `tab is whitespace`() {
        assertEquals(10.0, parseAndEval("2\t+\t8"), 0.001)
    }

    @Test fun `variable with underscore`() {
        parseAndEval("my_var=42")
        assertEquals(42.0, parseAndEval("my_var"), 0.001)
    }

    @Test fun `variable with mixed case`() {
        parseAndEval("myVar=10")
        assertEquals(10.0, parseAndEval("myVar"), 0.001)
    }

    @Test fun `identifier with digits`() {
        parseAndEval("x2=5")
        assertEquals(5.0, parseAndEval("x2"), 0.001)
    }

    @Test fun `string literal as variable name`() {
        // String literals are parsed as VariableNode with the string value
        val node = parse("'hello'")
        assertTrue(node is VariableNode)
        assertEquals("'hello'", (node as VariableNode).name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `array literal creation`() {
        parseAndEval("[1, 2, 3]")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unexpected character throws`() {
        parseAndEval("2 @ 3")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unclosed parenthesis throws`() {
        parseAndEval("(2 + 3")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unexpected closing parenthesis throws`() {
        parseAndEval("2 + 3)")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing ternary colon throws`() {
        parseAndEval("1 ? 10")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `comma outside function call throws`() {
        parseAndEval("1, 2")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unexpected token after expression throws`() {
        parseAndEval("2 3")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stats dot syntax not supported by parser`() {
        parseAndEval("stats.mean(2, 4, 6, 8)")
    }

    @Test fun `variable reuse after assignment`() {
        parseAndEval("x=5")
        parseAndEval("x=x + 3")
        assertEquals(8.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `expression with multiple operators`() {
        assertEquals(11.0, parseAndEval("1 + 2 * 3 + 4"), 0.001)
    }

    @Test fun `expression with all arithmetic operators`() {
        assertEquals(10.0, parseAndEval("2 * 3 + 10 / 2 - 1"), 0.001)
    }

    @Test fun `division resulting in decimal`() {
        assertEquals(2.5, parseAndEval("5 / 2"), 0.001)
    }

    @Test fun `negative result`() {
        assertEquals(-1.0, parseAndEval("2 - 3"), 0.001)
    }

    @Test fun `expression with parentheses`() {
        assertEquals(25.0, parseAndEval("(2 + 3) * (1 + 4)"), 0.001)
    }

    @Test fun `deeply nested parentheses`() {
        assertEquals(10.0, parseAndEval("((((1 + 2)) * 3) + 1)"), 0.001)
    }

    @Test fun `unary minus with parentheses`() {
        assertEquals(-10.0, parseAndEval("-(5 + 5)"), 0.001)
    }

    @Test fun `chained comparison`() {
        assertEquals(1.0, parseAndEval("5 > 3 && 10 > 2"), 0.001)
    }

    @Test fun `chained comparison with or`() {
        assertEquals(1.0, parseAndEval("5 < 3 || 10 > 2"), 0.001)
    }

    @Test fun `ternary inside expression`() {
        assertEquals(15.0, parseAndEval("10 + (1 ? 5 : 0)"), 0.001)
    }

    @Test fun `signed zero`() {
        assertEquals(0.0, parseAndEval("-0"), 0.001)
    }

    @Test fun `function call with nested expression arg`() {
        assertEquals(5.0, parseAndEval("abs(2 - 7)"), 0.001)
    }

    @Test fun `pow with negative exponent via division`() {
        assertEquals(0.25, parseAndEval("2 ** -2"), 0.001)
    }

    @Test fun `multiple function calls in expression`() {
        assertEquals(5.0, parseAndEval("min(10, max(1, 5))"), 0.001)
    }

    @Test fun `exponentiation with parenthesis`() {
        assertEquals(512.0, parseAndEval("2 ** (3 + 6)"), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unclosed bracket throws`() {
        parseAndEval("[1, 2")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unclosed function call throws`() {
        parseAndEval("abs(5")
    }

    @Test fun `ternary with comparison`() {
        assertEquals(100.0, parseAndEval("(10 > 5) ? 100 : 200"), 0.001)
    }

    @Test fun `ternary false with comparison`() {
        assertEquals(200.0, parseAndEval("(10 < 5) ? 100 : 200"), 0.001)
    }
}
