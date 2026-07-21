package com.ai.assistance.operit.core.tools.calculator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionNodeTest {

    @After
    fun tearDown() {
        ExpressionContext.clearVariables()
    }

    @Test fun `number node evaluates to its value`() {
        assertEquals(42.0, NumberNode(42.0).evaluate(), 0.001)
    }

    @Test fun `number node evaluates to zero`() {
        assertEquals(0.0, NumberNode(0.0).evaluate(), 0.001)
    }

    @Test fun `number node evaluates to negative value`() {
        assertEquals(-3.14, NumberNode(-3.14).evaluate(), 0.001)
    }

    @Test fun `number node evaluates to large value`() {
        assertEquals(1e15, NumberNode(1e15).evaluate(), 0.001)
    }

    @Test fun `variable node evaluates to set value`() {
        ExpressionContext.setVariable("x", 10.0)
        assertEquals(10.0, VariableNode("x").evaluate(), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `variable node throws for undefined variable`() {
        VariableNode("undefined").evaluate()
    }

    @Test fun `binary addition evaluates correctly`() {
        val node = BinaryOperationNode(NumberNode(3.0), "+", NumberNode(4.0))
        assertEquals(7.0, node.evaluate(), 0.001)
    }

    @Test fun `binary subtraction evaluates correctly`() {
        val node = BinaryOperationNode(NumberNode(10.0), "-", NumberNode(3.0))
        assertEquals(7.0, node.evaluate(), 0.001)
    }

    @Test fun `binary multiplication evaluates correctly`() {
        val node = BinaryOperationNode(NumberNode(6.0), "*", NumberNode(7.0))
        assertEquals(42.0, node.evaluate(), 0.001)
    }

    @Test fun `binary division evaluates correctly`() {
        val node = BinaryOperationNode(NumberNode(10.0), "/", NumberNode(2.0))
        assertEquals(5.0, node.evaluate(), 0.001)
    }

    @Test fun `binary division by zero returns infinity`() {
        val node = BinaryOperationNode(NumberNode(1.0), "/", NumberNode(0.0))
        assertTrue(node.evaluate().isInfinite())
    }

    @Test fun `binary modulo evaluates correctly`() {
        val node = BinaryOperationNode(NumberNode(10.0), "%", NumberNode(3.0))
        assertEquals(1.0, node.evaluate(), 0.001)
    }

    @Test fun `binary exponent with asterisk evaluates correctly`() {
        val node = BinaryOperationNode(NumberNode(2.0), "**", NumberNode(3.0))
        assertEquals(8.0, node.evaluate(), 0.001)
    }

    @Test fun `binary exponent with caret evaluates correctly`() {
        val node = BinaryOperationNode(NumberNode(2.0), "^", NumberNode(3.0))
        assertEquals(8.0, node.evaluate(), 0.001)
    }

    @Test fun `binary equals returns one for equal values`() {
        val node = BinaryOperationNode(NumberNode(5.0), "==", NumberNode(5.0))
        assertEquals(1.0, node.evaluate(), 0.001)
    }

    @Test fun `binary equals returns zero for unequal values`() {
        val node = BinaryOperationNode(NumberNode(5.0), "==", NumberNode(3.0))
        assertEquals(0.0, node.evaluate(), 0.001)
    }

    @Test fun `binary not equals returns one for unequal values`() {
        val node = BinaryOperationNode(NumberNode(5.0), "!=", NumberNode(3.0))
        assertEquals(1.0, node.evaluate(), 0.001)
    }

    @Test fun `binary not equals returns zero for equal values`() {
        val node = BinaryOperationNode(NumberNode(5.0), "!=", NumberNode(5.0))
        assertEquals(0.0, node.evaluate(), 0.001)
    }

    @Test fun `binary greater than evaluates correctly`() {
        val node = BinaryOperationNode(NumberNode(5.0), ">", NumberNode(3.0))
        assertEquals(1.0, node.evaluate(), 0.001)
    }

    @Test fun `binary greater than returns zero when false`() {
        val node = BinaryOperationNode(NumberNode(3.0), ">", NumberNode(5.0))
        assertEquals(0.0, node.evaluate(), 0.001)
    }

    @Test fun `binary greater or equal evaluates correctly`() {
        val node = BinaryOperationNode(NumberNode(5.0), ">=", NumberNode(5.0))
        assertEquals(1.0, node.evaluate(), 0.001)
    }

    @Test fun `binary less than evaluates correctly`() {
        val node = BinaryOperationNode(NumberNode(3.0), "<", NumberNode(5.0))
        assertEquals(1.0, node.evaluate(), 0.001)
    }

    @Test fun `binary less or equal evaluates correctly`() {
        val node = BinaryOperationNode(NumberNode(5.0), "<=", NumberNode(5.0))
        assertEquals(1.0, node.evaluate(), 0.001)
    }

    @Test fun `binary logical and returns one when both truthy`() {
        val node = BinaryOperationNode(NumberNode(1.0), "&&", NumberNode(2.0))
        assertEquals(1.0, node.evaluate(), 0.001)
    }

    @Test fun `binary logical and returns zero when left falsy`() {
        val node = BinaryOperationNode(NumberNode(0.0), "&&", NumberNode(2.0))
        assertEquals(0.0, node.evaluate(), 0.001)
    }

    @Test fun `binary logical or returns one when left truthy`() {
        val node = BinaryOperationNode(NumberNode(1.0), "||", NumberNode(0.0))
        assertEquals(1.0, node.evaluate(), 0.001)
    }

    @Test fun `binary logical or returns zero when both falsy`() {
        val node = BinaryOperationNode(NumberNode(0.0), "||", NumberNode(0.0))
        assertEquals(0.0, node.evaluate(), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `binary unknown operator throws`() {
        BinaryOperationNode(NumberNode(1.0), "@@", NumberNode(2.0)).evaluate()
    }

    @Test fun `unary plus returns same value`() {
        val node = UnaryOperationNode("+", NumberNode(5.0))
        assertEquals(5.0, node.evaluate(), 0.001)
    }

    @Test fun `unary minus negates value`() {
        val node = UnaryOperationNode("-", NumberNode(5.0))
        assertEquals(-5.0, node.evaluate(), 0.001)
    }

    @Test fun `unary double negation`() {
        val node = UnaryOperationNode("-", UnaryOperationNode("-", NumberNode(5.0)))
        assertEquals(5.0, node.evaluate(), 0.001)
    }

    @Test fun `unary not returns one when operand is zero`() {
        val node = UnaryOperationNode("!", NumberNode(0.0))
        assertEquals(1.0, node.evaluate(), 0.001)
    }

    @Test fun `unary not returns zero when operand is non-zero`() {
        val node = UnaryOperationNode("!", NumberNode(1.0))
        assertEquals(0.0, node.evaluate(), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unary unknown operator throws`() {
        UnaryOperationNode("~", NumberNode(5.0)).evaluate()
    }

    @Test fun `ternary returns true branch when condition truthy`() {
        val node = TernaryOperationNode(NumberNode(1.0), NumberNode(10.0), NumberNode(20.0))
        assertEquals(10.0, node.evaluate(), 0.001)
    }

    @Test fun `ternary returns false branch when condition falsy`() {
        val node = TernaryOperationNode(NumberNode(0.0), NumberNode(10.0), NumberNode(20.0))
        assertEquals(20.0, node.evaluate(), 0.001)
    }

    @Test fun `ternary with nested condition`() {
        ExpressionContext.setVariable("a", 5.0)
        val cond = BinaryOperationNode(VariableNode("a"), ">", NumberNode(3.0))
        val node = TernaryOperationNode(cond, NumberNode(1.0), NumberNode(0.0))
        assertEquals(1.0, node.evaluate(), 0.001)
    }

    @Test fun `function call node evaluates via context`() {
        val node = FunctionCallNode("abs", listOf(NumberNode(-5.0)))
        assertEquals(5.0, node.evaluate(), 0.001)
    }

    @Test fun `function call with two arguments`() {
        val node = FunctionCallNode("pow", listOf(NumberNode(2.0), NumberNode(3.0)))
        assertEquals(8.0, node.evaluate(), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `function call unknown function throws`() {
        FunctionCallNode("nonexistent", listOf(NumberNode(1.0))).evaluate()
    }

    @Test fun `assignment node sets variable and returns value`() {
        val node = AssignmentNode("x", NumberNode(42.0))
        assertEquals(42.0, node.evaluate(), 0.001)
        assertEquals(42.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `assignment node overwrites existing variable`() {
        ExpressionContext.setVariable("x", 10.0)
        AssignmentNode("x", NumberNode(99.0)).evaluate()
        assertEquals(99.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `compound assignment add evaluates correctly`() {
        ExpressionContext.setVariable("x", 10.0)
        val node = CompoundAssignmentNode("x", "+=", NumberNode(5.0))
        assertEquals(15.0, node.evaluate(), 0.001)
        assertEquals(15.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `compound assignment subtract evaluates correctly`() {
        ExpressionContext.setVariable("x", 10.0)
        val node = CompoundAssignmentNode("x", "-=", NumberNode(3.0))
        assertEquals(7.0, node.evaluate(), 0.001)
        assertEquals(7.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `compound assignment multiply evaluates correctly`() {
        ExpressionContext.setVariable("x", 10.0)
        val node = CompoundAssignmentNode("x", "*=", NumberNode(2.0))
        assertEquals(20.0, node.evaluate(), 0.001)
        assertEquals(20.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `compound assignment divide evaluates correctly`() {
        ExpressionContext.setVariable("x", 10.0)
        val node = CompoundAssignmentNode("x", "/=", NumberNode(2.0))
        assertEquals(5.0, node.evaluate(), 0.001)
        assertEquals(5.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `compound assignment unknown operator throws`() {
        ExpressionContext.setVariable("x", 10.0)
        CompoundAssignmentNode("x", "%=", NumberNode(2.0)).evaluate()
    }

    @Test fun `array access node with number node`() {
        val node = ArrayAccessNode(NumberNode(3.0), NumberNode(0.0))
        assertEquals('3'.code.toDouble(), node.evaluate(), 0.001)
    }

    @Test fun `template string with no expressions evaluates to double`() {
        val node = TemplateStringNode(listOf("42"))
        assertEquals(42.0, node.evaluate(), 0.001)
    }

    @Test fun `template string non-numeric returns nan`() {
        val node = TemplateStringNode(listOf("hello"))
        assertTrue(node.evaluate().isNaN())
    }

    @Test fun `template string with expression placeholder`() {
        val node = TemplateStringNode(listOf("result: ", NumberNode(42.0)))
        // "result: 42.0" cannot be parsed as double -> NaN
        assertTrue(node.evaluate().isNaN())
    }

    @Test fun `template string empty parts returns nan`() {
        val node = TemplateStringNode(listOf(""))
        assertTrue(node.evaluate().isNaN())
    }

    @Test fun `template string with only number returns value`() {
        val node = TemplateStringNode(listOf("3.14"))
        assertEquals(3.14, node.evaluate(), 0.001)
    }
}
