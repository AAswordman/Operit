package com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion

/**
 * HTML补全提供器
 */
class HtmlCompletionProvider : DefaultCompletionProvider() {
    // HTML标签
    private val htmlTags = listOf(
        "html", "head", "body", "div", "span", "p", "h1", "h2", "h3", "h4", "h5", "h6",
        "a", "img", "ul", "ol", "li", "table", "tr", "td", "th", "form", "input", "button",
        "select", "option", "textarea", "script", "style", "link", "meta", "title", "section",
        "article", "header", "footer", "nav", "aside", "main", "canvas", "video", "audio"
    )
    
    // HTML属性
    private val htmlAttributes = listOf(
        "id", "class", "style", "href", "src", "alt", "width", "height", "type", "value",
        "placeholder", "name", "action", "method", "target", "rel", "onclick", "onchange",
        "onsubmit", "onload", "title", "disabled", "checked", "selected", "required", "readonly"
    )
    
    override fun getCompletionItems(text: CharSequence, position: Int): List<CompletionItem> {
        val prefix = getPrefix(text, position)
        if (prefix.isEmpty()) return emptyList()
        
        val completions = mutableListOf<CompletionItem>()
        
        // 判断当前是否在标签内部
        val isInTagContext = isInTagContext(text, position)
        val isInAttributeContext = isInAttributeContext(text, position)
        
        when {
            // 在标签开始位置 (例如: <di|)
            isInTagContext && !isInAttributeContext -> {
                htmlTags.filter { it.startsWith(prefix) }.forEach {
                    completions.add(CompletionItem(
                        label = it,
                        insertText = it,
                        kind = CompletionItemKind.KEYWORD
                    ))
                }
            }
            
            // 在属性位置 (例如: <div cl|)
            isInAttributeContext -> {
                htmlAttributes.filter { it.startsWith(prefix) }.forEach {
                    completions.add(CompletionItem(
                        label = it,
                        insertText = "$it=\"$1\"",
                        kind = CompletionItemKind.PROPERTY
                    ))
                }
            }
            
            // 普通位置，提供标签补全
            else -> {
                htmlTags.filter { it.startsWith(prefix) }.forEach {
                    completions.add(CompletionItem(
                        label = it,
                        insertText = "<$it>$1</$it>",
                        kind = CompletionItemKind.SNIPPET
                    ))
                }
                
                // 添加从文件中提取的ID和类名
                val idAndClasses = extractIdAndClasses(text.toString())
                idAndClasses.filter { it.startsWith(prefix) }.forEach {
                    completions.add(CompletionItem(
                        label = it,
                        insertText = it,
                        kind = CompletionItemKind.VARIABLE
                    ))
                }
            }
        }
        
        return completions
    }
    
    /**
     * 判断当前位置是否在标签内部
     * 例如: <div |> 返回true
     */
    private fun isInTagContext(text: CharSequence, position: Int): Boolean {
        if (position <= 0) return false
        
        var start = position - 1
        var foundOpenBracket = false
        
        // 向前查找，直到找到 < 或 >
        while (start >= 0) {
            when (text[start]) {
                '>' -> return false // 找到了结束标签，不在标签内部
                '<' -> {
                    foundOpenBracket = true
                    break
                }
            }
            start--
        }
        
        return foundOpenBracket
    }
    
    /**
     * 判断当前位置是否在属性上下文中
     * 例如: <div cl|> 返回true
     */
    private fun isInAttributeContext(text: CharSequence, position: Int): Boolean {
        if (position <= 0) return false
        if (!isInTagContext(text, position)) return false
        
        var start = position - 1
        
        // 向前查找，直到找到标签名后的空格
        while (start >= 0) {
            val c = text[start]
            if (c == '<') return false // 到达标签开始，说明还在标签名中
            if (c == ' ') return true // 找到空格，说明在属性上下文中
            start--
        }
        
        return false
    }
    
    /**
     * 从HTML文本中提取ID和类名
     */
    private fun extractIdAndClasses(text: String): List<String> {
        val result = mutableListOf<String>()
        
        // 提取ID: id="someId"
        val idPattern = "id=[\"']([^\"']+)[\"']".toRegex()
        idPattern.findAll(text).forEach { matchResult ->
            val id = matchResult.groupValues[1]
            if (id.isNotEmpty()) {
                result.add(id)
                result.add("#$id") // 添加CSS选择器形式
            }
        }
        
        // 提取类名: class="class1 class2"
        val classPattern = "class=[\"']([^\"']+)[\"']".toRegex()
        classPattern.findAll(text).forEach { matchResult ->
            val classNames = matchResult.groupValues[1].split(" ")
            classNames.forEach { className ->
                if (className.isNotEmpty()) {
                    result.add(className)
                    result.add(".$className") // 添加CSS选择器形式
                }
            }
        }
        
        return result
    }
    
    override fun getTriggerCharacters(): Set<Char> = setOf('<', ' ', '"', '\'', '.')
    
    override fun getPrefix(text: CharSequence, position: Int): String {
        if (position <= 0) return ""
        
        var start = position - 1
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_' || text[start] == '-' || text[start] == '#' || text[start] == '.')) {
            start--
        }
        
        return if (start < position - 1) {
            text.subSequence(start + 1, position).toString()
        } else {
            ""
        }
    }
} 