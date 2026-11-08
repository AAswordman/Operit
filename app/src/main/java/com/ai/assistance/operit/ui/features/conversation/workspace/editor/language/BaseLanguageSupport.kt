package com.ai.assistance.operit.ui.features.conversation.workspace.editor.language

/**
 * 基础语言支持抽象类，提供通用实现
 */
abstract class BaseLanguageSupport : LanguageSupport {
    /**
     * 默认的注释起始标记
     */
    override fun getCommentStart(): List<String> = listOf("//", "/*")
    
    /**
     * 默认的多行注释结束标记
     */
    override fun getMultiLineCommentEnd(): String? = "*/"
    
    /**
     * 默认的字符串分隔符检查
     */
    override fun isStringDelimiter(char: Char): Boolean = char == '"' || char == '\''
    
    /**
     * 默认的字符串转义字符
     */
    override fun getStringEscapeChar(): Char = '\\'
    
    /**
     * 默认的内置函数集合
     */
    override fun getBuiltInFunctions(): Set<String> = emptySet()
    
    /**
     * 默认的内置类型集合
     */
    override fun getBuiltInTypes(): Set<String> = emptySet()

    /**
     * 默认的内置变量集合
     */
    override fun getBuiltInVariables(): Set<String> = emptySet()
} 
