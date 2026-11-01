package com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.language.LanguageFactory

/**
 * Kotlin补全提供器
 */
class KotlinCompletionProvider : DefaultCompletionProvider() {
    // 获取Kotlin语言支持
    private val languageSupport = LanguageFactory.getLanguageSupport("kotlin")
    
    // Kotlin常用API和方法
    private val commonMethods = mapOf(
        "list" to listOf("add", "remove", "size", "isEmpty", "contains", "forEach", "map", "filter", "first", "last"),
        "string" to listOf("length", "isEmpty", "substring", "replace", "split", "trim", "toInt", "toLong", "toDouble")
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
                
                // 添加常见代码片段
                if ("fun".startsWith(prefix)) {
                    completions.add(CompletionItem(
                        label = "fun",
                        insertText = "fun ${1}(${2}) {\n  ${3}\n}",
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
    
    override fun getTriggerCharacters(): Set<Char> = setOf('.', '(', ':')
    
    /**
     * 重写变量提取方法，针对Kotlin语法
     */
    override fun extractVariables(text: String): List<String> {
        val variables = mutableListOf<String>()
        
        // Kotlin变量声明模式: val/var name: Type = value
        val varPattern = "\\b(val|var)\\s+([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
        varPattern.findAll(text).forEach { matchResult ->
            val varName = matchResult.groupValues[2]
            if (varName.isNotEmpty()) {
                variables.add(varName)
            }
        }
        
        // 函数声明模式: fun name(params): ReturnType
        val funcPattern = "\\bfun\\s+([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
        funcPattern.findAll(text).forEach { matchResult ->
            val funcName = matchResult.groupValues[1]
            if (funcName.isNotEmpty()) {
                variables.add(funcName)
            }
        }
        
        // 函数参数模式: fun name(param1: Type, param2: Type)
        val paramPattern = "\\bfun\\s+(?:[a-zA-Z_][a-zA-Z0-9_]*)?\\s*\\(([^)]*)\\)".toRegex()
        paramPattern.findAll(text).forEach { matchResult ->
            val paramsText = matchResult.groupValues[1]
            // 分割参数列表
            val params = paramsText.split(",")
            params.forEach { param ->
                val trimmedParam = param.trim()
                if (trimmedParam.isNotEmpty()) {
                    // 提取参数名 (param: Type 中的 param)
                    val paramName = trimmedParam.split(":")[0].trim()
                    if (paramName.matches("[a-zA-Z_][a-zA-Z0-9_]*".toRegex())) {
                        variables.add(paramName)
                    }
                }
            }
        }
        
        // 类声明模式: class Name
        val classPattern = "\\bclass\\s+([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
        classPattern.findAll(text).forEach { matchResult ->
            val className = matchResult.groupValues[1]
            if (className.isNotEmpty()) {
                variables.add(className)
            }
        }
        
        return variables
    }
} 