package com.ai.assistance.operit.ui.features.conversation.workspace.editor.language

/**
 * 语言支持注册表，用于管理所有支持的语言
 */
object LanguageSupportRegistry {
    private val languageSupports = mutableMapOf<String, LanguageSupport>()
    private val extensionMap = mutableMapOf<String, String>()
    
    /**
     * 注册一个语言支持
     */
    fun register(languageSupport: LanguageSupport) {
        val name = languageSupport.getName().lowercase()
        languageSupports[name] = languageSupport
        
        // 注册文件扩展名到语言的映射
        for (extension in languageSupport.getFileExtensions()) {
            extensionMap[extension.lowercase()] = name
        }
    }
    
    /**
     * 根据语言名称获取语言支持
     */
    fun getLanguageSupport(language: String): LanguageSupport? {
        return languageSupports[language.lowercase()]
    }
    
    /**
     * 根据文件名获取语言支持
     */
    fun getLanguageSupportForFile(fileName: String): LanguageSupport? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val language = extensionMap[extension] ?: return null
        return languageSupports[language]
    }
    
    /**
     * 获取所有支持的语言
     */
    fun getSupportedLanguages(): Set<String> {
        return languageSupports.keys
    }
    
    /**
     * 初始化默认语言支持
     */
    fun init() {
        // 这里会在应用启动时注册所有语言支持
        // 将在各个语言实现类中调用
    }
} 