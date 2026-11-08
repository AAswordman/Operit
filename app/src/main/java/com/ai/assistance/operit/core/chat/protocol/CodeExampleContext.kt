package com.ai.assistance.operit.core.chat.protocol

internal data class CodeLiteralContext(val marker: String, val fenced: Boolean)

/** Tracks only parser state, never a copy of the accumulated message. */
internal class CodeExampleContext {
    private var code: CodeLiteralContext? = null
    private var openingMarker = '\u0000'
    private var openingRun = 0
    private var openingCanFence = false
    private var closingTicks = 0
    private var fenceBody = false
    private var fenceIndent = 0
    private var fenceRun = 0
    private var fenceTrailing = false
    private var fenceCandidate = true
    private var lineIndent = 0
    private var escapes = 0
    private var escapedTicks = false
    private var xml = XmlContext()

    fun copy(): CodeExampleContext = CodeExampleContext().also {
        it.code = code
        it.openingMarker = openingMarker
        it.openingRun = openingRun
        it.openingCanFence = openingCanFence
        it.closingTicks = closingTicks
        it.fenceBody = fenceBody
        it.fenceIndent = fenceIndent
        it.fenceRun = fenceRun
        it.fenceTrailing = fenceTrailing
        it.fenceCandidate = fenceCandidate
        it.lineIndent = lineIndent
        it.escapes = escapes
        it.escapedTicks = escapedTicks
        it.xml = xml.copy()
    }

    fun current(): CodeLiteralContext? {
        settleOpening()
        val active = code
        if (active != null && ((!active.fenced && closingTicks == active.marker.length) ||
                (active.fenced && fenceBody && fenceCandidate && fenceRun >= active.marker.length))) {
            code = null
            closingTicks = 0
        }
        return if (xml.active) null else code
    }

    fun append(text: CharSequence) { for (char in text) append(char) }

    fun append(char: Char) {
        if (escapedTicks && char == '`') { advancePosition(char); return }
        escapedTicks = false
        if (xml.active && xml.append(char)) {
            advancePosition(char)
            return
        }
        if (openingRun > 0 && char != openingMarker) settleOpening()
        val active = code
        if (active != null && !active.fenced && char != '`') {
            if (closingTicks == active.marker.length) code = null
            closingTicks = 0
        }
        if (code == null && char == '`' && escapes % 2 == 1) {
            escapedTicks = true
        } else if (code == null && char == '<' && escapes % 2 == 0) {
            xml.begin()
        } else {
            appendMarkdown(char)
        }
        advancePosition(char)
    }

    private fun settleOpening() {
        if (openingRun == 0) return
        if (openingMarker == '`' || openingRun >= 3) {
            code = CodeLiteralContext(openingMarker.toString().repeat(openingRun), openingCanFence && openingRun >= 3)
            closingTicks = 0
            fenceBody = false
            resetFenceLine()
        }
        openingRun = 0
    }

    private fun appendMarkdown(char: Char) {
        val active = code
        if (active == null) {
            if (openingRun > 0 && char == openingMarker) {
                openingRun++
            } else if (char == '`' && escapes % 2 == 0 || char == '~' && lineIndent in 0..3) {
                openingMarker = char
                openingRun = 1
                openingCanFence = lineIndent in 0..3
            }
            return
        }
        if (!active.fenced) {
            if (char == '`') closingTicks++
            return
        }
        if (!fenceBody) {
            if (char == '\n') { fenceBody = true; resetFenceLine() }
            return
        }
        if (char == '\n') {
            if (fenceCandidate && fenceRun >= active.marker.length) code = null
            resetFenceLine()
        } else if (fenceCandidate) {
            when {
                fenceRun == 0 && (char == ' ' || char == '\t') -> {
                    fenceIndent++
                    if (fenceIndent > 3) fenceCandidate = false
                }
                char == active.marker[0] && !fenceTrailing -> fenceRun++
                fenceRun >= active.marker.length && (char == ' ' || char == '\t' || char == '\r') -> fenceTrailing = true
                else -> fenceCandidate = false
            }
        }
    }

    private fun resetFenceLine() {
        fenceIndent = 0
        fenceRun = 0
        fenceTrailing = false
        fenceCandidate = true
    }

    private fun advancePosition(char: Char) {
        lineIndent = when {
            char == '\n' -> 0
            char == ' ' && lineIndent >= 0 -> (lineIndent + 1).coerceAtMost(4)
            else -> -1
        }
        escapes = if (char == '\\') escapes + 1 else 0
    }

    /** XML contents cannot extend the surrounding Markdown code range. */
    private class XmlContext {
        private var root: String? = null
        private var depth = 0
        private var token = ""
        private var name = StringBuilder()
        private var readingName = false
        private var closing = false
        private var quote = '\u0000'
        private var lastNonSpace = '\u0000'
        private var special: String? = null
        private var endMatch = 0
        val active: Boolean get() = depth > 0 || token.isNotEmpty() || special != null

        fun copy(): XmlContext = XmlContext().also {
            it.root = root; it.depth = depth; it.token = token
            it.name = StringBuilder(name); it.readingName = readingName
            it.closing = closing; it.quote = quote; it.lastNonSpace = lastNonSpace
            it.special = special; it.endMatch = endMatch
        }

        fun begin() {
            token = "<"; name = StringBuilder(); readingName = false
            closing = false; quote = '\u0000'; lastNonSpace = '<'
        }

        fun append(char: Char): Boolean {
            val ending = special
            if (ending != null) {
                endMatch = when {
                    char == ending[endMatch] -> endMatch + 1
                    char == ending[0] -> if (endMatch > 0 && ending[0] == ending[1]) endMatch else 1
                    else -> 0
                }
                if (endMatch == ending.length) { special = null; endMatch = 0 }
                return true
            }
            if (token.isEmpty()) {
                if (char == '<') begin()
                return true
            }
            if (token == "<" && char == '/') { token = "</"; closing = true; return true }
            if (token == "<" && char == '!') { token = "<!"; return true }
            if (token.startsWith("<!")) {
                token += char
                when {
                    token == "<!--" -> { special = "-->"; token = "" }
                    token == "<![CDATA[" -> { special = "]]>"; token = "" }
                    !"<!--".startsWith(token) && !"<![CDATA[".startsWith(token) -> token = ""
                }
                return special != null || token.isNotEmpty() || depth > 0
            }
            if (name.isEmpty()) {
                if (char in 'A'..'Z' || char in 'a'..'z') {
                    token = "tag"; name.append(char); readingName = true
                } else {
                    token = ""
                    if (char == '<') begin() else return depth > 0
                }
                return true
            }
            if (readingName) {
                if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "_.:-") {
                    name.append(char)
                    return true
                }
                readingName = false
                if (!char.isWhitespace() && char != '/' && char != '>') { token = ""; return depth > 0 }
            }
            if (quote != '\u0000') {
                if (char == quote) quote = '\u0000'
            } else if (char == '\'' || char == '"') {
                quote = char
            } else if (char == '>') {
                val tag = name.toString()
                if (root == null && !closing && lastNonSpace != '/') { root = tag; depth = 1 }
                else if (root.equals(tag, ignoreCase = true)) {
                    if (closing) depth-- else if (lastNonSpace != '/') depth++
                    if (depth == 0) root = null
                }
                token = ""
            }
            if (!char.isWhitespace()) lastNonSpace = char
            return true
        }
    }
}
