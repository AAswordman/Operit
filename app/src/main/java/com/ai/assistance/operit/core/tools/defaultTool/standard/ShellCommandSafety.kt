package com.ai.assistance.operit.core.tools.defaultTool.standard

/**
 * Lightweight shell command safety validator.
 *
 * Instead of naive substring matching the whole command line (which flags innocent
 * arguments such as `ffprobe -show_format`), this validator inspects commands at
 * execution positions across pipelines, compound statements, subshells, and command
 * substitutions.
 */
object ShellCommandSafety {
    /** Commands that are unconditionally dangerous on Android (e.g. partition formatters). */
    private val unconditionalDangerousCommands =
        setOf(
            "format",
        )

    /** Shells whose `-c` argument embeds another command to validate recursively. */
    private val embedCommandShells =
        setOf(
            "sh",
            "bash",
            "ash",
            "mksh",
            "zsh",
            "su",
        )

    /** Multi-call binaries whose following argument is the actual command word. */
    private val multiCallBinaries =
        setOf(
            "busybox",
            "toybox",
        )

    /** Wrapper commands that prefix the real command word. */
    private val wrapperCommands =
        setOf(
            "adb",
            "env",
            "shell",
            "timeout",
            "nice",
            "nohup",
            "exec",
            "stdbuf",
            "time",
            "strace",
        )

    /** Reserved keywords in shell compound commands. */
    private val controlKeywords =
        setOf(
            "if",
            "then",
            "else",
            "elif",
            "fi",
            "while",
            "until",
            "do",
            "done",
            "for",
            "in",
            "{",
            "}",
            "!",
        )

    private val varAssignmentRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*$")

    /**
     * Returns a danger reason when the command should be rejected, or null when it is safe.
     */
    fun validate(command: String): String? {
        if (command.isBlank()) return null
        checkCommandSubstitutions(command)?.let { return it }
        return validateSegments(splitSegments(command))
    }

    /**
     * Recursively checks command substitutions like `$(...)` or `...` inside strings or double quotes.
     */
    private fun checkCommandSubstitutions(command: String): String? {
        var index = 0
        while (index < command.length) {
            val char = command[index]
            if (char == '`') {
                val nextBacktick = command.indexOf('`', index + 1)
                if (nextBacktick > index) {
                    val inner = command.substring(index + 1, nextBacktick)
                    validate(inner)?.let { return "Embedded command substitution rejected: $it" }
                    index = nextBacktick + 1
                    continue
                }
            } else if (char == '$' && index + 1 < command.length && command[index + 1] == '(') {
                var depth = 1
                var scan = index + 2
                while (scan < command.length && depth > 0) {
                    if (command[scan] == '(') depth++
                    else if (command[scan] == ')') depth--
                    scan++
                }
                if (depth == 0) {
                    val inner = command.substring(index + 2, scan - 1)
                    validate(inner)?.let { return "Embedded command substitution rejected: $it" }
                    index = scan
                    continue
                }
            }
            index++
        }
        return null
    }

    private fun validateSegments(segments: List<String>): String? {
        for (segment in segments) {
            val tokens = tokenize(segment)
            if (tokens.isEmpty()) continue

            var index = 0
            while (index < tokens.size) {
                val token = tokens[index]
                // 1. Skip variable assignments, e.g. `LC_ALL=C FOO=bar`
                if (varAssignmentRegex.matches(token)) {
                    index++
                    continue
                }
                // 2. Skip compound shell keywords, e.g. `if`, `then`, `else`, `do`
                if (token.lowercase() in controlKeywords) {
                    index++
                    continue
                }
                val base = token.substringAfterLast('/').lowercase()
                // 3. Skip multi-call binary names (busybox, toybox)
                if (base in multiCallBinaries) {
                    index++
                    continue
                }
                // 4. Skip wrappers and consume their specific flags/arguments
                if (base in wrapperCommands) {
                    index++
                    when (base) {
                        "timeout" -> {
                            while (index < tokens.size) {
                                val t = tokens[index]
                                if (t.startsWith("-")) {
                                    index++
                                    if (index < tokens.size && !tokens[index].startsWith("-") &&
                                        (t == "-s" || t == "-k" || t == "--signal" || t == "--kill-after")
                                    ) {
                                        index++
                                    }
                                } else if (t.matches(Regex("^[0-9]+(\\.[0-9]+)?([smhd])?$"))) {
                                    index++
                                    break
                                } else {
                                    break
                                }
                            }
                        }
                        "nice" -> {
                            while (index < tokens.size && tokens[index].startsWith("-")) {
                                val t = tokens[index]
                                index++
                                if (t == "-n" && index < tokens.size) {
                                    index++
                                }
                            }
                        }
                        "env" -> {
                            while (index < tokens.size) {
                                val t = tokens[index]
                                if (t.startsWith("-")) {
                                    index++
                                    if (t == "-u" && index < tokens.size) index++
                                } else if (varAssignmentRegex.matches(t)) {
                                    index++
                                } else {
                                    break
                                }
                            }
                        }
                        "stdbuf" -> {
                            while (index < tokens.size && tokens[index].startsWith("-")) {
                                val t = tokens[index]
                                index++
                                if ((t == "-i" || t == "-o" || t == "-e") && index < tokens.size) {
                                    index++
                                }
                            }
                        }
                        "exec" -> {
                            while (index < tokens.size && tokens[index].startsWith("-")) {
                                val t = tokens[index]
                                index++
                                if (t == "-a" && index < tokens.size) index++
                            }
                        }
                        else -> {
                            while (index < tokens.size && tokens[index].startsWith("-")) {
                                index++
                            }
                        }
                    }
                    continue
                }
                // Found the actual target command word
                break
            }

            if (index >= tokens.size) continue
            val commandWord = tokens[index]
            index++

            val lowerCommand = commandWord.substringAfterLast('/').lowercase()
            if (lowerCommand in unconditionalDangerousCommands) {
                return "Dangerous command '$commandWord' is not allowed"
            }

            val args = tokens.subList(index, tokens.size)
            if (lowerCommand == "rm" && hasRecursiveForce(args)) {
                return "rm with recursive force flags is not allowed"
            }

            if (lowerCommand in embedCommandShells) {
                val commandIndex = args.indexOfFirst { it == "-c" }
                if (commandIndex >= 0 && commandIndex + 1 < args.size) {
                    val embedded = args[commandIndex + 1]
                    validate(embedded)?.let { reason ->
                        return "Embedded command rejected: $reason"
                    }
                }
            }
        }
        return null
    }

    /**
     * Splits a command line into shell pipeline/sequence segments on control operators
     * (`|`, `||`, `&&`, `;`, `&`, newline, `(`, `)`) while ignoring separators inside quotes.
     */
    private fun splitSegments(command: String): List<String> {
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var index = 0
        while (index < command.length) {
            val char = command[index]
            when {
                quote != null -> {
                    current.append(char)
                    if (char == quote) quote = null
                    if (char == '\\' && quote == '"' && index + 1 < command.length) {
                        current.append(command[index + 1])
                        index++
                    }
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    current.append(char)
                }
                char == '\\' && index + 1 < command.length -> {
                    current.append(char)
                    current.append(command[index + 1])
                    index++
                }
                char == ';' || char == '\n' || char == '(' || char == ')' -> {
                    segments += current.toString()
                    current.clear()
                }
                char == '|' || char == '&' -> {
                    // Handle `||` and `&&` as single separators.
                    if (index + 1 < command.length && command[index + 1] == char) {
                        index++
                    }
                    segments += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        segments += current.toString()
        return segments
    }

    /**
     * Splits one segment into quote-aware tokens. Backslash escapes work outside quotes and
     * inside double quotes (approximating POSIX shell behaviour).
     */
    private fun tokenize(segment: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var index = 0
        while (index < segment.length) {
            val char = segment[index]
            when {
                quote != null -> {
                    if (char == quote) {
                        quote = null
                    } else if (char == '\\' && quote == '"' && index + 1 < segment.length) {
                        current.append(segment[index + 1])
                        index++
                    } else {
                        current.append(char)
                    }
                }
                char == '\'' || char == '"' -> quote = char
                char == '\\' && index + 1 < segment.length -> {
                    current.append(segment[index + 1])
                    index++
                }
                char == ' ' || char == '\t' -> {
                    if (current.isNotEmpty()) {
                        tokens += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
            index++
        }
        if (current.isNotEmpty()) tokens += current.toString()
        return tokens
    }

    private fun hasRecursiveForce(args: List<String>): Boolean {
        var hasRecursive = false
        var hasForce = false
        for (arg in args) {
            if (arg == "--") {
                // POSIX end-of-options marker: all following tokens are operands/paths
                break
            }
            when {
                arg == "--recursive" || arg == "-R" -> hasRecursive = true
                arg == "--force" -> hasForce = true
                arg.startsWith("-") && !arg.startsWith("--") -> {
                    arg.drop(1).forEach { flag ->
                        val lowerFlag = flag.lowercaseChar()
                        if (lowerFlag == 'r') hasRecursive = true
                        if (lowerFlag == 'f') hasForce = true
                    }
                }
            }
        }
        return hasRecursive && hasForce
    }
}