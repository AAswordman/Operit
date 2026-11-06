package com.ai.assistance.operit.ui.common.markdown

/**
 * A bare `http://` or `https://` URL found in plain inline text.
 *
 * [start] and [end] delimit exactly the characters the link should cover. They never include
 * sentence punctuation or a closing parenthesis that belongs to the prose around the URL, so the
 * visible text and the clickable range stay the same when a URL ends a sentence or sits inside a
 * parenthetical.
 */
internal data class BareUrl(
    val start: Int,
    val end: Int,
    val url: String,
)

private const val HTTP_PREFIX = "http://"
private const val HTTPS_PREFIX = "https://"

// Characters that may follow a URL in prose without belonging to it. A `.` is by far the most
// common one: "see https://example.com." and "https://example.com, then" must not linkify the
// punctuation. Only characters that end a URL as often as they end a sentence belong here, so
// `/`, `=`, `&` and `%` never do, and a query or fragment keeps its own characters.
private const val TRAILING_SENTENCE_PUNCTUATION = ".,;:!?"

/**
 * Returns every bare `http`/`https` URL in [text], in source order.
 *
 * The scan is local to one inline text run. Callers decide which runs are eligible, which is how
 * the renderer keeps inline code and existing `[text](url)` link labels out of the result.
 */
internal fun findBareUrls(text: CharSequence): List<BareUrl> {
    if (text.isEmpty()) return emptyList()

    val urls = mutableListOf<BareUrl>()
    var index = 0
    while (index < text.length) {
        val schemeLength = schemeLengthAt(text, index)
        if (schemeLength == 0) {
            index++
            continue
        }

        val urlStart = index
        val bodyStart = index + schemeLength
        // A scheme with nothing behind it is prose about a scheme, not a URL: "the https:// prefix".
        if (bodyStart >= text.length || !isUrlHostStart(text, bodyStart)) {
            index = bodyStart
            continue
        }

        var bodyEnd = bodyStart
        while (bodyEnd < text.length) {
            val codePoint = Character.codePointAt(text, bodyEnd)
            if (!isUrlBodyCodePoint(codePoint)) break
            bodyEnd += Character.charCount(codePoint)
        }
        val urlEnd = trimUrlTail(text, bodyStart, bodyEnd)
        if (urlEnd <= bodyStart) {
            index = bodyStart
            continue
        }

        urls +=
            BareUrl(
                start = urlStart,
                end = urlEnd,
                // The scheme is normalized because the click path hands the string straight to
                // Uri/ACTION_VIEW, where an upper-case scheme does not match the intent filters
                // that browsers and apps register. Everything after the scheme is left as written.
                url = text.substring(urlStart, urlStart + schemeLength).lowercase() +
                    text.substring(urlStart + schemeLength, urlEnd),
            )
        index = urlEnd
    }
    return urls
}

private fun schemeLengthAt(text: CharSequence, index: Int): Int {
    // A scheme glued to the tail of a word is an identifier fragment, not a link. Non-ASCII text
    // counts as a boundary, so a URL pasted straight after Chinese prose still links.
    if (index > 0 && isAsciiLetterOrDigit(text[index - 1])) return 0
    return when {
        text.matchesPrefixAt(index, HTTPS_PREFIX) -> HTTPS_PREFIX.length
        text.matchesPrefixAt(index, HTTP_PREFIX) -> HTTP_PREFIX.length
        else -> 0
    }
}

private fun CharSequence.matchesPrefixAt(index: Int, prefix: String): Boolean {
    if (index + prefix.length > length) return false
    return regionMatches(index, prefix, 0, prefix.length, ignoreCase = true)
}

/**
 * Trims what the surrounding prose contributed rather than what the writer put into the URL:
 * trailing sentence punctuation, and closing parentheses or brackets the URL did not open itself.
 * `https://en.wikipedia.org/wiki/Foo_(bar)` keeps its closing bracket because the URL opened one;
 * `(see https://example.com)` drops the bracket that belongs to the sentence.
 */
private fun trimUrlTail(text: CharSequence, bodyStart: Int, bodyEnd: Int): Int {
    var openParentheses = 0
    var closeParentheses = 0
    var openBrackets = 0
    var closeBrackets = 0
    for (index in bodyStart until bodyEnd) {
        when (text[index]) {
            '(' -> openParentheses++
            ')' -> closeParentheses++
            '[' -> openBrackets++
            ']' -> closeBrackets++
        }
    }

    var end = bodyEnd
    while (end > bodyStart) {
        when (text[end - 1]) {
            in TRAILING_SENTENCE_PUNCTUATION -> end--
            ')' -> {
                if (closeParentheses <= openParentheses) return end
                closeParentheses--
                end--
            }
            ']' -> {
                if (closeBrackets <= openBrackets) return end
                closeBrackets--
                end--
            }
            else -> return end
        }
    }
    return end
}

/**
 * URL text includes Unicode letters, numbers and combining marks as well as URI punctuation.
 *
 * An ASCII-only body silently links a different destination for a Chinese path or query. Scan
 * code points so supplementary letters also stay intact, while Unicode sentence punctuation
 * still ends the link. Brackets support IPv6 hosts; unmatched prose brackets are trimmed above.
 * Markdown quotes, emphasis markers and angle brackets remain outside the clickable range.
 */
private fun isUrlBodyCodePoint(codePoint: Int): Boolean {
    if (Character.isLetterOrDigit(codePoint)) return true
    return when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt() -> true
        else -> codePoint in 0..127 && codePoint.toChar() in "-._~:/?#@!$&()+,;=%[]"
    }
}

private fun isUrlHostStart(text: CharSequence, index: Int): Boolean {
    return Character.isLetterOrDigit(Character.codePointAt(text, index)) || text[index] == '['
}

private fun isAsciiLetterOrDigit(char: Char): Boolean {
    return char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9'
}
