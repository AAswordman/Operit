package com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion

/**
 * 补全提供器工厂
 * 根据语言类型返回对应的补全提供器
 */
object CompletionProviderFactory {
    /**
     * 获取补全提供器
     * @param language 语言类型
     */
    fun getProvider(language: String): CompletionProvider {
        return when (language.lowercase()) {
            "javascript" -> JavaScriptCompletionProvider()
            "kotlin" -> KotlinCompletionProvider()
            "html" -> HtmlCompletionProvider()
            "dart" -> DartCompletionProvider()
            else -> DefaultCompletionProvider()
        }
    }
} 
