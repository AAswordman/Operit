package com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion

/**
 * 默认补全提供器
 * 提供基本的关键字补全和变量识别
 */
open class DefaultCompletionProvider : CompletionProvider {
    override fun getCompletionItems(text: CharSequence, position: Int): List<CompletionItem> {
        val prefix = getPrefix(text, position)
        if (prefix.isEmpty()) return emptyList()
        
        // 从文本中提取变量
        val variables = extractVariables(text.toString())
        
        return variables
            .filter { it.startsWith(prefix) }
            .map { 
                CompletionItem(
                    label = it,
                    insertText = it,
                    kind = CompletionItemKind.VARIABLE
                )
            }
    }
    
    /**
     * 从文本中提取变量名
     * 基本实现：查找var、let、const等关键字后的标识符
     */
    protected open fun extractVariables(text: String): List<String> {
        val variables = mutableListOf<String>()
        val variablePattern = "\\b(var|let|const|val)\\s+([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
        
        variablePattern.findAll(text).forEach { matchResult ->
            val variableName = matchResult.groupValues[2]
            if (variableName.isNotEmpty()) {
                variables.add(variableName)
            }
        }
        
        return variables
    }
} 