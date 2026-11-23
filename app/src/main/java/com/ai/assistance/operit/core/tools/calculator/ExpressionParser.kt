package com.ai.assistance.operit.core.tools.calculator

/**
 * 表达式解析器
 *
 * 将表达式字符串解析为语法树
 */
class ExpressionParser(private val expression: String) {
    private var position = 0
    private var currentToken = ""
    private var currentTokenType = TokenType.NONE

    /** 词法单元类型 */
    enum class TokenType {
        NONE,
        NUMBER,
        IDENTIFIER,
        OPERATOR,
        LEFT_PAREN,
        RIGHT_PAREN,
        LEFT_BRACKET,
        RIGHT_BRACKET,
        COMMA,
        STRING,
        TEMPLATE_START,
        TEMPLATE_MIDDLE,
        TEMPLATE_END,
        EOF
    }

    /** 解析表达式 */
    fun parse(): ExpressionNode {
        nextToken()
        val result = parseExpression()

        if (currentTokenType != TokenType.EOF) {
            throw IllegalArgumentException("Unexpected token: $currentToken")
        }

        return result
    }

    /** 解析表达式 */
    private fun parseExpression(): ExpressionNode {
        return parseTernary()
    }

    /** 解析三元运算符 */
    private fun parseTernary(): ExpressionNode {
        val condition = parseAssignment()

        if (currentToken == "?") {
            nextToken()
            val trueExpr = parseAssignment()

            if (currentToken != ":") {
                throw IllegalArgumentException("Expected ':' in ternary operator")
            }
            nextToken()

            val falseExpr = parseAssignment()
            return TernaryOperationNode(condition, trueExpr, falseExpr)
        }

        return condition
    }

    /** 解析赋值表达式 */
    private fun parseAssignment(): ExpressionNode {
        if (currentTokenType == TokenType.IDENTIFIER) {
            val variableName = currentToken
            val nextPos = position
            val nextChar = if (position < expression.length) expression[position] else ' '

            if (nextChar == '=') {
                val followingChar =
                        if (position + 1 < expression.length) expression[position + 1] else ' '

                if (followingChar == '=') {
                    // 这是==运算符，不是赋值
                    return parseLogicalOr()
                }

                // 简单赋值: x = expr
                nextToken() // 跳过=
                nextToken() // 获取下一个token

                val valueExpr = parseAssignment() // 递归解析右侧表达式
                return AssignmentNode(variableName, valueExpr)
            } else if (nextChar == '+' || nextChar == '-' || nextChar == '*' || nextChar == '/') {
                if (position + 1 < expression.length && expression[position + 1] == '=') {
                    // 复合赋值: x += expr, x -= expr, etc.
                    val operator = nextChar.toString() + "="
                    position += 2 // 跳过操作符
                    nextToken()

                    val valueExpr = parseAssignment()
                    return CompoundAssignmentNode(variableName, operator, valueExpr)
                }
            }
        }

        return parseLogicalOr()
    }

    /** 解析逻辑OR表达式 */
    private fun parseLogicalOr(): ExpressionNode {
        var left = parseLogicalAnd()

        while (currentToken == "||") {
            val operator = currentToken
            nextToken()
            val right = parseLogicalAnd()
            left = BinaryOperationNode(left, operator, right)
        }

        return left
    }

    /** 解析逻辑AND表达式 */
    private fun parseLogicalAnd(): ExpressionNode {
        var left = parseEquality()

        while (currentToken == "&&") {
            val operator = currentToken
            nextToken()
            val right = parseEquality()
            left = BinaryOperationNode(left, operator, right)
        }

        return left
    }

    /** 解析相等性表达式 */
    private fun parseEquality(): ExpressionNode {
        var left = parseComparison()

        while (currentToken == "==" || currentToken == "!=") {
            val operator = currentToken
            nextToken()
            val right = parseComparison()
            left = BinaryOperationNode(left, operator, right)
        }

        return left
    }

    /** 解析比较表达式 */
    private fun parseComparison(): ExpressionNode {
        var left = parseAdditive()

        while (currentToken == ">" ||
                currentToken == ">=" ||
                currentToken == "<" ||
                currentToken == "<=") {
            val operator = currentToken
            nextToken()
            val right = parseAdditive()
            left = BinaryOperationNode(left, operator, right)
        }

        return left
    }

    /** 解析加法和减法 */
    private fun parseAdditive(): ExpressionNode {
        var left = parseMultiplicative()

        while (currentToken == "+" || currentToken == "-") {
            val operator = currentToken
            nextToken()
            val right = parseMultiplicative()
            left = BinaryOperationNode(left, operator, right)
        }

        return left
    }

    /** 解析乘法和除法 */
    private fun parseMultiplicative(): ExpressionNode {
        var left = parseExponential()

        while (currentToken == "*" || currentToken == "/" || currentToken == "%") {
            val operator = currentToken
            nextToken()
            val right = parseExponential()
            left = BinaryOperationNode(left, operator, right)
        }

        return left
    }

    /** 解析指数运算 */
    private fun parseExponential(): ExpressionNode {
        var left = parseUnary()

        while (currentToken == "**" || currentToken == "^") {
            val operator = currentToken
            nextToken()
            val right = parseUnary()
            left = BinaryOperationNode(left, operator, right)
        }

        return left
    }

    /** 解析一元操作符 */
    private fun parseUnary(): ExpressionNode {
        if (currentToken == "+" || currentToken == "-" || currentToken == "!") {
            val operator = currentToken
            nextToken()
            val operand = parseUnary()
            return UnaryOperationNode(operator, operand)
        }

        return parseArrayAccess()
    }

    /** 解析数组访问 */
    private fun parseArrayAccess(): ExpressionNode {
        var expr = parsePrimary()

        while (true) {
            if (currentToken == "[") {
                nextToken() // 跳过[
                val index = parseExpression()

                if (currentToken != "]") {
                    throw IllegalArgumentException("Expected ']' in array access")
                }
                nextToken() // 跳过]

                expr = ArrayAccessNode(expr, index)
            } else if (currentToken == "." && peekNextToken() == "length") {
                // 特殊处理 .length 属性访问
                nextToken() // 跳过.
                nextToken() // 跳过length

                expr = FunctionCallNode("length", listOf(expr))
            } else {
                break
            }
        }

        return expr
    }

    /** 解析基本表达式 */
    private fun parsePrimary(): ExpressionNode {
        when (currentTokenType) {
            TokenType.NUMBER -> {
                val value = currentToken.toDouble()
                nextToken()
                return NumberNode(value)
            }
            TokenType.IDENTIFIER -> {
                val identifier = currentToken
                nextToken()

                // 函数调用
                if (currentToken == "(") {
                    nextToken() // 跳过(
                    val args = mutableListOf<ExpressionNode>()

                    if (currentToken != ")") {
                        args.add(parseExpression())

                        while (currentToken == ",") {
                            nextToken() // 跳过,
                            args.add(parseExpression())
                        }
                    }

                    if (currentToken != ")") {
                        throw IllegalArgumentException("Expected ')' in function call")
                    }
                    nextToken() // 跳过)

                    // convert 的单位是字符串常量，按字面量取用；其余实参照常求值。
                    if (identifier.equals("convert", ignoreCase = true)) {
                        require(args.size == 3) {
                            "convert requires 3 arguments: convert(value, fromUnit, toUnit)"
                        }
                        return ConversionNode(
                                value = args[0],
                                fromUnit = args[1].literalText(),
                                toUnit = args[2].literalText()
                        )
                    }

                    return FunctionCallNode(identifier, args)
                }

                // 计算器公开的命名空间函数调用
                if ((identifier == "Math" || identifier == "stats") && currentToken == ".") {
                    nextToken() // 跳过.
                    if (currentTokenType != TokenType.IDENTIFIER) {
                        throw IllegalArgumentException("Expected member name after $identifier.")
                    }
                    val methodName = currentToken
                    nextToken()

                    if (identifier == "Math" &&
                            (methodName == "PI" || methodName == "E") &&
                            currentToken != "(") {
                        return VariableNode(methodName)
                    }

                    if (currentToken != "(") {
                        throw IllegalArgumentException("Expected '(' after $identifier.$methodName")
                    }
                    nextToken() // 跳过(

                    val args = mutableListOf<ExpressionNode>()
                    if (currentToken != ")") {
                        args.add(parseExpression())

                        while (currentToken == ",") {
                            nextToken() // 跳过,
                            args.add(parseExpression())
                        }
                    }

                    if (currentToken != ")") {
                        throw IllegalArgumentException(
                                "Expected ')' in $identifier.$methodName call"
                        )
                    }
                    nextToken() // 跳过)

                    return FunctionCallNode("$identifier.$methodName", args)
                }

                // 变量引用
                return VariableNode(identifier)
            }
            TokenType.LEFT_PAREN -> {
                nextToken() // 跳过(
                val expr = parseExpression()

                if (currentToken != ")") {
                    throw IllegalArgumentException("Expected ')'")
                }
                nextToken() // 跳过)

                return expr
            }
            TokenType.LEFT_BRACKET -> {
                nextToken() // 跳过[
                val elements = mutableListOf<ExpressionNode>()

                if (currentToken != "]") {
                    elements.add(parseExpression())

                    while (currentToken == ",") {
                        nextToken() // 跳过,
                        elements.add(parseExpression())
                    }
                }

                if (currentToken != "]") {
                    throw IllegalArgumentException("Expected ']'")
                }
                nextToken() // 跳过]

                // 创建一个代表数组的节点
                return FunctionCallNode("array", elements)
            }
            TokenType.STRING -> {
                val value = currentToken
                nextToken()
                // 字符串节点处理为一个变量节点
                return VariableNode(value)
            }
            TokenType.TEMPLATE_START -> {
                return parseTemplate()
            }
            else -> {
                throw IllegalArgumentException("Unexpected token: $currentToken")
            }
        }
    }

    /** 解析模板字符串 */
    private fun parseTemplate(): ExpressionNode {
        val parts = mutableListOf<Any>()

        // 添加模板起始部分
        parts.add(currentToken.substring(1)) // 去掉开始的"
        nextToken()

        while (currentTokenType == TokenType.TEMPLATE_MIDDLE ||
                currentTokenType == TokenType.TEMPLATE_END) {
            if (currentTokenType == TokenType.TEMPLATE_MIDDLE) {
                val expr = parseExpression()
                parts.add(expr)
            } else { // TEMPLATE_END
                parts.add(currentToken.substring(0, currentToken.length - 1)) // 去掉结束的"
                nextToken()
                break
            }
        }

        return TemplateStringNode(parts)
    }

    /** 获取下一个词法单元 */
    private fun nextToken() {
        // 跳过空白字符
        while (position < expression.length && Character.isWhitespace(expression[position])) {
            position++
        }

        if (position >= expression.length) {
            currentToken = ""
            currentTokenType = TokenType.EOF
            return
        }

        val c = expression[position]

        when {
            c.isDigit() ||
                    (c == '.' &&
                            position + 1 < expression.length &&
                            expression[position + 1].isDigit()) -> {
                scanNumber()
            }
            c.isLetter() || c == '_' -> {
                scanIdentifier()
            }
            c == '"' || c == '\'' -> {
                scanString(c)
            }
            c == '`' -> {
                scanTemplateString()
            }
            c == '(' -> {
                currentToken = "("
                currentTokenType = TokenType.LEFT_PAREN
                position++
            }
            c == ')' -> {
                currentToken = ")"
                currentTokenType = TokenType.RIGHT_PAREN
                position++
            }
            c == '[' -> {
                currentToken = "["
                currentTokenType = TokenType.LEFT_BRACKET
                position++
            }
            c == ']' -> {
                currentToken = "]"
                currentTokenType = TokenType.RIGHT_BRACKET
                position++
            }
            c == ',' -> {
                currentToken = ","
                currentTokenType = TokenType.COMMA
                position++
            }
            c == '+' ||
                    c == '-' ||
                    c == '*' ||
                    c == '/' ||
                    c == '%' ||
                    c == '^' ||
                    c == '=' ||
                    c == '!' ||
                    c == '>' ||
                    c == '<' ||
                    c == '&' ||
                    c == '|' ||
                    c == '?' ||
                    c == ':' ||
                    c == '.' -> {
                scanOperator()
            }
            else -> {
                throw IllegalArgumentException("Invalid character: $c")
            }
        }
    }

    /** 扫描数字 */
    private fun scanNumber() {
        val start = position
        var hasDot = false

        while (position < expression.length) {
            val c = expression[position]
            if (c.isDigit()) {
                position++
            } else if (c == '.' && !hasDot) {
                hasDot = true
                position++
            } else {
                break
            }
        }

        currentToken = expression.substring(start, position)
        currentTokenType = TokenType.NUMBER
    }

    /** 扫描标识符 */
    private fun scanIdentifier() {
        val start = position

        while (position < expression.length) {
            val c = expression[position]
            if (c.isLetterOrDigit() || c == '_') {
                position++
            } else {
                break
            }
        }

        currentToken = expression.substring(start, position)
        currentTokenType = TokenType.IDENTIFIER
    }

    /** 扫描字符串字面量 */
    private fun scanString(quoteChar: Char) {
        val start = position
        position++ // 跳过开始的引号

        while (position < expression.length) {
            val c = expression[position]
            position++

            if (c == quoteChar) {
                break
            } else if (c == '\\' && position < expression.length) {
                // 处理转义字符
                position++
            }
        }

        currentToken = expression.substring(start, position)
        currentTokenType = TokenType.STRING
    }

    /** 扫描模板字符串 */
    private fun scanTemplateString() {
        val start = position
        position++ // 跳过开始的 `

        // 查找${或者结束的`
        while (position < expression.length) {
            if (position + 1 < expression.length &&
                            expression[position] == '$' &&
                            expression[position + 1] == '{'
            ) {
                currentToken = expression.substring(start, position)
                currentTokenType = TokenType.TEMPLATE_START
                position += 2 // 跳过 ${
                return
            } else if (expression[position] == '`') {
                currentToken = expression.substring(start, position + 1)
                currentTokenType = TokenType.TEMPLATE_END
                position++ // 跳过结束的 `
                return
            }
            position++
        }

        throw IllegalArgumentException("Unclosed template string")
    }

    /** 扫描操作符 */
    private fun scanOperator() {
        val start = position
        val c = expression[position]
        position++

        // 处理多字符操作符
        if (position < expression.length) {
            val nextChar = expression[position]

            if ((c == '+' ||
                            c == '-' ||
                            c == '*' ||
                            c == '/' ||
                            c == '=' ||
                            c == '!' ||
                            c == '>' ||
                            c == '<') && nextChar == '='
            ) {
                position++
            } else if (c == '*' && nextChar == '*') {
                position++
            } else if (c == '&' && nextChar == '&') {
                position++
            } else if (c == '|' && nextChar == '|') {
                position++
            }
        }

        currentToken = expression.substring(start, position)
        currentTokenType = TokenType.OPERATOR
    }

    /**
     * 取实参的单位字面量。
     *
     * `convert(100, "c", "f")` 的字符串实参在词法阶段会变成 [VariableNode]，名字保留引号；
     * 不加引号的 `convert(100, c, f)` 也接受，因为单位只是标签，不是需要求值的标识符。
     * 两种情况都只取字面文本，绝不对单位求值——单位不是变量。
     */
    private fun ExpressionNode.literalText(): String {
        val name = (this as? VariableNode)?.name
        if (name != null) {
            return name.trim('\'', '"')
        }
        throw IllegalArgumentException("convert units must be string literals or bare names")
    }

    /** 查看下一个词法单元但不消费它 */
    private fun peekNextToken(): String {
        val savedPosition = position
        val savedToken = currentToken
        val savedType = currentTokenType

        nextToken()
        val nextToken = currentToken

        // 恢复状态
        position = savedPosition
        currentToken = savedToken
        currentTokenType = savedType

        return nextToken
    }
}
