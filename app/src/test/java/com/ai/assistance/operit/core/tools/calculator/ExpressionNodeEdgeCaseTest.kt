package com.ai.assistance.operit.core.tools.calculator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionNodeEdgeCaseTest {

    @After
    fun tearDown() {
        ExpressionContext.clearVariables()
    }

    @Test fun `number node with large double`() {
        assertEquals(1e308, NumberNode(1e308).evaluate(), 1e293)
    }

    @Test fun `number node with small double`() {
        assertEquals(1e-308, NumberNode(1e-308).evaluate(), 1e-293)
    }

    @Test fun `binary operation with NaN left`() {
        val result = BinaryOperationNode(NumberNode(Double.NaN), "+", NumberNode(1.0)).evaluate()
        assertTrue(result.isNaN())
    }

    @Test fun `binary operation with infinity`() {
        val result = BinaryOperationNode(NumberNode(Double.POSITIVE_INFINITY), "+", NumberNode(1.0)).evaluate()
        assertTrue(result.isInfinite())
    }

    @Test fun `unary minus on zero`() {
        assertEquals(0.0, UnaryOperationNode("-", NumberNode(0.0)).evaluate(), 0.001)
    }

    @Test fun `unary minus on negative zero`() {
        assertEquals(0.0, UnaryOperationNode("-", NumberNode(-0.0)).evaluate(), 0.001)
    }

    @Test fun `ternary with nested ternary true case`() {
        val outer = TernaryOperationNode(
            NumberNode(1.0),
            TernaryOperationNode(NumberNode(1.0), NumberNode(10.0), NumberNode(20.0)),
            NumberNode(30.0),
        )
        assertEquals(10.0, outer.evaluate(), 0.001)
    }

    @Test fun `ternary with nested ternary false outer`() {
        val outer = TernaryOperationNode(
            NumberNode(0.0),
            NumberNode(10.0),
            TernaryOperationNode(NumberNode(1.0), NumberNode(20.0), NumberNode(30.0)),
        )
        assertEquals(20.0, outer.evaluate(), 0.001)
    }

    @Test fun `compound assignment with chained operations`() {
        ExpressionContext.setVariable("x", 10.0)
        CompoundAssignmentNode("x", "+=", NumberNode(5.0)).evaluate()
        CompoundAssignmentNode("x", "*=", NumberNode(2.0)).evaluate()
        assertEquals(30.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `assignment then compound on same variable`() {
        AssignmentNode("y", NumberNode(5.0)).evaluate()
        CompoundAssignmentNode("y", "+=", NumberNode(3.0)).evaluate()
        assertEquals(8.0, ExpressionContext.getVariable("y"), 0.001)
    }

    @Test fun `array access with negative index returns NaN`() {
        val result = ExpressionContext.getArrayElement(NumberNode(0.0), NumberNode(-1.0))
        assertTrue(result.isNaN())
    }

    @Test fun `template string with expression evaluating to number`() {
        val node = TemplateStringNode(listOf("prefix", NumberNode(42.0)))
        val result = node.evaluate()
        // "prefix42.0" cannot be parsed as double -> NaN
        assertTrue(result.isNaN())
    }

    @Test fun `template string pure number string`() {
        val node = TemplateStringNode(listOf("123.456"))
        assertEquals(123.456, node.evaluate(), 0.001)
    }

    @Test fun `variable node with PI constant`() {
        assertEquals(Math.PI, VariableNode("PI").evaluate(), 0.001)
    }

    @Test fun `variable node with E constant`() {
        assertEquals(Math.E, VariableNode("E").evaluate(), 0.001)
    }

    @Test fun `function call node with zero arguments`() {
        val result = FunctionCallNode("random", emptyList()).evaluate()
        assertTrue(result >= 0.0 && result < 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `function call node with unknown function throws`() {
        FunctionCallNode("nonexistent_func", listOf(NumberNode(1.0))).evaluate()
    }

    @Test fun `assignment node returns assigned value`() {
        val result = AssignmentNode("temp_var", NumberNode(77.0)).evaluate()
        assertEquals(77.0, result, 0.001)
        assertEquals(77.0, ExpressionContext.getVariable("temp_var"), 0.001)
    }

    @Test fun `nested assignment`() {
        ExpressionContext.setVariable("a", 1.0)
        val result = AssignmentNode("a", BinaryOperationNode(VariableNode("a"), "+", NumberNode(2.0))).evaluate()
        assertEquals(3.0, result, 0.001)
        assertEquals(3.0, ExpressionContext.getVariable("a"), 0.001)
    }

    @Test fun `binary operation string concatenation style`() {
        // When both operands are numbers, + is addition
        assertEquals(5.0, BinaryOperationNode(NumberNode(2.0), "+", NumberNode(3.0)).evaluate(), 0.001)
    }

    @Test fun `binary not equals`() {
        assertEquals(0.0, BinaryOperationNode(NumberNode(1.0), "!=", NumberNode(1.0)).evaluate(), 0.001)
        assertEquals(1.0, BinaryOperationNode(NumberNode(1.0), "!=", NumberNode(2.0)).evaluate(), 0.001)
    }

    @Test fun `binary comparison with same values`() {
        assertEquals(1.0, BinaryOperationNode(NumberNode(5.0), "<=", NumberNode(5.0)).evaluate(), 0.001)
        assertEquals(1.0, BinaryOperationNode(NumberNode(5.0), ">=", NumberNode(5.0)).evaluate(), 0.001)
        assertEquals(0.0, BinaryOperationNode(NumberNode(5.0), "<", NumberNode(5.0)).evaluate(), 0.001)
        assertEquals(0.0, BinaryOperationNode(NumberNode(5.0), ">", NumberNode(5.0)).evaluate(), 0.001)
    }

    @Test fun `unary not chained`() {
        val node = UnaryOperationNode("!", UnaryOperationNode("!", NumberNode(0.0)))
        assertEquals(0.0, node.evaluate(), 0.001)
    }

    @Test fun `unary not on non-zero`() {
        assertEquals(0.0, UnaryOperationNode("!", NumberNode(42.0)).evaluate(), 0.001)
    }

    @Test fun `compound assignment with subtract chain`() {
        ExpressionContext.setVariable("x", 100.0)
        CompoundAssignmentNode("x", "-=", NumberNode(30.0)).evaluate()
        CompoundAssignmentNode("x", "/=", NumberNode(2.0)).evaluate()
        assertEquals(35.0, ExpressionContext.getVariable("x"), 0.001)
    }

    @Test fun `array access via VariableNode with list`() {
        // When the variable value is a List, getArrayElement returns coerceToNumber of element
        // We can't easily set a List via setVariable since it only accepts Double
        // But we can test through coerceToNumber directly
        assertEquals(3.0, ExpressionContext.coerceToNumber(listOf(1, 2, 3)), 0.001)
    }

    @Test fun `variable node case sensitivity`() {
        ExpressionContext.setVariable("myVar", 42.0)
        assertEquals(42.0, VariableNode("myVar").evaluate(), 0.001)
    }
}
