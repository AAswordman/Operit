/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ConcurrentHashMap is required for atomic compute() operations and is safe for our minSdk 24.
@file:Suppress("BanConcurrentHashMap")

package androidx.a2ui.model.catalog.functions

import androidx.a2ui.model.catalog.A2uiFunction
import androidx.a2ui.model.catalog.A2uiFunctionDefinition
import androidx.a2ui.model.catalog.A2uiFunctionReturnType
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.protocol.A2uiExecutionContext
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicStringSchema
import java.util.concurrent.ConcurrentHashMap

/**
 * Performs string interpolation of data model values and other functions.
 *
 * Use this [A2uiFunction] to format strings with expressions.
 */
public class A2uiFormatStringFunction private constructor() : A2uiFunction {

    override val definition: A2uiFunctionDefinition =
        object : A2uiFunctionDefinition {
            override val name: String = "formatString"

            override val description: String =
                """Performs string interpolation of data model values and other functions in the catalog functions list and returns the resulting string. The value string can contain interpolated expressions in the ${'$'}{expression} format. Supported expression types include: JSON Pointer paths to the data model (e.g., ${'$'}{/absolute/path} or ${'$'}{relative/path}), and client-side function calls (e.g., ${'$'}{now()}). Function arguments must be named (e.g., ${'$'}{formatDate(value:${'$'}{/currentDate}, format:'MM-dd')}). To include a literal ${'$'}{ sequence, escape it as \${'$'}{."""

            override val returnType: A2uiFunctionReturnType = A2uiFunctionReturnType.STRING

            override val argumentSchema: A2uiSchema =
                A2uiObjectSchema(
                    properties = mapOf(ARG_VALUE_KEY to A2uiDynamicStringSchema.DEFAULT_INSTANCE),
                    required = setOf(ARG_VALUE_KEY),
                    isAdditionalPropertiesAllowed = false,
                )
        }

    /**
     * Interpolates the given string with bindings.
     *
     * @param args arguments containing the "value" string to format
     * @param executionContext context allowing to execute other functions, evaluate dynamic
     *   payloads and resolving data bindings
     * @return the formatted string
     */
    override fun execute(args: Map<String, Any>, executionContext: A2uiExecutionContext): Any? {
        val value = A2uiFunctionArgParser.getStringArg(args, ARG_VALUE_KEY)
        return parseAndResolve(value, executionContext)
    }

    private sealed interface TemplatePart {
        data class Literal(val text: String) : TemplatePart

        data class Expression(val expressionAst: Any?) : TemplatePart
    }

    /**
     * Parses the string to find bindings formatted as `${binding}`, resolves them, and replaces
     * them in the resulting string. It also unescapes `\${` to `${`.
     */
    private fun parseAndResolve(value: String, executionContext: A2uiExecutionContext): String {
        val cache =
            executionContext.getOrCreateFunctionScopedCache(definition) {
                ConcurrentHashMap<String, List<TemplatePart>>()
            }
        val template = cache.computeIfAbsent(value) { parseToTemplate(value) }

        return buildString {
            for (part in template) {
                when (part) {
                    is TemplatePart.Literal -> append(part.text)
                    is TemplatePart.Expression -> {
                        val evaluated = executionContext.evaluatePayload(part.expressionAst) ?: ""
                        append(evaluated.toString())
                    }
                }
            }
        }
    }

    private fun parseToTemplate(value: String): List<TemplatePart> {
        val parts = mutableListOf<TemplatePart>()
        var i = 0
        val currentLiteral = StringBuilder()

        while (i < value.length) {
            val nextStart = value.indexOf("\${", i)
            if (nextStart == -1) {
                currentLiteral.append(value.substring(i))
                break
            }

            // Count consecutive backslashes preceding the "${"
            var backslashCount = 0
            var j = nextStart - 1
            while ((j >= i) && (value[j] == '\\')) {
                backslashCount++
                j--
            }

            // Append everything up to the backslashes
            currentLiteral.append(value.substring(i, nextStart - backslashCount))

            // Every pair of backslashes "\\" collapses into a single literal "\"
            val literalBackslashes = backslashCount / 2
            repeat(literalBackslashes) { currentLiteral.append('\\') }

            // An odd number of backslashes means the "${" is escaped
            val isEscaped = (backslashCount % 2) != 0
            if (isEscaped) {
                currentLiteral.append("\${")
                i = nextStart + 2
            } else {
                val bindingValue = extractBindingValue(value, nextStart)
                if (bindingValue == null) {
                    currentLiteral.append(value.substring(nextStart))
                    i = value.length
                } else {
                    if (currentLiteral.isNotEmpty()) {
                        parts.add(TemplatePart.Literal(currentLiteral.toString()))
                        currentLiteral.setLength(0)
                    }
                    val ast = parseStringToAst(bindingValue.rawContent)
                    parts.add(TemplatePart.Expression(ast))
                    i = nextStart + 2 + bindingValue.lengthInSource + 1 // +2 for "${", +1 for "}"
                }
            }
        }

        if (currentLiteral.isNotEmpty()) {
            parts.add(TemplatePart.Literal(currentLiteral.toString()))
        }

        return parts
    }

    private class BindingValue(val rawContent: String, val lengthInSource: Int)

    /**
     * Skips a string literal starting at [startIndex] (accounting for escape sequences and matching
     * [quoteChar]), and returns the index immediately following the closing quote.
     */
    private fun skipStringLiteral(value: String, startIndex: Int, quoteChar: Char): Int {
        var i = startIndex + 1
        while (i < value.length) {
            when (value[i]) {
                '\\' -> {
                    i += 2 // Skip escape character and the character following it
                }
                quoteChar -> {
                    return i + 1 // Found closing quote, return next position
                }
                else -> {
                    i++
                }
            }
        }
        return i
    }

    /**
     * Extracts the raw string value of a binding starting at [startIndex], accounting for nested
     * expressions, string literals, and escaped characters.
     *
     * @return the extracted binding value, or null if no matching closing brace is found.
     */
    private fun extractBindingValue(value: String, startIndex: Int): BindingValue? {
        var braceBalance = 1
        var i = startIndex + 2

        while (i < value.length && braceBalance > 0) {
            val c = value[i]
            if (c == '\'' || c == '"') {
                // Ignore curly braces inside string literals
                i = skipStringLiteral(value, i, c)
            } else if (c == '\\') {
                // Skip the backslash and the escaped character (e.g. \} or \$)
                i += 2
            } else if (value.startsWith("""${'$'}{""", i)) {
                // Found a nested block start: "${"
                braceBalance++
                i += 2
            } else if (c == '}') {
                // Found standard closing brace: "}"
                braceBalance--
                if (braceBalance == 0) {
                    val lengthInSource = i - (startIndex + 2)
                    val rawBinding = value.substring(startIndex + 2, i)
                    return BindingValue(rawBinding, lengthInSource)
                }
                i++
            } else {
                i++
            }
        }
        return null
    }

    private fun parseStringToAst(binding: String): Any? {
        val parseTasksStack = mutableListOf<ParseTask>()
        val valueStack = mutableListOf<Any?>()

        parseTasksStack.add(ParseTask.ParseExpr(binding))

        while (parseTasksStack.isNotEmpty()) {
            when (val task = parseTasksStack.removeAt(parseTasksStack.lastIndex)) {
                is ParseTask.ParseExpr -> {
                    processParseExpr(task, parseTasksStack, valueStack)
                }
                is ParseTask.AssembleFunction -> {
                    processAssembleFunction(task, valueStack)
                }
            }
        }

        return if (valueStack.isNotEmpty()) valueStack.first() else mapOf("path" to binding)
    }

    private fun unescapeStringLiteral(content: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (c == '\\' && i + 1 < content.length) {
                val next = content[i + 1]
                when (next) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '\\' -> sb.append('\\')
                    '\'' -> sb.append('\'')
                    '"' -> sb.append('"')
                    '$' -> sb.append('$')
                    '{' -> sb.append('{')
                    '}' -> sb.append('}')
                    else -> {
                        sb.append(c)
                        sb.append(next)
                    }
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun processParseExpr(
        task: ParseTask.ParseExpr,
        parseTasksStack: MutableList<ParseTask>,
        valueStack: MutableList<Any?>,
    ) {
        val expr = task.expression.trim()
        val isSingleQuoted = expr.startsWith("'") && expr.endsWith("'") && expr.length >= 2
        val isDoubleQuoted = expr.startsWith("\"") && expr.endsWith("\"") && expr.length >= 2

        if (expr.startsWith("\${") && expr.endsWith("}")) {
            parseTasksStack.add(ParseTask.ParseExpr(expr.substring(2, expr.length - 1)))
        } else if (isSingleQuoted || isDoubleQuoted) {
            val inner = expr.substring(1, expr.length - 1)
            valueStack.add(unescapeStringLiteral(inner))
        } else if (expr == "true") {
            valueStack.add(true)
        } else if (expr == "false") {
            valueStack.add(false)
        } else if (expr == "null") {
            valueStack.add(null)
        } else {
            val longVal = expr.toLongOrNull()
            if (longVal != null) {
                valueStack.add(longVal)
            } else {
                val doubleVal = expr.toDoubleOrNull()
                if (doubleVal != null) {
                    valueStack.add(doubleVal)
                } else {
                    // Check if function call
                    val firstParen = expr.indexOf('(')
                    if (firstParen > 0) {
                        val name = expr.substring(0, firstParen).trim()
                        if (isValidIdentifier(name)) {
                            if (expr.endsWith(")")) {
                                processFunctionCall(expr, firstParen, parseTasksStack, valueStack)
                            } else {
                                throw A2uiException.A2uiRuntimeException(
                                    "Malformed function call '$expr': missing closing parenthesis"
                                )
                            }
                        } else {
                            // Unescape paths specifically at the AST level
                            val unescapedPath =
                                expr
                                    .replace("""\${'$'}{""", """${'$'}{""")
                                    .replace("""\}""", """}""")
                            valueStack.add(mapOf("path" to unescapedPath))
                        }
                    } else {
                        // Unescape paths specifically at the AST level
                        val unescapedPath =
                            expr.replace("""\${'$'}{""", """${'$'}{""").replace("""\}""", """}""")
                        valueStack.add(mapOf("path" to unescapedPath))
                    }
                }
            }
        }
    }

    private fun processFunctionCall(
        expr: String,
        firstParenthesis: Int,
        parseTasksStack: MutableList<ParseTask>,
        valueStack: MutableList<Any?>,
    ) {
        val name = expr.substring(0, firstParenthesis).trim()
        if (isValidIdentifier(name)) {
            val argsStr = expr.substring(firstParenthesis + 1, expr.length - 1).trim()
            if (argsStr.isEmpty()) {
                valueStack.add(mapOf("call" to name))
            } else {
                val args = splitTopLevelArgs(argsStr)
                val keys = mutableListOf<String>()
                val rawValues = mutableListOf<String>()
                for (arg in args) {
                    val colonIdx = arg.indexOf(':')
                    if (colonIdx > 0) {
                        val key = arg.substring(0, colonIdx).trim()
                        if (isValidIdentifier(key)) {
                            keys.add(key)
                            rawValues.add(arg.substring(colonIdx + 1).trim())
                        } else {
                            throw A2uiException.A2uiRuntimeException(
                                "Malformed argument '$arg' in function call '$expr': " +
                                    "argument key must be a valid identifier"
                            )
                        }
                    } else {
                        throw A2uiException.A2uiRuntimeException(
                            "Malformed argument '$arg' in function call '$expr': " +
                                "must be in key:value format with valid identifiers"
                        )
                    }
                }
                parseTasksStack.add(ParseTask.AssembleFunction(name, keys))
                for (j in rawValues.indices.reversed()) {
                    parseTasksStack.add(ParseTask.ParseExpr(rawValues[j]))
                }
            }
        } else {
            throw A2uiException.A2uiRuntimeException(
                "Invalid function name identifier '$name' in function call '$expr'"
            )
        }
    }

    private fun processAssembleFunction(
        task: ParseTask.AssembleFunction,
        valueStack: MutableList<Any?>,
    ) {
        val argsMap = mutableMapOf<String, Any?>()
        val size = task.keys.size
        val startIndex = valueStack.size - size
        for (j in 0 until size) {
            argsMap[task.keys[j]] = valueStack[startIndex + j]
        }
        if (size > 0) {
            valueStack.subList(startIndex, valueStack.size).clear()
        }
        valueStack.add(mapOf("call" to task.name, "args" to argsMap))
    }

    /** Checks if [name] is a valid identifier conforming to UAX #31. */
    private fun isValidIdentifier(name: String): Boolean {
        if (name.isEmpty() || (!Character.isUnicodeIdentifierStart(name[0]) && name[0] != '_'))
            return false
        for (i in 1 until name.length) {
            if (!Character.isUnicodeIdentifierPart(name[i])) return false
        }
        return true
    }

    private fun splitTopLevelArgs(argsStr: String): List<String> {
        val result = mutableListOf<String>()
        var braceCount = 0
        var parenCount = 0
        var quoteChar: Char? = null // null means not in quotes, otherwise '\'' or '"'
        val currentArg = StringBuilder()

        var i = 0
        while (i < argsStr.length) {
            val c = argsStr[i]
            if (quoteChar != null) {
                // We are inside a string literal
                if (c == '\\' && i + 1 < argsStr.length) {
                    currentArg.append(c)
                    currentArg.append(argsStr[i + 1])
                    i += 2
                } else if (c == quoteChar) {
                    currentArg.append(c)
                    quoteChar = null // closing quote
                    i++
                } else {
                    currentArg.append(c)
                    i++
                }
            } else {
                // Not inside a string literal
                if (c == '\'' || c == '"') {
                    quoteChar = c
                    currentArg.append(c)
                    i++
                } else {
                    when (c) {
                        '{' -> {
                            braceCount++
                            currentArg.append(c)
                        }
                        '}' -> {
                            braceCount--
                            currentArg.append(c)
                        }
                        '(' -> {
                            parenCount++
                            currentArg.append(c)
                        }
                        ')' -> {
                            parenCount--
                            currentArg.append(c)
                        }
                        ',' -> {
                            if (braceCount == 0 && parenCount == 0) {
                                result.add(currentArg.toString().trim())
                                currentArg.setLength(0)
                            } else {
                                currentArg.append(c)
                            }
                        }
                        else -> currentArg.append(c)
                    }
                    i++
                }
            }
        }
        if (currentArg.isNotEmpty()) {
            result.add(currentArg.toString().trim())
        }
        return result
    }

    private sealed class ParseTask {
        class ParseExpr(val expression: String) : ParseTask()

        class AssembleFunction(val name: String, val keys: List<String>) : ParseTask()
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiFormatStringFunction = A2uiFormatStringFunction()

        private const val ARG_VALUE_KEY: String = "value"
    }
}
