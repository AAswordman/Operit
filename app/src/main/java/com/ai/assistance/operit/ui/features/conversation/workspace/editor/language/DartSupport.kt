package com.ai.assistance.operit.ui.features.conversation.workspace.editor.language

/**
 * Dart 语言支持
 */
class DartSupport : BaseLanguageSupport() {
    companion object {
        private val KEYWORDS =
            setOf(
                "abstract", "as", "assert", "async", "await", "base", "break", "case",
                "catch", "class", "const", "continue", "covariant", "default", "deferred",
                "do", "dynamic", "else", "enum", "export", "extends", "extension", "external",
                "factory", "false", "final", "finally", "for", "Function", "get", "hide",
                "if", "implements", "import", "in", "interface", "is", "late", "library",
                "mixin", "new", "null", "of", "on", "operator", "part", "required",
                "rethrow", "return", "sealed", "set", "show", "static", "super", "switch",
                "sync", "this", "throw", "true", "try", "typedef", "var", "void", "when",
                "while", "with", "yield"
            )

        private val BUILT_IN_TYPES =
            setOf(
                "int", "double", "num", "bool", "String", "Object", "dynamic", "Never",
                "Future", "Stream", "List", "Map", "Set", "Iterable", "Duration", "DateTime",
                "RegExp", "Pattern", "Uri", "Widget", "BuildContext", "Key", "State",
                "StatelessWidget", "StatefulWidget"
            )

        private val BUILT_IN_FUNCTIONS =
            setOf(
                "print", "runApp", "setState", "debugPrint", "identical", "scheduleMicrotask",
                "Future", "Stream", "SizedBox", "Padding", "Container", "Center", "Column",
                "Row", "Expanded", "Text", "Icon", "Scaffold", "MaterialApp"
            )

        private val BUILT_IN_VARIABLES =
            setOf(
                "context", "widget", "mounted", "children"
            )

        private val FILE_EXTENSIONS = listOf("dart")

        init {
            LanguageSupportRegistry.register(DartSupport())
        }
    }

    override fun getName(): String = "dart"

    override fun getKeywords(): Set<String> = KEYWORDS

    override fun getBuiltInTypes(): Set<String> = BUILT_IN_TYPES

    override fun getBuiltInFunctions(): Set<String> = BUILT_IN_FUNCTIONS

    override fun getBuiltInVariables(): Set<String> = BUILT_IN_VARIABLES

    override fun getFileExtensions(): List<String> = FILE_EXTENSIONS
}
