package com.ai.assistance.operit.api.chat.llmprovider

/**
 * Closed-form media probes for model-config / function-config tests.
 *
 * The expected codes live in the test assets, not in the prompt. Chat analysis
 * prompts are left unchanged; only these probes ask for the marker.
 */
object MediaCapabilityProbe {
    const val IMAGE_ASSET_PATH = "test/1.jpg"
    const val AUDIO_ASSET_PATH = "test/1.mp3"
    const val VIDEO_ASSET_PATH = "test/1.mp4"

    const val IMAGE_CODE = "7K2Q"
    const val AUDIO_CODE = "375"
    const val VIDEO_CODE = "M4XP"

    const val IMAGE_PROMPT =
        "A short code is shown in this image. Reply with only that code. Do not describe the image."
    const val AUDIO_PROMPT =
        "This audio says three digits in English, one after another. Reply with only those three digits and nothing else."
    const val VIDEO_PROMPT =
        "A short code is shown on screen throughout this video. Reply with only that code. Do not describe the video."

    private val DIGIT_WORDS =
        listOf(
            "zero" to "0",
            "one" to "1",
            "two" to "2",
            "three" to "3",
            "four" to "4",
            "five" to "5",
            "six" to "6",
            "seven" to "7",
            "eight" to "8",
            "nine" to "9"
        )

    fun matchesImage(response: String): Boolean = alnumUpper(response) == IMAGE_CODE

    fun matchesVideo(response: String): Boolean = alnumUpper(response) == VIDEO_CODE

    fun matchesAudio(response: String): Boolean {
        if (alnumUpper(response) == AUDIO_CODE) {
            return true
        }
        return extractStrictDigitWords(response) == AUDIO_CODE
    }

    internal fun alnumUpper(text: String): String {
        val builder = StringBuilder(text.length)
        for (ch in text) {
            if (ch.isLetterOrDigit()) {
                builder.append(ch.uppercaseChar())
            }
        }
        return builder.toString()
    }

    internal fun extractStrictDigitWords(text: String): String? {
        val lower = text.lowercase()
        val builder = StringBuilder()
        var index = 0
        while (index < lower.length) {
            val ch = lower[index]
            if (ch.isDigit()) {
                builder.append(ch)
                index++
                continue
            }
            if (!ch.isLetter()) {
                index++
                continue
            }
            var matched = false
            for ((word, digit) in DIGIT_WORDS) {
                if (lower.startsWith(word, index)) {
                    val end = index + word.length
                    val boundaryOk = end == lower.length || !lower[end].isLetter()
                    if (boundaryOk) {
                        builder.append(digit)
                        index = end
                        matched = true
                        break
                    }
                }
            }
            if (!matched) {
                return null
            }
        }
        return builder.toString()
    }
}
