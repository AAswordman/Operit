package com.ai.assistance.operit.core.tools.builtin.calculator

import com.ai.assistance.operit.core.tools.CalculationResultData
import com.ai.assistance.operit.core.tools.DateResultData
import com.ai.assistance.operit.core.tools.calculator.Calculator as CalcImpl
import java.util.Date

/** 增强的计算器类，支持数学表达式计算、日期计算和JavaScript语法特性 提供安全的表达式计算，替代eval() */
class StandardCalculator {
    companion object {
        /** 计算表达式 */
        fun evalExpression(expression: String): Double {
            return CalcImpl.evalExpression(expression)
        }

        /** 计算表达式并返回结构化数据 */
        fun calculateExpression(expression: String): CalculationResultData {
            val result = CalcImpl.evalExpression(expression)
            val formattedResult = CalcImpl.formatResult(result)
            val variables = getVariablesMap()

            return CalculationResultData(
                    expression = expression,
                    result = result,
                    formattedResult = formattedResult,
                    variables = variables
            )
        }

        /** 获取所有变量作为Map */
        private fun getVariablesMap(): Map<String, Double> {
            // 假设这些是CalcImpl内部常用的变量
            val commonVars = listOf("ans", "pi", "e")
            val result = mutableMapOf<String, Double>()

            for (varName in commonVars) {
                val value = getVariable(varName)
                if (value != null) {
                    result[varName] = value
                }
            }

            return result
        }

        /** 获取变量值 */
        fun getVariable(name: String): Double? {
            return CalcImpl.getVariable(name)
        }

        /** 设置变量值 */
        fun setVariable(name: String, value: Double) {
            CalcImpl.setVariable(name, value)
        }

        /** 清除所有变量 */
        fun clearVariables() {
            CalcImpl.clearVariables()
        }

        /** 格式化日期 */
        fun formatDate(date: Date, format: String): String {
            return CalcImpl.formatDate(date, format)
        }

        /** 格式化日期并返回结构化数据 */
        fun formatDateStructured(date: Date, format: String): DateResultData {
            val formattedDate = CalcImpl.formatDate(date, format)
            return DateResultData(
                    date = date.toString(),
                    format = format,
                    formattedDate = formattedDate
            )
        }

        /** 格式化结果 */
        fun formatResult(result: Double): String {
            return CalcImpl.formatResult(result)
        }

        /** 获取支持的单位列表 */
        fun getSupportedUnits(): Map<String, List<String>> {
            return CalcImpl.getSupportedUnits()
        }

        /** 获取支持的日期函数 */
        fun getSupportedDateFunctions(): List<String> {
            return CalcImpl.getSupportedDateFunctions()
        }

        /** 获取支持的统计函数 */
        fun getSupportedStatFunctions(): List<String> {
            return CalcImpl.getSupportedStatFunctions()
        }

        /** 获取支持的JavaScript特性 */
        fun getSupportedJsFeatures(): List<String> {
            return CalcImpl.getSupportedJsFeatures()
        }
    }
}
