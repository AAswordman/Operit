package com.ai.assistance.operit.util.streamnative

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamGroup
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeMarkdownEofInstrumentedTest {
    private suspend fun renderedText(
        groups: Stream<StreamGroup<MarkdownProcessorType?>>,
    ): String {
        val text = StringBuilder()
        groups.collect { group -> group.stream.collect { text.append(it) } }
        return text.toString()
    }

    @Test
    fun charBlockFlushesDigitsAtEof() = runBlocking {
        val source = stream<Char> { "42".forEach { emit(it) } }
        assertEquals("42", renderedText(source.nativeMarkdownSplitByBlock()))
    }

    @Test
    fun emptyCharSuffixDoesNotReplaySeed() = runBlocking {
        val source = stream<Char> {}
        val groups = source.nativeMarkdownSplitByBlock(null, null, initialContent = "42")
        assertEquals("", renderedText(groups))
    }

    @Test
    fun charSuffixEmitsOnlyNewDigits() = runBlocking {
        val source = stream<Char> { emit('2') }
        val groups = source.nativeMarkdownSplitByBlock(null, null, initialContent = "4")
        assertEquals("2", renderedText(groups))
    }

    @Test
    fun emptyStringSuffixDoesNotReplayInlineSeed() = runBlocking {
        val source = stream<String> {}
        val groups = source.nativeMarkdownSplitByInline(null, null, initialContent = "[")
        assertEquals("", renderedText(groups))
    }

    @Test
    fun stringSuffixKeepsOnlyNewInlineText() = runBlocking {
        val source = stream<String> { emit("a") }
        val groups = source.nativeMarkdownSplitByInline(null, null, initialContent = "[")
        assertEquals("a", renderedText(groups))
    }

    @Test
    fun cancelledInputDoesNotFlushTentativeDigits() = runBlocking {
        val source = stream<Char> {
            emit('4')
            throw CancellationException("cancelled sample")
        }
        val text = StringBuilder()
        try {
            source.nativeMarkdownSplitByBlock().collect { group ->
                group.stream.collect { text.append(it) }
            }
        } catch (_: CancellationException) {
        }
        assertEquals("", text.toString())
    }
}
