package com.ai.assistance.operit.ui.common.markdown

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownPlainTextRendererAndroidTest {

    private fun render(
        markdown: String,
        latexToPlainText: (List<String>) -> List<String> = { it },
    ): String = runBlocking { markdownToPlainTextForCopy(markdown, latexToPlainText) }

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
            render(content)
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
            render(content)
        )
    }

    @Test fun markdownToPlainTextForCopy_convertsTableToTabbedText() {
        val content =
            """
            | 名称 | 数量 |
            | --- | ---: |
            | 苹果 | **2** |
            """.trimIndent()

        assertEquals("名称\t数量\n苹果\t2", render(content))
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
            render(content)
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
            render(content)
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
            render(content) { formulas ->
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
