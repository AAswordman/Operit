package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

/**
 * 根据文件名检测编程语言。
 *
 * 只有明确扩展名才会被识别，无法安全判断的格式统一返回 text。
 */
object LanguageDetector {
    /**
     * 根据文件名获取编程语言。
     * @param fileName 文件名
     * @return 语言标识符，未知格式返回 text
     */
    fun detectLanguage(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return when (extension) {
            // Kotlin、Java 和 JVM 语言。
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "groovy", "gradle" -> "groovy"
            "scala" -> "scala"
            "pas", "pp" -> "pascal"
            "fs", "fsi", "fsx" -> "fsharp"

            // JavaScript 生态和 ActionScript。
            "js", "mjs", "cjs" -> "javascript"
            "ts", "tsx" -> "typescript"
            "as" -> "actionscript"
            "coffee", "litcoffee" -> "coffeescript"

            // 标记语言和样式文件。
            "html", "htm", "xhtml" -> "html"
            "css", "scss", "sass", "less" -> "css"
            "xml", "svg", "xsd", "xsl", "xslt" -> "xml"
            "md", "markdown" -> "markdown"
            "tex", "ltx", "sty" -> "latex"

            // 数据和配置格式。
            "json" -> "json"
            "json5" -> "json5"
            "yaml", "yml" -> "yaml"
            "toml" -> "toml"
            "ini", "desktop" -> "ini"
            "properties" -> "properties"
            "cmake" -> "cmake"
            "tf", "tfvars", "hcl" -> "hcl"
            "graphql", "gql" -> "graphql"

            // C 风格语言和系统语言。
            "c", "cpp", "cc", "cxx", "h", "hh", "hpp" -> "cpp"
            "cs" -> "csharp"
            "php", "phtml" -> "php"
            "go" -> "go"
            "rs" -> "rust"
            "swift" -> "swift"
            "dart" -> "dart"
            "sol" -> "solidity"
            "gd" -> "gdscript"
            "v", "sv", "svh" -> "verilog"
            "glsl", "vert", "frag", "geom", "comp" -> "glsl"
            "hlsl", "fx", "fxh" -> "hlsl"

            // 井号和脚本语言。
            "py", "pyw" -> "python"
            "rb", "rbw", "rake", "gemspec" -> "ruby"
            "r" -> "r"
            "jl" -> "julia"
            "ps1", "psm1", "psd1" -> "powershell"
            "nim", "nims" -> "nim"
            "cr" -> "crystal"
            "ex", "exs" -> "elixir"
            "sh", "bash", "zsh", "ksh", "fish" -> "shell"
            "tcl" -> "tcl"
            "awk" -> "awk"
            "raku", "rakumod" -> "raku"
            "pl", "pm" -> "perl"

            // 双横线和其他语言。
            "sql" -> "sql"
            "lua" -> "lua"
            "hs", "lhs" -> "haskell"
            "adb", "ads" -> "ada"
            "elm" -> "elm"
            "purs" -> "purescript"
            "vhd", "vhdl" -> "vhdl"
            "applescript", "scpt" -> "applescript"

            // 分号、百分号和其他注释格式。
            "asm" -> "assembly"
            "lisp", "lsp" -> "lisp"
            "scm", "ss" -> "scheme"
            "clj", "cljs", "cljc" -> "clojure"
            "rkt" -> "racket"
            "au3" -> "autoit"
            "prolog" -> "prolog"
            "erl", "hrl" -> "erlang"
            "f", "for", "f90", "f95", "f03", "f08" -> "fortran"
            "vb" -> "vb"
            "vbs" -> "vbscript"
            "vim" -> "vim"
            "bat", "cmd" -> "batch"
            "cob", "cbl", "cpy" -> "cobol"
            "ml", "mli" -> "ocaml"
            "gp", "gnuplot" -> "gnuplot"

            else -> "text"
        }
    }
} 