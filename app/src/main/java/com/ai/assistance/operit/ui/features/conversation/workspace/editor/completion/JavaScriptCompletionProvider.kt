package com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.language.LanguageFactory

/**
 * JavaScript补全提供器
 */
class JavaScriptCompletionProvider : DefaultCompletionProvider() {
    // 获取JavaScript语言支持
    private val languageSupport = LanguageFactory.getLanguageSupport("javascript")
    
    // JavaScript常用API和方法
    private val commonMethods = mapOf(
        "console" to listOf("log", "error", "warn", "info", "debug", "trace", "time", "timeEnd"),
        "document" to listOf("getElementById", "getElementsByClassName", "querySelector", "querySelectorAll", "createElement", "addEventListener"),
        "array" to listOf("push", "pop", "shift", "unshift", "slice", "splice", "map", "filter", "reduce", "forEach", "find", "some", "every")
    )
    
    override fun getCompletionItems(text: CharSequence, position: Int): List<CompletionItem> {
        // 获取前缀
        val prefix = getPrefix(text, position)
        
        // 获取上下文，判断是否在对象方法调用场景
        val context = getContext(text, position)
        
        return when {
            // 对象方法调用场景
            context.contains(".") -> {
                val objectName = context.substringBefore(".").trim().lowercase()
                val methodPrefix = context.substringAfter(".").trim()
                
                // 根据对象名获取可能的方法列表
                val methods = commonMethods[objectName] ?: return emptyList()
                
                // 过滤符合前缀的方法
                methods.filter { it.startsWith(methodPrefix) }
                    .map { 
                        CompletionItem(
                            label = it,
                            insertText = it,
                            kind = CompletionItemKind.METHOD
                        )
                    }
            }
            
            // 普通补全场景
            prefix.isNotEmpty() -> {
                // 获取所有可能的补全项
                val completions = mutableListOf<CompletionItem>()
                
                // 添加关键字
                languageSupport?.getKeywords()?.filter { it.startsWith(prefix) }?.forEach {
                    completions.add(CompletionItem(
                        label = it,
                        insertText = it,
                        kind = CompletionItemKind.KEYWORD
                    ))
                }
                
                // 添加内置函数
                languageSupport?.getBuiltInFunctions()?.filter { it.startsWith(prefix) }?.forEach {
                    completions.add(CompletionItem(
                        label = it,
                        insertText = it,
                        kind = CompletionItemKind.FUNCTION
                    ))
                }
                
                // 添加内置变量
                languageSupport?.getBuiltInVariables()?.filter { it.startsWith(prefix) }?.forEach {
                    completions.add(CompletionItem(
                        label = it,
                        insertText = it,
                        kind = CompletionItemKind.VARIABLE
                    ))
                }
                
                // 添加常见代码片段
                if ("fun".startsWith(prefix)) {
                    completions.add(CompletionItem(
                        label = "function",
                        insertText = "function ${1}(${2}) {\n  ${3}\n}",
                        kind = CompletionItemKind.SNIPPET
                    ))
                }
                
                if ("if".startsWith(prefix)) {
                    completions.add(CompletionItem(
                        label = "if",
                        insertText = "if (${1}) {\n  ${2}\n}",
                        kind = CompletionItemKind.SNIPPET
                    ))
                }
                
                // 添加从文件中提取的变量
                val variables = extractVariables(text.toString())
                variables.filter { it.startsWith(prefix) }.forEach {
                    completions.add(CompletionItem(
                        label = it,
                        insertText = it,
                        kind = CompletionItemKind.VARIABLE
                    ))
                }
                
                completions
            }
            
            else -> emptyList()
        }
    }
    
    /**
     * 获取当前上下文
     * 例如：对于"console.l"，返回"console.l"
     */
    private fun getContext(text: CharSequence, position: Int): String {
        if (position <= 0) return ""
        
        var start = position - 1
        // 向前查找，直到遇到空格、换行或者分号
        while (start >= 0 && text[start] != ' ' && text[start] != '\n' && text[start] != ';') {
            start--
        }
        
        return if (start < position - 1) {
            text.subSequence(start + 1, position).toString()
        } else {
            ""
        }
    }
    
    override fun getTriggerCharacters(): Set<Char> = setOf('.', '(')
    
    /**
     * 重写变量提取方法，针对JavaScript语法
     */
    override fun extractVariables(text: String): List<String> {
        val variables = mutableListOf<String>()
        
        // JavaScript变量声明模式: var/let/const name = value
        val varPattern = "\\b(var|let|const)\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)".toRegex()
        varPattern.findAll(text).forEach { matchResult ->
            val varName = matchResult.groupValues[2]
            if (varName.isNotEmpty()) {
                variables.add(varName)
            }
        }
        
        // 函数声明模式: function name(params)
        val funcPattern = "\\bfunction\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)".toRegex()
        funcPattern.findAll(text).forEach { matchResult ->
            val funcName = matchResult.groupValues[1]
            if (funcName.isNotEmpty()) {
                variables.add(funcName)
            }
        }
        
        // 函数参数模式: function name(param1, param2)
        val paramPattern = "\\bfunction\\s+(?:[a-zA-Z_$][a-zA-Z0-9_$]*)?\\s*\\(([^)]*)\\)".toRegex()
        paramPattern.findAll(text).forEach { matchResult ->
            val params = matchResult.groupValues[1].split(",")
            params.forEach { param ->
                val trimmedParam = param.trim()
                if (trimmedParam.isNotEmpty() && trimmedParam.matches("[a-zA-Z_$][a-zA-Z0-9_$]*".toRegex())) {
                    variables.add(trimmedParam)
                }
            }
        }
        
        return variables
    }
} 