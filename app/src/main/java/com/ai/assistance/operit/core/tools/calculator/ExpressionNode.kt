package com.ai.assistance.operit.core.tools.calculator

/** 表达式语法树的基础节点接口 */
sealed interface ExpressionNode {
    /** 计算节点的值 */
    fun evaluate(): Double
}

/** 常量节点（数字字面量） */
data class NumberNode(val value: Double) : ExpressionNode {
    override fun evaluate(): Double = value
}

/** 变量引用节点 */
data class VariableNode(val name: String) : ExpressionNode {
    override fun evaluate(): Double {
        return ExpressionContext.getVariable(name)
    }
}

/** 二元操作符节点 */
data class BinaryOperationNode(
        val left: ExpressionNode,
        val operator: String,
        val right: ExpressionNode
) : ExpressionNode {
    override fun evaluate(): Double {
        val leftValue = left.evaluate()
        val rightValue = right.evaluate()

        return when (operator) {
            "+" -> leftValue + rightValue
            "-" -> leftValue - rightValue
            "*" -> leftValue * rightValue
            "/" -> leftValue / rightValue
            "**", "^" -> Math.pow(leftValue, rightValue)
            "%" -> leftValue % rightValue
            "==" -> if (leftValue == rightValue) 1.0 else 0.0
            "!=" -> if (leftValue != rightValue) 1.0 else 0.0
            ">" -> if (leftValue > rightValue) 1.0 else 0.0
            ">=" -> if (leftValue >= rightValue) 1.0 else 0.0
            "<" -> if (leftValue < rightValue) 1.0 else 0.0
            "<=" -> if (leftValue <= rightValue) 1.0 else 0.0
            "&&" -> if (leftValue != 0.0 && rightValue != 0.0) 1.0 else 0.0
            "||" -> if (leftValue != 0.0 || rightValue != 0.0) 1.0 else 0.0
            else -> throw IllegalArgumentException("Unknown operator: $operator")
        }
    }
}

/** 一元操作符节点 */
data class UnaryOperationNode(val operator: String, val operand: ExpressionNode) : ExpressionNode {
    override fun evaluate(): Double {
        val value = operand.evaluate()

        return when (operator) {
            "+" -> value
            "-" -> -value
            "!" -> if (value == 0.0) 1.0 else 0.0
            else -> throw IllegalArgumentException("Unknown unary operator: $operator")
        }
    }
}

/** 函数调用节点 */
data class FunctionCallNode(val name: String, val arguments: List<ExpressionNode>) :
        ExpressionNode {
    override fun evaluate(): Double {
        val evaluatedArgs = arguments.map { it.evaluate() }
        return ExpressionContext.callFunction(name, evaluatedArgs)
    }
}

/**
 * 单位换算节点：`convert(value, fromUnit, toUnit)`。
 *
 * 单位是解析期确定的字符串常量，不是运行时数值，因此它们随节点保存，而不是塞进
 * [ExpressionContext] 的全局变量表——那样一次失败调用就会把 `_convert_from` 留在
 * 共享状态里，影响后续所有表达式的变量视图。
 */
data class ConversionNode(
        val value: ExpressionNode,
        val fromUnit: String,
        val toUnit: String
) : ExpressionNode {
    override fun evaluate(): Double =
            ExpressionContext.convertUnit(value.evaluate(), fromUnit, toUnit)
}

/** 三元运算符节点 (condition ? trueExpr : falseExpr) */
data class TernaryOperationNode(
        val condition: ExpressionNode,
        val trueExpression: ExpressionNode,
        val falseExpression: ExpressionNode
) : ExpressionNode {
    override fun evaluate(): Double {
        val conditionValue = condition.evaluate()
        return if (conditionValue != 0.0) {
            trueExpression.evaluate()
        } else {
            falseExpression.evaluate()
        }
    }
}

/** 变量赋值节点 */
data class AssignmentNode(val variableName: String, val value: ExpressionNode) : ExpressionNode {
    override fun evaluate(): Double {
        val result = value.evaluate()
        ExpressionContext.setVariable(variableName, result)
        return result
    }
}

/** 复合赋值节点 (+=, -=, *=, /=) */
data class CompoundAssignmentNode(
        val variableName: String,
        val operator: String,
        val value: ExpressionNode
) : ExpressionNode {
    override fun evaluate(): Double {
        val currentValue = ExpressionContext.getVariable(variableName)
        val rightValue = value.evaluate()

        val result =
                when (operator) {
                    "+=" -> currentValue + rightValue
                    "-=" -> currentValue - rightValue
                    "*=" -> currentValue * rightValue
                    "/=" -> currentValue / rightValue
                    else ->
                            throw IllegalArgumentException(
                                    "Unknown compound assignment operator: $operator"
                            )
                }

        ExpressionContext.setVariable(variableName, result)
        return result
    }
}

/** 数组元素访问节点 */
data class ArrayAccessNode(val array: ExpressionNode, val index: ExpressionNode) : ExpressionNode {
    override fun evaluate(): Double {
        return ExpressionContext.getArrayElement(array, index)
    }
}

/** 字符串模板节点 */
data class TemplateStringNode(
        val parts: List<Any> // String 或 ExpressionNode
) : ExpressionNode {
    override fun evaluate(): Double {
        val result =
                parts.joinToString("") { part ->
                    when (part) {
                        is String -> part
                        is ExpressionNode -> part.evaluate().toString()
                        else -> part.toString()
                    }
                }

        return try {
            result.toDouble()
        } catch (e: NumberFormatException) {
            // JS的行为是返回NaN
            Double.NaN
        }
    }
}
