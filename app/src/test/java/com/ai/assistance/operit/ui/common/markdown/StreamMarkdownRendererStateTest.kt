package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.util.stream.MutableSharedStream
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamMarkdownRendererStateTest {
    @Test
    fun `releasing completed xml stream clears replay content`() = runTest {
        val stream = MutableSharedStream<String>(replay = Int.MAX_VALUE)
        stream.emit("completed tool payload")
        val xmlNodeStreams = mutableMapOf<Int, Stream<String>>(7 to stream)

        releaseXmlNodeStream(xmlNodeStreams, 7)

        assertTrue(xmlNodeStreams.isEmpty())
        assertTrue(stream.replayCache.isEmpty())
    }
}
