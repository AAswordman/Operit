package com.ai.assistance.operit.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal object MathMlPlainTextConverter {
    fun convert(mathMl: String): String {
        val math = Jsoup.parse(mathMl).selectFirst("math")
            ?: error("MathML does not contain a math element")
        return render(math)
            .replace(Regex("""\s+"""), " ")
            .replace("( ", "(")
            .replace(" )", ")")
            .replace("[ ", "[")
            .replace(" ]", "]")
            .replace(" ,", ",")
            .trim()
    }

    private fun render(node: Node): String {
        if (node is TextNode) return node.text().takeUnless(String::isBlank).orEmpty()
        if (node !is Element) return ""

        val children = node.children()
        return when (node.tagName()) {
            "annotation" -> ""
            "semantics" -> children.firstOrNull()?.let(::render).orEmpty()
            "mfrac" -> {
                val numerator = children.getOrNull(0)?.let(::render).orEmpty()
                val denominator = children.getOrNull(1)?.let(::render).orEmpty()
                "($numerator)/($denominator)"
            }
            "msqrt" -> "√(${renderChildren(node)})"
            "mroot" -> {
                val radicand = children.getOrNull(0)?.let(::render).orEmpty()
                val degree = children.getOrNull(1)?.let(::render).orEmpty()
                "root($degree, $radicand)"
            }
            "msup" -> {
                val base = children.getOrNull(0)?.let(::render).orEmpty()
                val exponent = children.getOrNull(1)?.let(::render).orEmpty()
                appendLargeOperatorSpacing(
                    base,
                    base + (toSuperscript(exponent) ?: "^($exponent)")
                )
            }
            "msub" -> {
                val base = children.getOrNull(0)?.let(::render).orEmpty()
                val subscript = children.getOrNull(1)?.let(::render).orEmpty()
                appendLargeOperatorSpacing(
                    base,
                    base + (toSubscript(subscript) ?: "_($subscript)")
                )
            }
            "msubsup" -> {
                val base = children.getOrNull(0)?.let(::render).orEmpty()
                val subscript = children.getOrNull(1)?.let(::render).orEmpty()
                val superscript = children.getOrNull(2)?.let(::render).orEmpty()
                appendLargeOperatorSpacing(
                    base,
                    base +
                        (toSubscript(subscript) ?: "_($subscript)") +
                        (toSuperscript(superscript) ?: "^($superscript)")
                )
            }
            "munder" -> {
                val base = children.getOrNull(0)?.let(::render).orEmpty()
                val under = children.getOrNull(1)?.let(::render).orEmpty()
                appendLargeOperatorSpacing(base, "${base}_($under)")
            }
            "mover" -> {
                val base = children.getOrNull(0)?.let(::render).orEmpty()
                val over = children.getOrNull(1)?.let(::render).orEmpty()
                appendLargeOperatorSpacing(base, "$base^($over)")
            }
            "munderover" -> {
                val base = children.getOrNull(0)?.let(::render).orEmpty()
                val under = children.getOrNull(1)?.let(::render).orEmpty()
                val over = children.getOrNull(2)?.let(::render).orEmpty()
                appendLargeOperatorSpacing(base, "${base}_($under)^($over)")
            }
            "mtable" -> {
                children.joinToString(prefix = "[", postfix = "]", separator = "; ") { row ->
                    row.children().joinToString(", ") { cell -> render(cell) }
                }
            }
            "mo" -> renderOperator(node.text())
            "mi" -> {
                val text = node.text()
                if (text in FUNCTION_NAMES) "$text " else text
            }
            "mn", "mtext" -> node.text()
            "mspace" -> " "
            else -> renderChildren(node)
        }
    }

    private fun renderChildren(element: Element): String =
        element.childNodes().joinToString(separator = "", transform = ::render)

    private fun renderOperator(operator: String): String =
        when (operator) {
            "\u2061" -> ""
            "+", "−", "-", "=", "≠", "≤", "≥", "±", "∓", "×", "÷", "∈", "∉",
            "→", "←", "↔", "⇒", "⇐", "⇔", "∧", "∨" -> " $operator "
            "," -> ", "
            else -> operator
        }

    private fun appendLargeOperatorSpacing(base: String, expression: String): String =
        if (base.trim() in LARGE_OPERATORS) "$expression " else expression

    private fun toSuperscript(value: String): String? =
        value.map { SUPERSCRIPTS[it] ?: return null }.joinToString("")

    private fun toSubscript(value: String): String? =
        value.map { SUBSCRIPTS[it] ?: return null }.joinToString("")

    private val FUNCTION_NAMES = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc",
        "sinh", "cosh", "tanh", "log", "ln", "exp",
        "lim", "max", "min"
    )

    private val LARGE_OPERATORS = setOf("∑", "∏", "∫", "∬", "∭", "lim")

    private val SUPERSCRIPTS = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'i' to 'ⁱ', 'n' to 'ⁿ'
    )

    private val SUBSCRIPTS = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎'
    )
}
