package com.ai.assistance.operit.ui.common.a2ui

import java.math.BigDecimal
import java.math.MathContext

/** Bounded pure reducer; buttons use the ordinary local_action/set path to store its result. */
internal object CardCalculator {
    private val precision = MathContext(12)
    private val limit = BigDecimal("1000000000000")
    private val decimal = Regex("-?\\d{1,13}(\\.\\d{0,24})?")
    val keys = setOf("AC", "backspace", "sign", ".", "=", "+", "-", "*", "/") + (0..9).map(Int::toString)
    fun initial(): Map<String, Any> = state("0")
    private fun state(display: String, stored: String = "", op: String = "", replace: Boolean = false, error: Boolean = false) =
        mapOf("display" to display, "stored" to stored, "op" to op, "replace" to replace, "error" to error)
    private fun failure() = state("0", replace = true, error = true)
    private fun number(value: String): BigDecimal? =
        value.takeIf { it.length <= 40 && decimal.matches(it) }?.toBigDecimalOrNull()?.takeIf { it.abs() <= limit }
    private fun format(value: BigDecimal): String? =
        value.round(precision).stripTrailingZeros().takeIf { it.abs() <= limit && it.scale() in -12..24 }?.toPlainString()

    fun step(raw: Any?, key: String): Map<String, Any>? {
        if (key !in keys) return null
        if (key == "AC") return initial()
        val current = raw as? Map<*, *> ?: return null
        val display = current["display"] as? String ?: return null
        val stored = current["stored"] as? String ?: return null
        val op = current["op"] as? String ?: return null
        val replace = current["replace"] as? Boolean ?: return null
        val error = current["error"] as? Boolean ?: return null
        if (op !in setOf("", "+", "-", "*", "/") || display.length > 40 || stored.length > 40) return null
        val value = number(display) ?: return failure()
        fun compute(): String? {
            val a = number(stored) ?: return null
            val result = when (op) {
                "+" -> a.add(value, precision)
                "-" -> a.subtract(value, precision)
                "*" -> a.multiply(value, precision)
                "/" -> if (value.signum() == 0) return null else a.divide(value, precision)
                else -> return null
            }
            return format(result)
        }
        if (key in "0".."9" || key == ".") {
            val base = if (replace || error) "0" else display
            val next = when {
                key == "." -> if (base.contains('.')) base else "$base."
                base == "0" -> key
                base == "-0" -> "-$key"
                base.count { it.isDigit() } >= 16 -> base
                else -> base + key
            }
            return if (number(next) == null) state(display, stored, op, replace, error)
            else state(next, if (error) "" else stored, if (error) "" else op)
        }
        if (key == "backspace") {
            if (error) return initial()
            val next = if (replace) "0" else display.dropLast(1).takeUnless { it.isEmpty() || it == "-" } ?: "0"
            return state(next, stored, op)
        }
        if (error) return failure()
        if (key == "sign") return state(if (display.startsWith("-")) display.drop(1) else "-$display", stored, op, replace)
        if (key == "=") {
            if (op.isEmpty() || stored.isEmpty()) return state(display, replace = true)
            return compute()?.let { state(it, replace = true) } ?: failure()
        }
        // Consecutive operators replace the pending one; chaining evaluates only a entered operand.
        val next = if (op.isNotEmpty() && stored.isNotEmpty() && !replace) compute() ?: return failure() else display
        return state(next, next, key, replace = true)
    }
}
