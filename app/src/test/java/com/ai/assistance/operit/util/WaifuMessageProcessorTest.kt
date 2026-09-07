package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test

class WaifuMessageProcessorTest {
    @Test
    fun calculateTypingDelayMs_firstSegmentIsImmediate() {
        assertEquals(
            0L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 80,
                charDelayMs = 240,
                isFirstSegment = true,
            )
        )
    }

    @Test
    fun calculateTypingDelayMs_usesCurrentSegmentLengthForShortTail() {
        assertEquals(
            720L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 3,
                charDelayMs = 240,
                isFirstSegment = false,
            )
        )
    }

    @Test
    fun calculateTypingDelayMs_capsLongSegmentDelay() {
        assertEquals(
            3000L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 80,
                charDelayMs = 240,
                isFirstSegment = false,
            )
        )
    }

    @Test
    fun calculateTypingDelayMs_nonPositiveDelayIsImmediate() {
        assertEquals(
            0L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 10,
                charDelayMs = 0,
                isFirstSegment = false,
            )
        )
    }

    @Test
    fun ensureBlockLatexDelimiters_wrapsDollarWhenMissing() {
        val body = "\\colorbox{pink}{\\text{早该说这句}}"
        assertEquals(
            "$$" + body + "$$",
            WaifuMessageProcessor.ensureBlockLatexDelimiters(body)
        )
    }

    @Test
    fun ensureBlockLatexDelimiters_preservesExistingDollarDelimiters() {
        val already = "\$\$x^2 + y^2 = z^2\$\$"
        assertEquals(already, WaifuMessageProcessor.ensureBlockLatexDelimiters(already))
    }

    @Test
    fun ensureBlockLatexDelimiters_preservesBracketDelimiters() {
        val already = "\\[E = mc^2\\]"
        assertEquals(already, WaifuMessageProcessor.ensureBlockLatexDelimiters(already))
    }

    @Test
    fun ensureBlockLatexDelimiters_keepsOuterWhitespace() {
        val body = "\\frac{1}{2}"
        assertEquals(
            "\n$$" + body + "$$\n",
            WaifuMessageProcessor.ensureBlockLatexDelimiters("\n" + body + "\n")
        )
    }

    @Test
    fun ensureBlockLatexDelimiters_returnsBlankInputUnchanged() {
        assertEquals("", WaifuMessageProcessor.ensureBlockLatexDelimiters(""))
        assertEquals("   \n", WaifuMessageProcessor.ensureBlockLatexDelimiters("   \n"))
    }

    @Test
    fun ensureBlockLatexDelimiters_doesNotDoubleWrapEmptyBody() {
        // A stray `$$` on its own should not become `$$$$$$`.
        assertEquals("$$", WaifuMessageProcessor.ensureBlockLatexDelimiters("$$"))
    }

    // ---- Integration tests below run the native block splitter and need libstreamnative.so.
    // ---- They skip cleanly when the native library is unavailable on the host JVM,
    // ---- e.g. plain JVM unit tests without the Android jniLibs on java.library.path.

    private fun requireNativeStreamSplitter() {
        try {
            System.loadLibrary("streamnative")
        } catch (e: UnsatisfiedLinkError) {
            assumeNoException(e)
        }
    }

    @Test
    fun splitMessageBySentences_preservesDollarBlockLatex() {
        requireNativeStreamSplitter()
        val content = "早该说这句：$$\\colorbox{pink}{\\text{早该说这句}}$$ 就这样。"
        val segments = WaifuMessageProcessor.splitMessageBySentences(content)
        val latexSegment = segments.singleOrNull { it.contains("colorbox") }
            ?: error("Expected exactly one segment containing the LaTeX body, got $segments")
        assertTrue(
            "LaTeX segment must keep the `$$` delimiters so the chat bubble renders it as LaTeX: $latexSegment",
            latexSegment.startsWith("$$") && latexSegment.endsWith("$$")
        )
        assertTrue(
            "LaTeX body must not be split by sentence rules: $latexSegment",
            latexSegment.contains("\\colorbox{pink}{\\text{早该说这句}}")
        )
    }

    @Test
    fun splitMessageBySentences_preservesBracketBlockLatex() {
        requireNativeStreamSplitter()
        val content = "看这里：\\[\\colorbox{pink}{\\text{早该说这句}}\\] 就这样。"
        val segments = WaifuMessageProcessor.splitMessageBySentences(content)
        val latexSegment = segments.singleOrNull { it.contains("colorbox") }
            ?: error("Expected exactly one segment containing the LaTeX body, got $segments")
        assertTrue(
            "Bracket-delimited LaTeX must survive intact: $latexSegment",
            latexSegment.startsWith("\\[") && latexSegment.endsWith("\\]")
        )
    }

    @Test
    fun splitMessageBySentences_doesNotSplitBlockLatexInterior() {
        requireNativeStreamSplitter()
        // A period inside the LaTeX body previously chopped the block in two.
        val content = "结果：\$\$x = 1.5 \\cdot y\$\$"
        val segments = WaifuMessageProcessor.splitMessageBySentences(content)
        val latexSegment = segments.singleOrNull { it.contains("cdot") }
            ?: error("Expected exactly one segment containing the LaTeX body, got $segments")
        assertEquals("\$\$x = 1.5 \\cdot y\$\$", latexSegment)
    }

    @Test
    fun splitMessageBySentences_keepsOrderAroundBlockLatex() {
        requireNativeStreamSplitter()
        val content = "开头。\$\$a+b\$\$ 结尾。"
        val segments = WaifuMessageProcessor.splitMessageBySentences(content)
        assertEquals(listOf("开头。", "\$\$a+b\$\$", "结尾。"), segments)
    }

    @Test
    fun streamSegments_preservesDollarBlockLatexAcrossChunks() = runBlocking {
        requireNativeStreamSplitter()
        // Split the LaTeX block across many small chunks to simulate model streaming.
        val fullText = "早该说这句：$$\\colorbox{pink}{\\text{早该说这句}}$$ 就这样。"
        val chunks = fullText.chunked(3)
        val chunkStream: Stream<String> = stream {
            chunks.forEach { emit(it) }
        }
        val collected = mutableListOf<String>()
        WaifuMessageProcessor.streamSegments(chunkStream).collect { collected.add(it) }

        val latexSegment = collected.singleOrNull { it.contains("colorbox") }
            ?: error("Expected exactly one segment containing the LaTeX body, got $collected")
        assertTrue(
            "Streamed LaTeX must be wrapped in `$$` for the bubble renderer: $latexSegment",
            latexSegment.startsWith("$$") && latexSegment.endsWith("$$")
        )
        assertTrue(
            "Streamed LaTeX body must remain in one segment: $latexSegment",
            latexSegment.contains("\\colorbox{pink}{\\text{早该说这句}}")
        )
    }

    @Test
    fun streamSegments_preservesBracketBlockLatexAcrossChunks() = runBlocking {
        requireNativeStreamSplitter()
        val fullText = "开头：\\[a^2 + b^2 = c^2\\] 结尾。"
        val chunks = fullText.chunked(4)
        val chunkStream: Stream<String> = stream {
            chunks.forEach { emit(it) }
        }
        val collected = mutableListOf<String>()
        WaifuMessageProcessor.streamSegments(chunkStream).collect { collected.add(it) }

        val latexSegment = collected.singleOrNull { it.contains("a^2") }
            ?: error("Expected exactly one segment containing the LaTeX body, got $collected")
        assertTrue(
            "Bracket-delimited LaTeX must survive streaming intact: $latexSegment",
            latexSegment.startsWith("\\[") && latexSegment.endsWith("\\]")
        )
    }
}
