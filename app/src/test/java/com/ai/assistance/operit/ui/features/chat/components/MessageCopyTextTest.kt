package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCopyTextTest {

    @Test fun cleanMessageContentForCopy_removesMultipleOpenAiReasoningMetadata() {
        val content =
            """
            <meta provider="openai:responses_reasoning">first-payload</meta>
            <tool name="run"><param name="command">pwd</param></tool>
            <meta provider="openai:responses_reasoning">second-payload</meta>
            final answer
            """.trimIndent()

        assertEquals("final answer", cleanMessageContentForCopy(content))
    }

    @Test fun cleanMessageContentForCopy_removesGeminiThoughtSignature() {
        val content = "prefix<meta provider=\"gemini:thought_signature\">signature</meta>suffix"

        assertEquals("prefixsuffix", cleanMessageContentForCopy(content))
    }

    @Test fun cleanMessageContentForCopy_preservesOtherMetaAndMarkdown() {
        val content = "<meta provider=\"other\">value</meta>\n**answer**"

        assertEquals(content, cleanMessageContentForCopy(content))
    }

    @Test fun cleanMessageContentForCopy_preservesHtmlMetaBeforeInternalMetadata() {
        val content =
            "<meta charset=\"utf-8\">visible" +
                "<meta provider=\"openai:responses_reasoning\">payload</meta>answer"

        assertEquals("<meta charset=\"utf-8\">visibleanswer", cleanMessageContentForCopy(content))
    }

    @Test fun markdownToPlainTextForCopy_removesFormattingMarkers() {
        val content =
            """
            ## 标题

            这是 **粗体**、*斜体*、~~删除线~~ 和 [链接](https://example.com)。
            ---
            - 第一项
            1. 第二项
            """.trimIndent()

        assertEquals(
            """
            标题

            这是 粗体、斜体、删除线 和 链接 (https://example.com)。

            • 第一项
            1. 第二项
            """.trimIndent(),
            markdownToPlainTextForCopy(content)
        )
    }

    @Test fun markdownToPlainTextForCopy_preservesCodeBlockContent() {
        val content =
            """
            ```kotlin
            val pipe = "a | b"
            val markdown = "**text**"
            ```
            """.trimIndent()

        assertEquals(
            """
            ----kotlin-----
            val pipe = "a | b"
            val markdown = "**text**"
            """.trimIndent(),
            markdownToPlainTextForCopy(content)
        )
    }

    @Test fun markdownToPlainTextForCopy_convertsTableToTabbedText() {
        val content =
            """
            | 名称 | 数量 |
            | --- | ---: |
            | 苹果 | **2** |
            """.trimIndent()

        assertEquals(
            "名称\t数量\n苹果\t2",
            markdownToPlainTextForCopy(content)
        )
    }

    @Test fun markdownToPlainTextForCopy_preservesLatexContent() {
        val content =
            """
            结果是 **公式**：${'$'}x_1${'$'}。
            行内括号：\(x^2+y^2\)。

            ${'$'}${'$'}
            \frac{x_1+\sqrt{y}}{2}
            ${'$'}${'$'}

            \[
            \sqrt{x}
            \]
            """.trimIndent()

        assertEquals(
            """
            结果是 公式：x_1。
            行内括号：x^2+y^2。

            \frac{x_1+\sqrt{y}}{2}

            \sqrt{x}
            """.trimIndent(),
            markdownToPlainTextForCopy(content)
        )
    }

    @Test fun markdownToPlainTextForCopy_doesNotConvertLatexInsideCode() {
        val content =
            """
            ```python
            formula = "${'$'}x_1${'$'}"
            ```
            """.trimIndent()

        assertEquals(
            """
            ----python-----
            formula = "${'$'}x_1${'$'}"
            """.trimIndent(),
            markdownToPlainTextForCopy(content)
        )
    }
}
