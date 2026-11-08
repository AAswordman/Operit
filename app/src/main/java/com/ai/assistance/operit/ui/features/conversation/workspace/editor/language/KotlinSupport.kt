package com.ai.assistance.operit.ui.features.conversation.workspace.editor.language

/**
 * Kotlin语言支持
 */
class KotlinSupport : BaseLanguageSupport() {
    companion object {
        private val KEYWORDS = setOf(
            "package", "as", "typealias", "class", "this", "super", "val", "var", "fun", "for",
            "null", "true", "false", "is", "in", "throw", "return", "break", "continue", "object",
            "if", "else", "while", "do", "try", "when", "interface", "typeof", "by", "catch",
            "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import",
            "init", "param", "property", "receiver", "set", "setparam", "where", "actual",
            "abstract", "annotation", "companion", "const", "crossinline", "data", "enum",
            "expect", "external", "final", "infix", "inline", "inner", "internal", "lateinit",
            "noinline", "open", "operator", "out", "override", "private", "protected", "public",
            "reified", "sealed", "suspend", "tailrec", "vararg"
        )
        
        private val BUILT_IN_TYPES = setOf(
            "Any", "Unit", "String", "Int", "Boolean", "Char", "Byte", "Short",
            "Long", "Double", "Float", "Array", "List", "Map", "Set", "Pair",
            "Triple", "Nothing", "Collection", "MutableList", "MutableMap", "MutableSet"
        )
        
        private val BUILT_IN_FUNCTIONS = setOf(
            "apply", "let", "run", "with", "also", "takeIf", "takeUnless", "repeat",
            "lazy", "lazyOf", "arrayOf", "listOf", "mutableListOf", "setOf", "mutableSetOf",
            "mapOf", "mutableMapOf", "println", "print", "require", "check", "error",
            "TODO", "run", "runCatching", "use"
        )
        
        private val FILE_EXTENSIONS = listOf("kt", "kts")
        
        init {
            // 注册语言支持
            LanguageSupportRegistry.register(KotlinSupport())
        }
    }
    
    override fun getName(): String = "kotlin"
    
    override fun getKeywords(): Set<String> = KEYWORDS
    
    override fun getBuiltInTypes(): Set<String> = BUILT_IN_TYPES
    
    override fun getBuiltInFunctions(): Set<String> = BUILT_IN_FUNCTIONS
    
    override fun getFileExtensions(): List<String> = FILE_EXTENSIONS
} 