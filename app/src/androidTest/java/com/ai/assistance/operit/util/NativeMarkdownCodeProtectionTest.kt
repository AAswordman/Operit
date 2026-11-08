package com.ai.assistance.operit.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.ui.common.markdown.parseMarkdownToNodes
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.streamnative.NativeMarkdownSplitter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeMarkdownCodeProtectionTest {
    private fun tagsByCharacter(source: String, chunks: List<String>): List<Int> {
        val tags = MutableList(source.length) { -2 }
        val session = NativeMarkdownSplitter.createBlockSession()
        try {
            for (chunk in chunks) {
                val segments = session.push(chunk)
                for (index in segments.indices step 3) {
                    if (segments[index] >= 0) {
                        for (offset in segments[index + 1] until segments[index + 2]) {
                            tags[offset] = segments[index]
                        }
                    }
                }
            }
        } finally {
            session.destroy()
        }
        return tags
    }

    private fun assertAllChunkBoundaries(source: String, expected: List<Int>) {
        assertEquals(expected, tagsByCharacter(source, listOf(source)))
        for (split in 1 until source.length) {
            assertEquals(expected, tagsByCharacter(source, listOf(source.take(split), source.drop(split))))
        }
        assertEquals(expected, tagsByCharacter(source, source.map(Char::toString)))
    }

    @Test fun inlineCodeKeepsXmlLiteralAndFollowingRealToolIsRecognized() {
        for (delimiter in listOf("`", "``")) {
            val example = "前文 $delimiter<tool_exec name=\"example\">示例</tool_exec>$delimiter 后文"
            val realTool = "<tool_live name=\"run\">参数</tool_live>"
            assertAllChunkBoundaries(
                example + realTool,
                List(example.length) { MarkdownProcessorType.PLAIN_TEXT.ordinal } +
                    List(realTool.length) { MarkdownProcessorType.XML_BLOCK.ordinal },
            )
        }
    }

    @Test fun bothFencesPreserveCodeAndFollowingAnswerAtEveryChunkBoundary() {
        for (marker in listOf("```", "~~~", "````", "~~~~")) {
            val code = "${marker}xml\n<tool_exec name=\"example\">示例</tool_exec>\n$marker \t\n"
            val answer = "CODE_TEXT_END\n"
            assertAllChunkBoundaries(
                code + answer,
                List(code.length) { MarkdownProcessorType.CODE_BLOCK.ordinal } +
                    List(answer.length) { MarkdownProcessorType.PLAIN_TEXT.ordinal },
            )
        }
    }

    @Test fun shorterOrDifferentClosingFenceDoesNotExposeToolExample() {
        val code = "````xml\n```\n~~~\n<tool_exec name=\"example\">示例</tool_exec>\n````\n"
        assertAllChunkBoundaries(code, List(code.length) { MarkdownProcessorType.CODE_BLOCK.ordinal })
    }

    @Test fun codeExamplesBecomeInlineAndFencedNodesWithoutSplittingParagraph() = runBlocking {
        val source = "前文 `<tool_exec name=\"example\">示例</tool_exec>` 后文\n" +
            "~~~xml\n<tool_exec name=\"example\">示例</tool_exec>\n~~~\nCODE_TEXT_END\n"
        val nodes = parseMarkdownToNodes(source)

        assertFalse(nodes.any { it.type == MarkdownProcessorType.XML_BLOCK })
        assertEquals(1, nodes.count { it.type == MarkdownProcessorType.CODE_BLOCK })
        val paragraph = nodes.first { it.type == MarkdownProcessorType.PLAIN_TEXT }
        assertTrue(paragraph.content.toString().contains("前文"))
        assertTrue(paragraph.content.toString().contains("后文"))
        assertTrue(paragraph.children.any { it.type == MarkdownProcessorType.INLINE_CODE })
        assertTrue(nodes.last().content.toString().contains("CODE_TEXT_END"))
    }
}