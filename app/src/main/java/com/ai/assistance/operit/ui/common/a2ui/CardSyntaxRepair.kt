package com.ai.assistance.operit.ui.common.a2ui

/** Repairs punctuation only. The strict compiler remains the acceptance and resource boundary. */
internal object CardSyntaxRepair {
    private const val MAX_CANDIDATES = 64

    fun attempt(
        source: String,
        error: CardExpress.SyntaxFailure,
        validate: (String) -> CardExpress.Prepared,
    ): CardExpress.Prepared? {
        if (source.length > CardExpress.MAX_SOURCE) return null
        val candidates = linkedMapOf<String, String>()
        var line = 1
        var column = 1
        var quoted = false
        var escaped = false
        val stack = ArrayDeque<Char>()
        val closings = mutableListOf<Int>()
        val insertionPoints = mutableListOf<Int>()
        var errorOffset = -1
        var extraClosings = 0
        var mismatched = false
        source.forEachIndexed { index, c ->
            if (!quoted && insertionPoints.size <= MAX_CANDIDATES) insertionPoints.add(index)
            if (line == error.line && column == error.column && !quoted) errorOffset = index
            if (quoted) {
                if (c == '"' && !escaped) quoted = false
                escaped = !escaped && c == '\\'
            } else if (c == '"') {
                quoted = true
                escaped = false
            } else when (c) {
                '(', '[', '{' -> stack.addLast(c)
                ')', ']', '}' -> {
                    if (closings.size <= MAX_CANDIDATES) closings.add(index)
                    val opening = when (c) { ')' -> '('; ']' -> '['; else -> '{' }
                    if (stack.isEmpty()) extraClosings++
                    else if (stack.last() == opening) stack.removeLast()
                    else mismatched = true
                }
            }
            if (c == '\n') { line++; column = 1 } else column++
        }
        // Never complete a truncated string or reinterpret a wrongly typed bracket.
        if (quoted) return null
        if (insertionPoints.size <= MAX_CANDIDATES) insertionPoints.add(source.length)
        if (errorOffset >= 0) {
            val replacement = when {
                error.reason == "Expected ':'." && source[errorOffset] == '=' -> ':'
                error.reason == "Expected '='." && source[errorOffset] == ':' -> '='
                else -> null
            }
            if (replacement != null)
                candidates[source.replaceRange(errorOffset, errorOffset + 1, replacement.toString())] = "separator"
        }
        if (!mismatched && extraClosings == 0 && stack.size == 1) {
            if (insertionPoints.size > MAX_CANDIDATES) return null
            val closing = when (stack.last()) { '(' -> ")"; '[' -> "]"; else -> "}" }
            // Appending at EOF is not necessarily unique: closing an inner function sooner
            // can move optional arguments to its parent. Check all unquoted positions.
            insertionPoints.forEach { index ->
                candidates[source.substring(0, index) + closing + source.substring(index)] = "missing_closing_bracket"
            }
        }
        if (!mismatched && extraClosings == 1 && stack.isEmpty()) {
            // Check every candidate, not just a prefix: a later one may change the meaning.
            if (closings.size > MAX_CANDIDATES) return null
            closings.forEach { index ->
                candidates[source.removeRange(index, index + 1)] = "extra_closing_bracket"
            }
        }
        if (candidates.size > MAX_CANDIDATES) return null
        var accepted: CardExpress.Prepared? = null
        for ((candidate, kind) in candidates) {
            val result = try { validate(candidate) }
                catch (_: CardExpress.Failure) { continue }
                catch (_: IllegalArgumentException) { continue }
            if (accepted != null && accepted.document != result.document) return null
            if (accepted == null) accepted = result.copy(repair = kind)
        }
        return accepted
    }
}
