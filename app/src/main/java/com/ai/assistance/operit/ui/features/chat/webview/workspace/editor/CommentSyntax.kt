package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

/**
 * 文件格式的注释规则。
 *
 * 行注释和块注释分别保存，调用方根据当前行或选区选择合适的方式。
 */
data class CommentSyntax(
    val linePrefixes: List<String> = emptyList(),
    val blockStart: String? = null,
    val blockEnd: String? = null
) {
    val preferredLinePrefix: String?
        get() = linePrefixes.firstOrNull()

    val hasBlockComment: Boolean
        get() = !blockStart.isNullOrEmpty() && !blockEnd.isNullOrEmpty()
}

/**
 * 按语言名称提供注释规则。
 *
 * 这里只注册能够通过明确扩展名识别的格式，未知格式不会自动猜测。
 */
object CommentSyntaxRegistry {
    private fun line(prefix: String, vararg alternatives: String): CommentSyntax =
        CommentSyntax(linePrefixes = listOf(prefix) + alternatives.toList())

    private fun block(start: String, end: String): CommentSyntax =
        CommentSyntax(blockStart = start, blockEnd = end)

    private fun lineAndBlock(
        prefix: String,
        start: String,
        end: String,
        vararg alternatives: String
    ): CommentSyntax = CommentSyntax(
        linePrefixes = listOf(prefix) + alternatives.toList(),
        blockStart = start,
        blockEnd = end
    )

    private val syntaxes: Map<String, CommentSyntax> = mapOf(
        // 双斜线和 C 风格块注释。
        "kotlin" to lineAndBlock("//", "/*", "*/"),
        "java" to lineAndBlock("//", "/*", "*/"),
        "javascript" to lineAndBlock("//", "/*", "*/"),
        "typescript" to lineAndBlock("//", "/*", "*/"),
        "cpp" to lineAndBlock("//", "/*", "*/"),
        "csharp" to lineAndBlock("//", "/*", "*/"),
        "php" to lineAndBlock("//", "/*", "*/", "#"),
        "go" to lineAndBlock("//", "/*", "*/"),
        "rust" to lineAndBlock("//", "/*", "*/"),
        "swift" to lineAndBlock("//", "/*", "*/"),
        "dart" to lineAndBlock("//", "/*", "*/"),
        "actionscript" to lineAndBlock("//", "/*", "*/"),
        "groovy" to lineAndBlock("//", "/*", "*/"),
        "scala" to lineAndBlock("//", "/*", "*/"),
        "solidity" to lineAndBlock("//", "/*", "*/"),
        "gdscript" to lineAndBlock("//", "/*", "*/"),
        "verilog" to lineAndBlock("//", "/*", "*/"),
        "glsl" to lineAndBlock("//", "/*", "*/"),
        "hlsl" to lineAndBlock("//", "/*", "*/"),
        "pascal" to lineAndBlock("//", "(*", "*)"),
        "fsharp" to lineAndBlock("//", "(*", "*)"),

        "css" to block("/*", "*/"),

        // HTML/XML 风格块注释。
        "html" to block("<!--", "-->"),
        "xml" to block("<!--", "-->"),
        "markdown" to block("<!--", "-->"),

        // 井号注释。
        "python" to line("#"),
        "ruby" to line("#"),
        "perl" to line("#"),
        "r" to line("#"),
        "julia" to line("#"),
        "powershell" to line("#"),
        "nim" to line("#"),
        "crystal" to line("#"),
        "elixir" to line("#"),
        "shell" to line("#"),
        "yaml" to line("#"),
        "toml" to line("#"),
        "cmake" to line("#"),
        "hcl" to lineAndBlock("#", "/*", "*/", "//"),
        "graphql" to line("#"),
        "coffeescript" to line("#"),
        "tcl" to line("#"),
        "awk" to line("#"),
        "gnuplot" to line("#"),
        "raku" to line("#"),
        "properties" to line("#", "!"),

        // 双横线注释。
        "sql" to lineAndBlock("--", "/*", "*/"),
        "lua" to lineAndBlock("--", "--[[", "]]"),
        "haskell" to lineAndBlock("--", "{-", "-}"),
        "ada" to line("--"),
        "elm" to lineAndBlock("--", "{-", "-}"),
        "purescript" to lineAndBlock("--", "{-", "-}"),
        "vhdl" to line("--"),
        "applescript" to lineAndBlock("--", "(*", "*)"),

        // 分号注释。
        "assembly" to line(";"),
        "lisp" to line(";"),
        "scheme" to line(";"),
        "clojure" to line(";"),
        "racket" to line(";"),
        "autoit" to line(";"),
        "ini" to line(";", "#"),

        // 百分号注释。
        "latex" to line("%"),
        "erlang" to line("%"),
        "prolog" to line("%"),

        // 感叹号、单引号和双引号注释。
        "fortran" to line("!"),
        "vb" to line("'"),
        "vbscript" to line("'"),
        "vim" to line("\""),

        // 其他具有明确语法的格式。
        "batch" to line("REM ", "rem ", "Rem "),
        "cobol" to line("*>"),
        "ocaml" to block("(*", "*)"),
        "json5" to lineAndBlock("//", "/*", "*/")
    )

    fun forLanguage(language: String): CommentSyntax? =
        syntaxes[language.lowercase()]
}
