package com.ai.assistance.operit.ui.features.conversation.workspace.editor.completion

import com.ai.assistance.operit.ui.features.conversation.workspace.editor.language.LanguageFactory

/**
 * Dart 补全提供器
 */
class DartCompletionProvider : DefaultCompletionProvider() {
    private val languageSupport = LanguageFactory.getLanguageSupport("dart")

    override fun getCompletionItems(text: CharSequence, position: Int): List<CompletionItem> {
        val prefix = getPrefix(text, position)
        if (prefix.isEmpty()) {
            return emptyList()
        }

        val completions = mutableListOf<CompletionItem>()

        languageSupport?.getKeywords()?.filter { it.startsWith(prefix) }?.forEach {
            completions.add(
                CompletionItem(
                    label = it,
                    insertText = it,
                    kind = CompletionItemKind.KEYWORD
                )
            )
        }

        languageSupport?.getBuiltInTypes()?.filter { it.startsWith(prefix) }?.forEach {
            completions.add(
                CompletionItem(
                    label = it,
                    insertText = it,
                    kind = CompletionItemKind.CLASS
                )
            )
        }

        languageSupport?.getBuiltInFunctions()?.filter { it.startsWith(prefix) }?.forEach {
            completions.add(
                CompletionItem(
                    label = it,
                    insertText = it,
                    kind = CompletionItemKind.FUNCTION
                )
            )
        }

        languageSupport?.getBuiltInVariables()?.filter { it.startsWith(prefix) }?.forEach {
            completions.add(
                CompletionItem(
                    label = it,
                    insertText = it,
                    kind = CompletionItemKind.VARIABLE
                )
            )
        }

        extractVariables(text.toString()).filter { it.startsWith(prefix) }.forEach {
            completions.add(
                CompletionItem(
                    label = it,
                    insertText = it,
                    kind = CompletionItemKind.VARIABLE
                )
            )
        }

        return completions.distinctBy { "${it.kind}:${it.label}" }
    }

    override fun getTriggerCharacters(): Set<Char> = setOf('.', '(', ':', '=')

    override fun extractVariables(text: String): List<String> {
        val variables = mutableListOf<String>()

        val variablePattern =
            "\\b(?:final|var|const|late(?:\\s+final)?)\\s+([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
        variablePattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            if (name.isNotEmpty()) {
                variables.add(name)
            }
        }

        val functionPattern =
            "\\b(?:void|Future<[^>]+>|Future|Stream<[^>]+>|Stream|int|double|num|bool|String|Widget)?\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(".toRegex()
        functionPattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            if (name.isNotEmpty() && name !in setOf("if", "for", "while", "switch")) {
                variables.add(name)
            }
        }

        val typePattern =
            "\\b(?:class|enum|mixin|extension|typedef)\\s+([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
        typePattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            if (name.isNotEmpty()) {
                variables.add(name)
            }
        }

        return variables
    }
}
