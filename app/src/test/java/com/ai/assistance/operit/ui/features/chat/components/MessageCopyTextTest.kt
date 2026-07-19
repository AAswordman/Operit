package com.ai.assistance.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun markdownToPlainTextForCopy_batchesAllLatexFromClassicFormulaSample() {
        val content =
            """
            以下是一些经典且应用广泛的数学公式，涵盖代数、几何、三角学、微积分等领域，采用标准 LaTeX 格式呈现，方便阅读与复述。

            ---

            ### 1. 二次方程求根公式
            对于一元二次方程 \(ax^2 + bx + c = 0\)（\(a \neq 0\)）：
            \[
            x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
            \]

            ---

            ### 2. 勾股定理
            在直角三角形中，直角边 \(a, b\) 与斜边 \(c\) 满足：
            \[
            a^2 + b^2 = c^2
            \]

            ---

            ### 3. 欧拉公式
            复分析中的核心恒等式：
            \[
            e^{i\theta} = \cos\theta + i\sin\theta
            \]
            特别地，当 \(\theta = \pi\) 时，得到欧拉恒等式：
            \[
            e^{i\pi} + 1 = 0
            \]

            ---

            ### 4. 微积分基本定理（牛顿-莱布尼茨公式）
            若 \(F'(x) = f(x)\) 且 \(f\) 在 \([a,b]\) 上连续，则：
            \[
            \int_a^b f(x)\,dx = F(b) - F(a)
            \]

            ---

            ### 5. 泰勒级数展开
            函数 \(f(x)\) 在点 \(a\) 附近可展开为：
            \[
            f(x) = \sum_{n=0}^{\infty} \frac{f^{(n)}(a)}{n!} (x - a)^n
            \]

            ---

            ### 6. 正态分布概率密度函数
            \[
            f(x) = \frac{1}{\sigma\sqrt{2\pi}} \exp\left( -\frac{(x-\mu)^2}{2\sigma^2} \right)
            \]

            ---

            ### 7. 自然数求和公式（等差数列）
            \[
            1 + 2 + 3 + \cdots + n = \frac{n(n+1)}{2}
            \]
            """.trimIndent()

        var batchCalls = 0
        var capturedFormulas = emptyList<String>()
        val result =
            markdownToPlainTextForCopy(content) { formulas ->
                batchCalls++
                capturedFormulas = formulas
                formulas.indices.map { index -> "FORMULA_$index" }
            }

        assertEquals(1, batchCalls)
        assertEquals(18, capturedFormulas.size)
        assertTrue(capturedFormulas.contains("""f(x) = \sum_{n=0}^{\infty} \frac{f^{(n)}(a)}{n!} (x - a)^n"""))
        assertTrue(capturedFormulas.contains("""f(x) = \frac{1}{\sigma\sqrt{2\pi}} \exp\left( -\frac{(x-\mu)^2}{2\sigma^2} \right)"""))
        assertTrue(capturedFormulas.contains("""1 + 2 + 3 + \cdots + n = \frac{n(n+1)}{2}"""))
        assertTrue(result.contains("FORMULA_17"))
        assertFalse(result.contains("\\frac"))
    }
}
