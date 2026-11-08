package com.ai.assistance.operit.core.tools.condition

import com.ai.assistance.operit.util.logging.AppLogger

object ConditionEvaluator {
    private const val TAG = "ConditionEvaluator"

    fun evaluate(expression: String, capabilities: Map<String, Any?>): Boolean {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) {
            return true
        }

        return try {
            val tokens = Tokenizer(trimmed).tokenize()
            val parser = Parser(tokens)
            val ast = parser.parseExpression()
            val value = ast.eval(capabilities)
            value.isTruthy()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Condition evaluation failed: '$expression' (${e.message})")
            false
        }
    }

    private sealed interface Token {
        data class Identifier(val text: String) : Token
        data class StringLiteral(val value: String) : Token
        data class NumberLiteral(val value: Double) : Token
        data class BooleanLiteral(val value: Boolean) : Token
        data object NullLiteral : Token
        data class Operator(val op: String) : Token
        data class Punct(val ch: Char) : Token
        data object Eof : Token
    }

    private class Tokenizer(private val input: String) {
        private var i = 0

        fun tokenize(): List<Token> {
            val out = mutableListOf<Token>()
            while (true) {
                skipWs()
                if (i >= input.length) {
                    out.add(Token.Eof)
                    return out
                }

                val c = input[i]
                when {
                    c == '(' || c == ')' || c == '[' || c == ']' || c == ',' -> {
                        out.add(Token.Punct(c))
                        i++
                    }
                    c == '"' || c == '\'' -> {
                        out.add(Token.StringLiteral(readString(c)))
                    }
                    c.isDigit() || (c == '.' && i + 1 < input.length && input[i + 1].isDigit()) -> {
                        out.add(Token.NumberLiteral(readNumber()))
                    }
                    isIdentStart(c) -> {
                        val ident = readIdentifier()
                        when (ident) {
                            "true" -> out.add(Token.BooleanLiteral(true))
                            "false" -> out.add(Token.BooleanLiteral(false))
                            "null" -> out.add(Token.NullLiteral)
                            "in" -> out.add(Token.Operator("in"))
                            else -> out.add(Token.Identifier(ident))
                        }
                    }
                    else -> {
                        val op = readOperator() ?: throw IllegalArgumentException("Unexpected character '$c'")
                        out.add(Token.Operator(op))
                    }
                }
            }
        }

        private fun skipWs() {
            while (i < input.length && input[i].isWhitespace()) i++
        }

        private fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_'

        private fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '.'

        private fun readIdentifier(): String {
            val start = i
            i++
            while (i < input.length && isIdentPart(input[i])) i++
            return input.substring(start, i)
        }

        private fun readString(quote: Char): String {
            i++
            val sb = StringBuilder()
            while (i < input.length) {
                val c = input[i]
                if (c == quote) {
                    i++
                    return sb.toString()
                }
                if (c == '\\') {
                    if (i + 1 >= input.length) {
                        throw IllegalArgumentException("Unterminated escape")
                    }
                    val n = input[i + 1]
                    when (n) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        '\\' -> sb.append('\\')
                        '\'' -> sb.append('\'')
                        '"' -> sb.append('"')
                        else -> sb.append(n)
                    }
                    i += 2
                    continue
                }
                sb.append(c)
                i++
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun readNumber(): Double {
            val start = i
            var hasDot = false
            while (i < input.length) {
                val c = input[i]
                if (c.isDigit()) {
                    i++
                } else if (c == '.' && !hasDot) {
                    hasDot = true
                    i++
                } else {
                    break
                }
            }
            return input.substring(start, i).toDouble()
        }

        private fun readOperator(): String? {
            fun match(s: String): Boolean {
                if (!input.startsWith(s, i)) return false
                i += s.length
                return true
            }

            return when {
                match("&&") -> "&&"
                match("||") -> "||"
                match("==") -> "=="
                match("!=") -> "!="
                match(">=") -> ">="
                match("<=") -> "<="
                match(">") -> ">"
                match("<") -> "<"
                match("!") -> "!"
                else -> null
            }
        }
    }

    private sealed interface Expr {
        fun eval(capabilities: Map<String, Any?>): Value
    }

    private data class LiteralExpr(val value: Value) : Expr {
        override fun eval(capabilities: Map<String, Any?>): Value = value
    }

    private data class IdentifierExpr(val name: String) : Expr {
        override fun eval(capabilities: Map<String, Any?>): Value {
            return Value.fromAny(capabilities[name])
        }
    }

    private data class ArrayExpr(val elements: List<Expr>) : Expr {
        override fun eval(capabilities: Map<String, Any?>): Value {
            return Value.Array(elements.map { it.eval(capabilities) })
        }
    }

    private data class UnaryExpr(val op: String, val expr: Expr) : Expr {
        override fun eval(capabilities: Map<String, Any?>): Value {
            val v = expr.eval(capabilities)
            return when (op) {
                "!" -> Value.Bool(!v.isTruthy())
                else -> throw IllegalArgumentException("Unsupported unary operator: $op")
            }
        }
    }

    private data class BinaryExpr(val left: Expr, val op: String, val right: Expr) : Expr {
        override fun eval(capabilities: Map<String, Any?>): Value {
            return when (op) {
                "&&" -> {
                    val lv = left.eval(capabilities)
                    if (!lv.isTruthy()) return Value.Bool(false)
                    val rv = right.eval(capabilities)
                    Value.Bool(rv.isTruthy())
                }
                "||" -> {
                    val lv = left.eval(capabilities)
                    if (lv.isTruthy()) return Value.Bool(true)
                    val rv = right.eval(capabilities)
                    Value.Bool(rv.isTruthy())
                }
                "==" -> Value.Bool(left.eval(capabilities) == right.eval(capabilities))
                "!=" -> Value.Bool(left.eval(capabilities) != right.eval(capabilities))
                ">" -> Value.Bool(left.eval(capabilities).compareTo(right.eval(capabilities)) > 0)
                ">=" -> Value.Bool(left.eval(capabilities).compareTo(right.eval(capabilities)) >= 0)
                "<" -> Value.Bool(left.eval(capabilities).compareTo(right.eval(capabilities)) < 0)
                "<=" -> Value.Bool(left.eval(capabilities).compareTo(right.eval(capabilities)) <= 0)
                "in" -> {
                    val item = left.eval(capabilities)
                    val container = right.eval(capabilities)
                    val ok = (container as? Value.Array)?.items?.any { it == item } == true
                    Value.Bool(ok)
                }
                else -> throw IllegalArgumentException("Unsupported operator: $op")
            }
        }
    }

    private sealed interface Value : Comparable<Value> {
        fun isTruthy(): Boolean

        data class Bool(val value: Boolean) : Value {
            override fun isTruthy(): Boolean = value
            override fun compareTo(other: Value): Int = compareValues(toNumberOrNull(), other.toNumberOrNull())
        }

        data class Num(val value: Double) : Value {
            override fun isTruthy(): Boolean = value != 0.0 && !value.isNaN()
            override fun compareTo(other: Value): Int = compareValues(value, other.toNumberOrNull())
        }

        data class Str(val value: String) : Value {
            override fun isTruthy(): Boolean = value.isNotEmpty()
            override fun compareTo(other: Value): Int {
                val otherStr = (other as? Str)?.value
                    ?: throw IllegalArgumentException("Cannot compare string to ${other::class.simpleName}")
                return value.compareTo(otherStr)
            }
        }

        data object Null : Value {
            override fun isTruthy(): Boolean = false
            override fun compareTo(other: Value): Int = throw IllegalArgumentException("Cannot compare null")
        }

        data class Array(val items: List<Value>) : Value {
            override fun isTruthy(): Boolean = items.isNotEmpty()
            override fun compareTo(other: Value): Int = throw IllegalArgumentException("Cannot compare array")
        }

        fun toNumberOrNull(): Double? = when (this) {
            is Num -> value
            is Bool -> if (value) 1.0 else 0.0
            else -> null
        }

        companion object {
            fun fromAny(v: Any?): Value {
                return when (v) {
                    null -> Null
                    is Value -> v
                    is Boolean -> Bool(v)
                    is Number -> Num(v.toDouble())
                    is CharSequence -> Str(v.toString())
                    is Enum<*> -> Str(v.name)
                    is List<*> -> Array(v.map { fromAny(it) })
                    is kotlin.Array<*> -> Array(v.map { fromAny(it) })
                    else -> Str(v.toString())
                }
            }
        }
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun parseExpression(): Expr = parseOr()

        private fun parseOr(): Expr {
            var left = parseAnd()
            while (matchOp("||")) {
                val right = parseAnd()
                left = BinaryExpr(left, "||", right)
            }
            return left
        }

        private fun parseAnd(): Expr {
            var left = parseEquality()
            while (matchOp("&&")) {
                val right = parseEquality()
                left = BinaryExpr(left, "&&", right)
            }
            return left
        }

        private fun parseEquality(): Expr {
            var left = parseRelational()
            while (true) {
                left = when {
                    matchOp("==") -> BinaryExpr(left, "==", parseRelational())
                    matchOp("!=") -> BinaryExpr(left, "!=", parseRelational())
                    else -> return left
                }
            }
        }

        private fun parseRelational(): Expr {
            var left = parseUnary()
            while (true) {
                left = when {
                    matchOp(">=") -> BinaryExpr(left, ">=", parseUnary())
                    matchOp("<=") -> BinaryExpr(left, "<=", parseUnary())
                    matchOp(">") -> BinaryExpr(left, ">", parseUnary())
                    matchOp("<") -> BinaryExpr(left, "<", parseUnary())
                    matchOp("in") -> BinaryExpr(left, "in", parseUnary())
                    else -> return left
                }
            }
        }

        private fun parseUnary(): Expr {
            if (matchOp("!")) {
                return UnaryExpr("!", parseUnary())
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Expr {
            val t = peek()
            when (t) {
                is Token.BooleanLiteral -> {
                    pos++
                    return LiteralExpr(Value.Bool(t.value))
                }
                is Token.NullLiteral -> {
                    pos++
                    return LiteralExpr(Value.Null)
                }
                is Token.NumberLiteral -> {
                    pos++
                    return LiteralExpr(Value.Num(t.value))
                }
                is Token.StringLiteral -> {
                    pos++
                    return LiteralExpr(Value.Str(t.value))
                }
                is Token.Identifier -> {
                    pos++
                    return IdentifierExpr(t.text)
                }
                is Token.Punct -> {
                    if (t.ch == '(') {
                        pos++
                        val inner = parseExpression()
                        expectPunct(')')
                        return inner
                    }
                    if (t.ch == '[') {
                        pos++
                        val elements = mutableListOf<Expr>()
                        if (!checkPunct(']')) {
                            elements.add(parseExpression())
                            while (matchPunct(',')) {
                                elements.add(parseExpression())
                            }
                        }
                        expectPunct(']')
                        return ArrayExpr(elements)
                    }
                }
                else -> {
                }
            }
            throw IllegalArgumentException("Unexpected token: $t")
        }

        private fun peek(): Token = tokens.getOrElse(pos) { Token.Eof }

        private fun matchOp(op: String): Boolean {
            val t = peek()
            if (t is Token.Operator && t.op == op) {
                pos++
                return true
            }
            return false
        }

        private fun matchPunct(ch: Char): Boolean {
            val t = peek()
            if (t is Token.Punct && t.ch == ch) {
                pos++
                return true
            }
            return false
        }

        private fun checkPunct(ch: Char): Boolean {
            val t = peek()
            return t is Token.Punct && t.ch == ch
        }

        private fun expectPunct(ch: Char) {
            if (!matchPunct(ch)) {
                throw IllegalArgumentException("Expected '$ch'")
            }
        }
    }
}
