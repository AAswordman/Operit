package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import com.ai.assistance.operit.util.logging.AppLogger

/**
 * 代码格式化工具，支持 JavaScript、CSS、HTML 的基本格式化
 */
object CodeFormatter {
    private const val TAG = "CodeFormatter"
    
    /**
     * 根据语言类型格式化代码
     * @param code 原始代码
     * @param language 语言类型
     * @return 格式化后的代码
     */
    fun format(code: String, language: String): String {
        return try {
            when (language.lowercase()) {
                "javascript", "js" -> formatJavaScript(code)
                "css" -> formatCSS(code)
                "html", "htm" -> formatHTML(code)
                else -> {
                    AppLogger.w(TAG, "不支持的语言类型: $language")
                    code
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "格式化代码时出错", e)
            code // 出错时返回原始代码
        }
    }
    
    /**
     * 格式化 JavaScript 代码
     */
    private fun formatJavaScript(code: String): String {
        val result = StringBuilder()
        var indentLevel = 0
        var i = 0
        var inString = false
        var stringChar = ' '
        var inComment = false
        var inMultiLineComment = false
        
        while (i < code.length) {
            val char = code[i]
            
            // 处理多行注释
            if (!inString && i + 1 < code.length && code.substring(i, i + 2) == "/*") {
                inMultiLineComment = true
                result.append("/*")
                i += 2
                continue
            }
            
            if (inMultiLineComment) {
                result.append(char)
                if (i + 1 < code.length && code.substring(i, i + 2) == "*/") {
                    result.append('/')
                    inMultiLineComment = false
                    i += 2
                    continue
                }
                i++
                continue
            }
            
            // 处理单行注释
            if (!inString && i + 1 < code.length && code.substring(i, i + 2) == "//") {
                inComment = true
                result.append("//")
                i += 2
                continue
            }
            
            if (inComment) {
                result.append(char)
                if (char == '\n') {
                    inComment = false
                }
                i++
                continue
            }
            
            // 处理字符串
            if (char == '"' || char == '\'' || char == '`') {
                if (!inString) {
                    inString = true
                    stringChar = char
                } else if (char == stringChar && (i == 0 || code[i - 1] != '\\')) {
                    inString = false
                }
                result.append(char)
                i++
                continue
            }
            
            if (inString) {
                result.append(char)
                i++
                continue
            }
            
            // 处理缩进
            when (char) {
                '{', '[' -> {
                    result.append(char)
                    
                    // 检查是否是空的 {} 或 []
                    var nextNonWhitespace = i + 1
                    while (nextNonWhitespace < code.length && code[nextNonWhitespace] in listOf(' ', '\t', '\n', '\r')) {
                        nextNonWhitespace++
                    }
                    
                    val isEmptyBracket = nextNonWhitespace < code.length && 
                        ((char == '{' && code[nextNonWhitespace] == '}') || 
                         (char == '[' && code[nextNonWhitespace] == ']'))
                    
                    if (!isEmptyBracket) {
                        indentLevel++
                        // 换行但不立即添加缩进
                        if (i + 1 < code.length && code[i + 1] != '\n') {
                            result.append('\n')
                        }
                    }
                }
                '}', ']' -> {
                    // 检查是否是空的 {} 或 []（向前查找对应的开括号）
                    var lastNonWhitespace = result.length - 1
                    while (lastNonWhitespace >= 0 && result[lastNonWhitespace] in listOf(' ', '\t', '\n', '\r')) {
                        lastNonWhitespace--
                    }
                    
                    val isEmptyBracket = lastNonWhitespace >= 0 &&
                        ((char == '}' && result[lastNonWhitespace] == '{') ||
                         (char == ']' && result[lastNonWhitespace] == '['))
                    
                    if (!isEmptyBracket) {
                        indentLevel = maxOf(0, indentLevel - 1)
                        // 如果前面不是换行，添加换行
                        if (result.isNotEmpty() && result.last() != '\n') {
                            result.append('\n')
                        }
                        // 删除行尾的空格（如果有的话）
                        while (result.length > 1 && result[result.length - 2] == ' ') {
                            result.setLength(result.length - 1)
                            result.append('\n')
                        }
                        // 添加缩进
                        result.append("    ".repeat(indentLevel))
                    } else {
                        // 空括号，移除中间的空白
                        while (result.isNotEmpty() && result.last() in listOf(' ', '\t', '\n', '\r')) {
                            result.setLength(result.length - 1)
                        }
                    }
                    result.append(char)
                }
                ';' -> {
                    result.append(char)
                    // 分号后换行，但不立即添加缩进（缩进在实际内容前添加）
                    if (i + 1 < code.length && code[i + 1] != '\n') {
                        result.append('\n')
                    }
                }
                '\n', '\r' -> {
                    // 只保留单个换行，不添加缩进（缩进在实际内容前添加）
                    if (result.isNotEmpty() && result.last() != '\n') {
                        result.append('\n')
                    }
                }
                ' ', '\t' -> {
                    // 跳过多余空格
                    if (result.isNotEmpty() && result.last() != ' ' && result.last() != '\n') {
                        result.append(' ')
                    }
                }
                else -> {
                    // 在实际内容前添加缩进
                    if (result.isNotEmpty() && result.last() == '\n') {
                        result.append("    ".repeat(indentLevel))
                    }
                    result.append(char)
                }
            }
            
            i++
        }
        
        return result.toString().trim()
    }
    
    /**
     * 格式化 CSS 代码
     */
    private fun formatCSS(code: String): String {
        val result = StringBuilder()
        var indentLevel = 0
        var i = 0
        var inComment = false
        
        while (i < code.length) {
            val char = code[i]
            
            // 处理注释
            if (!inComment && i + 1 < code.length && code.substring(i, i + 2) == "/*") {
                inComment = true
                result.append("/*")
                i += 2
                continue
            }
            
            if (inComment) {
                result.append(char)
                if (i + 1 < code.length && code.substring(i, i + 2) == "*/") {
                    result.append('/')
                    inComment = false
                    i += 2
                    continue
                }
                i++
                continue
            }
            
            // 处理缩进
            when (char) {
                '{' -> {
                    result.append(" {")
                    indentLevel++
                    result.append('\n')
                    result.append("    ".repeat(indentLevel))
                }
                '}' -> {
                    indentLevel = maxOf(0, indentLevel - 1)
                    // 移除前面的空格
                    while (result.isNotEmpty() && (result.last() == ' ' || result.last() == '\t')) {
                        result.setLength(result.length - 1)
                    }
                    if (result.isNotEmpty() && result.last() != '\n') {
                        result.append('\n')
                    }
                    result.append("    ".repeat(indentLevel))
                    result.append('}')
                    result.append('\n')
                    result.append("    ".repeat(indentLevel))
                }
                ';' -> {
                    result.append(';')
                    result.append('\n')
                    result.append("    ".repeat(indentLevel))
                }
                ':' -> {
                    result.append(": ")
                }
                '\n', '\r' -> {
                    // 跳过换行，由格式化器控制
                }
                ' ', '\t' -> {
                    // 跳过多余空格
                    if (result.isNotEmpty() && result.last() != ' ' && result.last() != '\n' && result.last() != ':') {
                        result.append(' ')
                    }
                }
                else -> {
                    result.append(char)
                }
            }
            
            i++
        }
        
        return result.toString().trim()
    }
    
    /**
     * 格式化 HTML 代码
     */
    private fun formatHTML(code: String): String {
        val result = StringBuilder()
        var indentLevel = 0
        var i = 0
        var inTag = false
        var inComment = false
        var inScript = false
        var inStyle = false
        var inString = false
        var stringChar = ' '
        
        // 自闭合标签和内联标签列表
        val selfClosingTags = setOf("br", "img", "input", "hr", "meta", "link", "area", "base", "col", "embed", "param", "source", "track", "wbr")
        val inlineTags = setOf("span", "a", "strong", "em", "b", "i", "u", "small", "code", "kbd", "var", "samp", "sub", "sup", "mark", "del", "ins", "abbr", "cite", "dfn", "q", "time")
        
        while (i < code.length) {
            val char = code[i]
            
            // 处理字符串（在标签属性中）
            if (inTag && (char == '"' || char == '\'' || char == '`')) {
                if (!inString) {
                    inString = true
                    stringChar = char
                } else if (char == stringChar && (i == 0 || code[i - 1] != '\\')) {
                    inString = false
                }
                result.append(char)
                i++
                continue
            }
            
            if (inString) {
                result.append(char)
                i++
                continue
            }
            
            // 处理注释
            if (!inComment && !inScript && !inStyle && i + 3 < code.length && code.substring(i, i + 4) == "<!--") {
                inComment = true
                if (result.isNotEmpty() && result.last() != '\n') {
                    result.append('\n')
                }
                result.append("    ".repeat(indentLevel))
                result.append("<!--")
                i += 4
                continue
            }
            
            if (inComment) {
                result.append(char)
                if (i + 2 < code.length && code.substring(i, i + 3) == "-->") {
                    result.append("->")
                    inComment = false
                    i += 3
                    continue
                }
                i++
                continue
            }
            
            // 处理script和style标签内的内容
            if (inScript) {
                result.append(char)
                if (char == '<' && i + 8 < code.length && code.substring(i, i + 9).lowercase() == "</script>") {
                    inScript = false
                }
                i++
                continue
            }
            
            if (inStyle) {
                result.append(char)
                if (char == '<' && i + 7 < code.length && code.substring(i, i + 8).lowercase() == "</style>") {
                    inStyle = false
                }
                i++
                continue
            }
            
            // 处理DOCTYPE
            if (!inTag && char == '<' && i + 8 < code.length && code.substring(i, i + 9).lowercase() == "<!doctype") {
                // 找到DOCTYPE结束位置
                val endPos = code.indexOf('>', i)
                if (endPos != -1) {
                    result.append(code.substring(i, endPos + 1))
                    result.append('\n')
                    i = endPos + 1
                    continue
                }
            }
            
            // 处理标签
            if (char == '<') {
                inTag = true
                val isClosingTag = i + 1 < code.length && code[i + 1] == '/'
                
                // 获取标签名
                var tagEnd = i + 1
                if (isClosingTag) tagEnd++
                while (tagEnd < code.length && code[tagEnd] != ' ' && code[tagEnd] != '>' && code[tagEnd] != '\n') {
                    tagEnd++
                }
                val tagName = code.substring(if (isClosingTag) i + 2 else i + 1, tagEnd).lowercase()
                
                // 闭合标签减少缩进
                if (isClosingTag && tagName !in inlineTags) {
                    indentLevel = maxOf(0, indentLevel - 1)
                }
                
                // 添加换行和缩进（除非是内联标签）
                if (tagName !in inlineTags) {
                    if (result.isNotEmpty() && result.last() != '\n') {
                        result.append('\n')
                    }
                    result.append("    ".repeat(indentLevel))
                }
                result.append(char)
                
                // 检测script和style标签
                if (!isClosingTag && tagName == "script") {
                    inScript = true
                } else if (!isClosingTag && tagName == "style") {
                    inStyle = true
                }
            } else if (char == '>') {
                result.append(char)
                
                // 获取刚刚闭合的标签名
                val tagStart = result.lastIndexOf('<')
                if (tagStart != -1 && inTag) {
                    val tagContent = result.substring(tagStart + 1, result.length - 1).trim()
                    val isClosingTag = tagContent.startsWith('/')
                    val isSelfClosing = tagContent.endsWith('/') || result[result.length - 2] == '/'
                    
                    var tagNameEnd = 0
                    for (j in (if (isClosingTag) 1 else 0) until tagContent.length) {
                        if (tagContent[j] in listOf(' ', '/', '>')) {
                            tagNameEnd = j
                            break
                        }
                    }
                    if (tagNameEnd == 0) tagNameEnd = tagContent.length
                    val tagName = tagContent.substring(if (isClosingTag) 1 else 0, tagNameEnd).lowercase()
                    
                    // 开始标签且非自闭合且非内联标签，增加缩进
                    if (!isClosingTag && !isSelfClosing && tagName !in selfClosingTags && tagName !in inlineTags) {
                        indentLevel++
                    }
                }
                
                inTag = false
            } else if (inTag) {
                result.append(char)
            } else {
                // 处理标签之间的内容
                if (char !in listOf('\n', '\r', '\t')) {
                    if (char == ' ') {
                        // 跳过多余空格
                        if (result.isNotEmpty() && result.last() != ' ' && result.last() != '\n') {
                            result.append(char)
                        }
                    } else {
                        result.append(char)
                    }
                }
            }
            
            i++
        }
        
        return result.toString().trim()
    }
}
