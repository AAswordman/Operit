package com.ai.assistance.operit.ui.common.a2ui

import java.time.LocalDate

internal fun cardDateBounds(min: String, max: String): ClosedRange<LocalDate>? = runCatching {
    fun date(value: String, fallback: String): LocalDate {
        val text = value.ifEmpty { fallback }
        require(text.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        return LocalDate.parse(text).also { require(it.year in 1900..2100) }
    }
    val start = date(min, "1900-01-01")
    val end = date(max, "2100-12-31")
    require(start <= end)
    start..end
}.getOrNull()

/** A small math language, not arbitrary TeX macros. Bounds apply before parsing. */
internal fun validCardFormula(text: String): Boolean {
    if (text.isBlank() || text.length > 1024) return false
    val commands = setOf("frac", "sqrt", "sum", "prod", "int", "lim", "infty", "left", "right",
        "alpha", "beta", "gamma", "delta", "theta", "lambda", "mu", "pi", "sigma", "phi", "omega",
        "Delta", "Sigma", "Omega", "times", "cdot", "div", "pm", "le", "leq", "ge", "geq", "ne",
        "neq", "approx", "sin", "cos", "tan", "log", "ln", "exp", "text", "mathrm", "mathbf",
        "overline", "vec", "hat")
    var depth = 0
    var count = 0
    var i = 0
    while (i < text.length) {
        when (text[i]) {
            '\\' -> {
                val start = ++i
                while (i < text.length && text[i].isLetter()) i++
                if (i == start) {
                    if (i >= text.length || text[i] !in ",;! {}%_#") return false
                    i++
                } else if (text.substring(start, i) !in commands || ++count > 64) return false
                continue
            }
            '{' -> if (++depth > 12) return false
            '}' -> if (--depth < 0) return false
            '$', '%', '#', '&', '\u0000' -> return false
        }
        i++
    }
    return depth == 0
}
