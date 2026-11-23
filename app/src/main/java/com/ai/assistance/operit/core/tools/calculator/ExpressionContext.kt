package com.ai.assistance.operit.core.tools.calculator

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.sqrt

/** 表达式计算上下文，用于存储变量和函数 */
object ExpressionContext {
    // 变量存储
    private val variables = mutableMapOf<String, Any>()

    // 常量
    init {
        variables["PI"] = Math.PI
        variables["E"] = Math.E
    }

    // 日期格式
    private val DATE_FORMATS =
            arrayOf(
                    "yyyy-MM-dd",
                    "yyyy/MM/dd",
                    "MM/dd/yyyy",
                    "dd/MM/yyyy",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy/MM/dd HH:mm:ss"
            )

    /** 获取变量值 */
    fun getVariable(name: String): Double {
        val value = variables[name] ?: throw IllegalArgumentException("Variable $name not defined")
        return coerceToNumber(value)
    }

    /** 设置变量值 */
    fun setVariable(name: String, value: Double) {
        variables[name] = value
    }

    /** 将任意值转换为数字（JavaScript风格） */
    fun coerceToNumber(value: Any?): Double {
        return when (value) {
            null -> 0.0
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            is String -> {
                try {
                    when (value.lowercase()) {
                        "true" -> 1.0
                        "false" -> 0.0
                        "null", "undefined" -> 0.0
                        "nan" -> Double.NaN
                        "infinity" -> Double.POSITIVE_INFINITY
                        "-infinity" -> Double.NEGATIVE_INFINITY
                        else -> value.toDouble()
                    }
                } catch (e: NumberFormatException) {
                    Double.NaN // JavaScript风格：无效字符串转为NaN
                }
            }
            is List<*> -> value.size.toDouble()
            else -> Double.NaN
        }
    }

    /** 获取数组或字符串元素 */
    fun getArrayElement(array: ExpressionNode, index: ExpressionNode): Double {
        val indexValue = index.evaluate().toInt()

        when (array) {
            is VariableNode -> {
                val arrayValue = variables[array.name]
                when (arrayValue) {
                    is List<*> -> {
                        if (indexValue < 0 || indexValue >= arrayValue.size) return Double.NaN
                        return coerceToNumber(arrayValue[indexValue])
                    }
                    is String -> {
                        if (indexValue < 0 || indexValue >= arrayValue.length) return Double.NaN
                        return arrayValue[indexValue].code.toDouble()
                    }
                    else ->
                            throw IllegalArgumentException(
                                    "Value is not an array or string: ${array.name}"
                            )
                }
            }
            else -> {
                val arrayResult = array.evaluate().toString()
                if (indexValue < 0 || indexValue >= arrayResult.length) return Double.NaN
                return arrayResult[indexValue].code.toDouble()
            }
        }
    }

    /** 调用函数 */
    fun callFunction(name: String, args: List<Double>): Double {
        val mathFunctionName =
                if (name.startsWith("Math.", ignoreCase = true)) name.substring(5) else name

        return when {
            // 数学函数
            mathFunctionName.equals("abs", ignoreCase = true) -> Math.abs(args[0])
            mathFunctionName.equals("sqrt", ignoreCase = true) -> Math.sqrt(args[0])
            mathFunctionName.equals("sin", ignoreCase = true) -> Math.sin(args[0])
            mathFunctionName.equals("cos", ignoreCase = true) -> Math.cos(args[0])
            mathFunctionName.equals("tan", ignoreCase = true) -> Math.tan(args[0])
            mathFunctionName.equals("asin", ignoreCase = true) -> Math.asin(args[0])
            mathFunctionName.equals("acos", ignoreCase = true) -> Math.acos(args[0])
            mathFunctionName.equals("atan", ignoreCase = true) -> Math.atan(args[0])
            mathFunctionName.equals("log", ignoreCase = true) -> Math.log10(args[0])
            mathFunctionName.equals("ln", ignoreCase = true) -> Math.log(args[0])
            mathFunctionName.equals("round", ignoreCase = true) -> Math.round(args[0]).toDouble()
            mathFunctionName.equals("floor", ignoreCase = true) -> Math.floor(args[0])
            mathFunctionName.equals("ceil", ignoreCase = true) -> Math.ceil(args[0])
            mathFunctionName.equals("pow", ignoreCase = true) -> Math.pow(args[0], args[1])
            mathFunctionName.equals("max", ignoreCase = true) -> args.maxOrNull() ?: Double.NaN
            mathFunctionName.equals("min", ignoreCase = true) -> args.minOrNull() ?: Double.NaN
            mathFunctionName.equals("random", ignoreCase = true) -> Math.random()
            mathFunctionName.equals("fact", ignoreCase = true) -> factorial(args[0].toInt()).toDouble()

            // 日期函数
            name.equals("today", ignoreCase = true) ->
                    TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()).toDouble()
            name.equals("now", ignoreCase = true) -> System.currentTimeMillis().toDouble()
            name.equals("date", ignoreCase = true) -> {
                val dateStr = args[0].toString()
                val date =
                        parseDate(dateStr)
                                ?: throw IllegalArgumentException("Cannot parse date: $dateStr")
                TimeUnit.MILLISECONDS.toDays(date.time).toDouble()
            }
            name.equals("date_diff", ignoreCase = true) -> {
                val date1 =
                        parseDate(args[0].toString())
                                ?: throw IllegalArgumentException("Cannot parse first date")
                val date2 =
                        parseDate(args[1].toString())
                                ?: throw IllegalArgumentException("Cannot parse second date")
                val diffInMillis = Math.abs(date1.time - date2.time)
                TimeUnit.MILLISECONDS.toDays(diffInMillis).toDouble()
            }
            name.equals("date_add", ignoreCase = true) -> {
                val date =
                        parseDate(args[0].toString())
                                ?: throw IllegalArgumentException("Cannot parse date")
                val daysToAdd = args[1].toInt()
                val calendar = Calendar.getInstance()
                calendar.time = date
                calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                TimeUnit.MILLISECONDS.toDays(calendar.timeInMillis).toDouble()
            }
            name.equals("weekday", ignoreCase = true) -> {
                val date =
                        parseDate(args[0].toString())
                                ?: throw IllegalArgumentException("Cannot parse date")
                val calendar = Calendar.getInstance()
                calendar.time = date
                calendar.get(Calendar.DAY_OF_WEEK).toDouble()
            }
            name.equals("month", ignoreCase = true) -> {
                val date =
                        parseDate(args[0].toString())
                                ?: throw IllegalArgumentException("Cannot parse date")
                val calendar = Calendar.getInstance()
                calendar.time = date
                (calendar.get(Calendar.MONTH) + 1).toDouble()
            }
            name.equals("year", ignoreCase = true) -> {
                val date =
                        parseDate(args[0].toString())
                                ?: throw IllegalArgumentException("Cannot parse date")
                val calendar = Calendar.getInstance()
                calendar.time = date
                calendar.get(Calendar.YEAR).toDouble()
            }
            name.equals("day", ignoreCase = true) -> {
                val date =
                        parseDate(args[0].toString())
                                ?: throw IllegalArgumentException("Cannot parse date")
                val calendar = Calendar.getInstance()
                calendar.time = date
                calendar.get(Calendar.DAY_OF_MONTH).toDouble()
            }

            // 统计函数
            name.equals("stats.mean", ignoreCase = true) -> args.average()
            name.equals("stats.median", ignoreCase = true) -> {
                val sorted = args.sorted()
                if (sorted.size % 2 == 0) {
                    (sorted[sorted.size / 2] + sorted[sorted.size / 2 - 1]) / 2
                } else {
                    sorted[sorted.size / 2]
                }
            }
            name.equals("stats.min", ignoreCase = true) -> args.minOrNull() ?: 0.0
            name.equals("stats.max", ignoreCase = true) -> args.maxOrNull() ?: 0.0
            name.equals("stats.sum", ignoreCase = true) -> args.sum()
            name.equals("stats.stdev", ignoreCase = true) -> {
                val mean = args.average()
                val variance = args.map { (it - mean).pow(2) }.average()
                sqrt(variance)
            }

            else -> throw IllegalArgumentException("Unknown function: $name")
        }
    }

    /** 单位转换 */
    fun convertUnit(value: Double, fromUnit: String, toUnit: String): Double {
        return when {
            // 温度转换
            fromUnit == "f" && toUnit == "c" -> (value - 32) * 5 / 9 // F to C
            fromUnit == "c" && toUnit == "f" -> value * 9 / 5 + 32 // C to F
            fromUnit == "c" && toUnit == "k" -> value + 273.15 // C to K
            fromUnit == "k" && toUnit == "c" -> value - 273.15 // K to C
            fromUnit == "f" && toUnit == "k" -> (value - 32) * 5 / 9 + 273.15 // F to K
            fromUnit == "k" && toUnit == "f" -> (value - 273.15) * 9 / 5 + 32 // K to F

            // 长度转换
            fromUnit == "km" && toUnit == "mi" -> value * 0.621371 // km to miles
            fromUnit == "mi" && toUnit == "km" -> value * 1.60934 // miles to km
            fromUnit == "m" && toUnit == "ft" -> value * 3.28084 // meters to feet
            fromUnit == "ft" && toUnit == "m" -> value * 0.3048 // feet to meters
            fromUnit == "cm" && toUnit == "in" -> value * 0.393701 // cm to inches
            fromUnit == "in" && toUnit == "cm" -> value * 2.54 // inches to cm

            // 重量转换
            fromUnit == "kg" && toUnit == "lb" -> value * 2.20462 // kg to pounds
            fromUnit == "lb" && toUnit == "kg" -> value * 0.453592 // pounds to kg
            fromUnit == "g" && toUnit == "oz" -> value * 0.035274 // grams to ounces
            fromUnit == "oz" && toUnit == "g" -> value * 28.3495 // ounces to grams

            // 体积转换
            fromUnit == "l" && toUnit == "gal" -> value * 0.264172 // liters to gallons
            fromUnit == "gal" && toUnit == "l" -> value * 3.78541 // gallons to liters
            fromUnit == "ml" && toUnit == "oz" -> value * 0.033814 // milliliters to fluid ounces
            fromUnit == "oz" && toUnit == "ml" -> value * 29.5735 // fluid ounces to milliliters

            // 速度转换
            fromUnit == "kph" && toUnit == "mph" -> value * 0.621371 // km/h to miles/h
            fromUnit == "mph" && toUnit == "kph" -> value * 1.60934 // miles/h to km/h

            // 相同单位
            fromUnit == toUnit -> value
            else -> throw IllegalArgumentException("Unsupported conversion: $fromUnit to $toUnit")
        }
    }

    /** 阶乘计算 */
    private fun factorial(n: Int): Long {
        if (n < 0) throw IllegalArgumentException("Factorial is not defined for negative numbers")
        if (n > 20) throw IllegalArgumentException("Factorial too large to calculate")

        var result = 1L
        for (i in 2..n) {
            result *= i
        }
        return result
    }

    /** 日期解析 */
    private fun parseDate(dateString: String): Date? {
        // 特殊情况：today()
        if (dateString.trim() == "today()") {
            return Date(System.currentTimeMillis())
        }

        // 尝试所有支持的日期格式
        for (format in DATE_FORMATS) {
            try {
                val formatter = SimpleDateFormat(format, Locale.getDefault())
                formatter.isLenient = false
                return formatter.parse(dateString)
            } catch (e: Exception) {
                // 尝试下一个格式
            }
        }
        return null
    }

    /** 清除所有变量 */
    fun clearVariables() {
        variables.clear()

        // 重新添加常量
        variables["PI"] = Math.PI
        variables["E"] = Math.E
    }

    /** 格式化结果显示 */
    fun formatResult(result: Double): String {
        // 如果是整数则不显示小数部分
        if (result == Math.floor(result) && !result.isNaN() && !result.isInfinite()) {
            return result.toInt().toString()
        }
        // 否则使用小数格式
        return "%.6f".format(result).trimEnd('0').trimEnd('.')
    }
}
