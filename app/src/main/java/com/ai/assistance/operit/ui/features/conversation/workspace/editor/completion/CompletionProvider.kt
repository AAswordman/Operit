package com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion

import android.text.Editable

/**
 * 代码补全项
 */
data class CompletionItem(
    val label: String,        // 显示的文本
    val insertText: String,   // 插入的文本
    val detail: String? = null, // 详细信息
    val kind: CompletionItemKind = CompletionItemKind.TEXT // 补全项类型
)

/**
 * 补全项类型
 */
enum class CompletionItemKind {
    TEXT,
    KEYWORD,
    FUNCTION,
    VARIABLE,
    CLASS,
    PROPERTY,
    METHOD,
    SNIPPET
}

/**
 * 代码补全提供器接口
 */
interface CompletionProvider {
    /**
     * 获取补全项
     * @param text 当前文本
     * @param position 光标位置
     * @return 补全项列表
     */
    fun getCompletionItems(text: CharSequence, position: Int): List<CompletionItem>
    
    /**
     * 获取补全项 (兼容String类型)
     */
    fun getCompletionItems(text: String, position: Int): List<CompletionItem> {
        return getCompletionItems(text as CharSequence, position)
    }
    
    /**
     * 获取触发字符
     * 当输入这些字符时会触发补全
     */
    fun getTriggerCharacters(): Set<Char> = setOf('.', ':')
    
    /**
     * 是否应该显示补全
     * @param text 当前文本
     * @param position 光标位置
     */
    fun shouldShowCompletion(text: CharSequence, position: Int): Boolean {
        // 默认实现：当输入字母或数字时显示补全
        if (position <= 0 || position > text.length) return false
        val currentChar = text[position - 1]
        return currentChar.isLetterOrDigit() || currentChar in getTriggerCharacters()
    }
    
    /**
     * 是否应该显示补全 (兼容String类型)
     */
    fun shouldShowCompletion(text: String, position: Int): Boolean {
        return shouldShowCompletion(text as CharSequence, position)
    }
    
    /**
     * 获取当前前缀
     * @param text 当前文本
     * @param position 光标位置
     */
    fun getPrefix(text: CharSequence, position: Int): String {
        if (position <= 0) return ""
        
        var start = position - 1
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_')) {
            start--
        }
        
        return if (start < position - 1) {
            text.subSequence(start + 1, position).toString()
        } else {
            ""
        }
    }
    
    /**
     * 获取当前前缀 (兼容String类型)
     */
    fun getPrefix(text: String, position: Int): String {
        return getPrefix(text as CharSequence, position)
    }
} 