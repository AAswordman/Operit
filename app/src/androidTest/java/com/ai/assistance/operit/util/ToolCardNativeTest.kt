package com.ai.assistance.operit.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.streamnative.NativeMarkdownSplitter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolCardNativeTest {
    private fun xmlContent(chunks: List<String>): String {
        val source = chunks.joinToString("")
        val session = NativeMarkdownSplitter.createBlockSession()
        val result = StringBuilder()
        try {
            for (chunk in chunks) {
                val segments = session.push(chunk)
                for (index in segments.indices step 3) {
                    if (segments[index] == MarkdownProcessorType.XML_BLOCK.ordinal) {
                        result.append(source.substring(segments[index + 1], segments[index + 2]))
                    }
                }
            }
        } finally {
            session.destroy()
        }
        return result.toString()
    }

    @Test fun adjacentToolResultKeepsWholePayloadAtEveryChunkBoundary() {
        val tool = "<tool_result_aBc4 name=\"read_file\"><content>前</content>后</content></tool_result_aBc4>"
        val source = "说明" + tool + "回答"
        for (split in 0..source.length) {
            assertEquals("切分位置=$split", tool, xmlContent(listOf(source.take(split), source.drop(split))))
        }
        assertEquals(tool, xmlContent(source.map { it.toString() }))
    }

    @Test fun fencedToolExampleStaysCodeAndOrdinaryInlineXmlStaysText() {
        assertEquals("", xmlContent(listOf("```xml\n<tool_exec name=\"run\">示例</tool_exec>\n```")))
        assertEquals("", xmlContent(listOf("说明<details>正文</details>")))
    }
}
