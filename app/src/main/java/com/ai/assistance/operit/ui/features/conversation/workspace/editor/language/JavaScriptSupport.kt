package com.ai.assistance.operit.ui.features.conversation.workspace.editor.language

/**
 * JavaScript语言支持
 */
class JavaScriptSupport : BaseLanguageSupport() {
    companion object {
        private val KEYWORDS = setOf(
            "function", "var", "let", "const", "if", "else", "for", "while", "do", "switch",
            "case", "break", "continue", "return", "try", "catch", "finally", "throw",
            "new", "delete", "typeof", "instanceof", "void", "this", "super", "class",
            "extends", "import", "export", "default", "async", "await", "yield", "static",
            "get", "set", "in", "of", "with", "debugger"
        )
        
        private val BUILT_IN_TYPES = setOf(
            "Array", "Boolean", "Date", "Error", "Function", "JSON", "Math", "Number",
            "Object", "Promise", "RegExp", "String", "Symbol", "Map", "Set", "WeakMap",
            "WeakSet", "ArrayBuffer", "DataView", "Int8Array", "Uint8Array", "Uint8ClampedArray",
            "Int16Array", "Uint16Array", "Int32Array", "Uint32Array", "Float32Array", "Float64Array"
        )

        private val BUILT_IN_VARIABLES = setOf(
            "console", "window", "document", "navigator", "location", "history", "screen",
            "localStorage", "sessionStorage", "performance"
        )
        
        private val BUILT_IN_FUNCTIONS = setOf(
            "setTimeout", "clearTimeout", "setInterval", "clearInterval",
            "encodeURI", "decodeURI", "encodeURIComponent", "decodeURIComponent",
            "parseInt", "parseFloat", "isNaN", "isFinite", "eval", "alert", "confirm", "prompt",
            "btoa", "atob", "fetch"
        )
        
        private val FILE_EXTENSIONS = listOf("js", "mjs", "cjs")
        
        init {
            // 注册语言支持
            LanguageSupportRegistry.register(JavaScriptSupport())
        }
    }
    
    override fun getName(): String = "javascript"
    
    override fun getKeywords(): Set<String> = KEYWORDS
    
    override fun getBuiltInTypes(): Set<String> = BUILT_IN_TYPES
    
    override fun getBuiltInFunctions(): Set<String> = BUILT_IN_FUNCTIONS

    override fun getBuiltInVariables(): Set<String> = BUILT_IN_VARIABLES
    
    override fun getFileExtensions(): List<String> = FILE_EXTENSIONS
} 