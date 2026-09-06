package com.ai.assistance.operit.core.tools.defaultTool.standard

/**
 * Lightweight shell command safety validator.
 *
 * Instead of substring matching the whole command line (which flags innocent
 * arguments such as `ffprobe -show_format`), this validator extracts the
 * command word of every shell pipeline segment and only checks dangerous
 * behaviour at command position.
 */
object ShellCommandSafety {

    /** Commands that are unconditionally dangerous on Android (e.g. partition formatters). */
    private val unconditionalDangerousCommands =
        setOf(
            "format",
        )

    /** Shells / wrappers whose `-c` argument embeds another command to validate recursively. */
    private val embedCommandShells =
        setOf(
            "sh",
            "bash",
            "ash",
            "mksh",
            "su",
            "exec",
        )

    /** Multi-call binaries whose first argument is the actual command word. */
    private val multiCallBinaries =
        setOf(
            "busybox",
            "toybox",
        )

    /** Wrapper commands that merely prefix the real command word. */
    private val wrapperCommands =
        setOf(
            "adb",
            "env",
            "shell",
            "timeout",
            "nice",
            "nohup",
        )

    private val varAssignmentRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*$")

    /**
     * Returns a danger reason when the command should be rejected, or null when it is safe.
     */
    fun validate(command: String): String? {
        if (command.isBlank()) return null
        return validateSegments(splitSegments(command))
    }

    private fun validateSegments(segments: List<String>): String? {
        for (segment in segments) {
            val tokens = tokenize(segment)
            if (tokens.isEmpty()) continue
            var index = 0

            // Skip leading variable assignments such as `LC_ALL=C cmd ...`.
            while (index < tokens.size && varAssignmentRegex.matches(tokens[index])) {
                index++
            }
            if (index >= tokens.size) continue

            var commandWord = tokens[index]
            index++
            while (
                index < tokens.size &&
                    (commandWord.lowercase() in multiCallBinaries ||
                        commandWord.lowercase() in wrapperCommands)
            ) {
                commandWord = tokens[index]
                index++
            }

            val lowerCommand = commandWord.lowercase()
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
     * (`|`, `||`, `&&`, `;`, `&`, newline) while ignoring separators inside quotes.
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
                char == ';' || char == '\n' -> {
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
