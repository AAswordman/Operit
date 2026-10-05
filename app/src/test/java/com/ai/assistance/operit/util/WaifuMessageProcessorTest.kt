package com.ai.assistance.operit.util

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPlugin
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPluginRegistry
import com.ai.assistance.operit.ui.common.markdown.XmlRenderResult
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test

class WaifuMessageProcessorTest {
    private val registeredPluginIds = mutableListOf<String>()

    @After
    fun unregisterTestPlugins() {
        registeredPluginIds.forEach(XmlRenderPluginRegistry::unregister)
    }

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
    fun streamSegments_doesNotEmitPartialOrderedListMarkerAcrossChunks() = runBlocking {
        requireNativeStreamSplitter()
        val chunks = listOf(
            "提示。\n1.",
            " ",
            "先检查完整内容",
            "，再核对结果。",
            "\n后续内容继续输出。"
        )
        val chunkStream: Stream<String> = stream {
            chunks.forEach { emit(it) }
        }
        val collected = mutableListOf<String>()
        WaifuMessageProcessor.streamSegments(chunkStream).collect { collected.add(it) }

        assertTrue("流式分句不能单独提交列表序号：$collected", collected.none { it.trim() == "1." })
        assertTrue("完整列表项应保留原始序号：$collected", collected.any { it.contains("1. 先检查完整内容") })
        assertTrue("列表项之后的本轮回复不能丢失：$collected", collected.any { it.contains("后续内容继续输出。") })
    }

    @Test
    fun streamSegments_preservesInlineOrderedListAcrossChunks() = runBlocking {
        requireNativeStreamSplitter()
        val chunks = listOf("结束。1.", " ", "事项。", "后续回复。")
        val collected = mutableListOf<String>()
        WaifuMessageProcessor.streamSegments(stream { chunks.forEach { emit(it) } })
            .collect { collected.add(it) }

        assertTrue("不能单独输出行中的序号：$collected", collected.none { it.trim() == "1." })
        assertTrue("行中的完整列表项保持原样：$collected", collected.any { it.contains("1. 事项。") })
        assertTrue("列表项之后的回复继续输出：$collected", collected.any { it.contains("后续回复。") })
    }

    @Test
    fun splitMessageBySentences_preservesUserWrittenEntityPlaceholder() {
        requireNativeStreamSplitter()
        val literal = "{WAIFUENTITY:99} {WAIFUENTITY:999999999999999999999}"
        val rendered = WaifuMessageProcessor.splitMessageBySentences("$literal。https://example.com。")
            .joinToString("")

        assertTrue("用户的临时标记字面量保持原样：$rendered", rendered.contains(literal))
        assertTrue("真实受保护链接保持原样：$rendered", rendered.contains("https://example.com"))
    }

    @Test
    fun splitMessageBySentences_preservesSupportedMarkdownAndInlineLatex() {
        requireNativeStreamSplitter()
        val content =
            "# 清单\n" +
                "1. **重点** [文档](https://example.com)\n" +
                "- ![示意图](https://example.com/image.png)\n" +
                "> 引用内容\n" +
                "公式：\$x = 1.5\$。"

        val renderedSegments = WaifuMessageProcessor.splitMessageBySentences(content)
        val renderedContent = renderedSegments.joinToString("\n")

        assertTrue("有序列表和行内格式应保留：$renderedSegments", renderedContent.contains("1. **重点** [文档](https://example.com)"))
        assertTrue("无序列表和图片链接应保留：$renderedSegments", renderedContent.contains("- ![示意图](https://example.com/image.png)"))
        assertTrue("引用标记应保留：$renderedSegments", renderedContent.contains("> 引用内容"))
        assertTrue("行内 LaTeX 应保持完整：$renderedSegments", renderedContent.contains("\$x = 1.5\$"))
    }

    @Test
    fun splitMessageBySentences_preservesRegisteredPluginXmlAndRemovesOtherXml() {
        requireNativeStreamSplitter()
        registerPluginXmlTag("plan")
        registerPluginXmlTag("emotion")
        val content =
            "开头。<plan id=\"1\">先检查 1. 内容</plan>结尾。" +
                "<status type=\"completion\"/>" +
                "<emotion>happy</emotion>" +
                "<note>不应保留</note>"

        val segments = WaifuMessageProcessor.splitMessageBySentences(content)
        val renderedContent = segments.joinToString("")

        assertTrue(
            "已注册的插件 XML 应保持完整：$segments",
            renderedContent.contains("<plan id=\"1\">先检查 1. 内容</plan>")
        )
        assertTrue(
            "Waifu 表情标签应保留：$segments",
            renderedContent.contains("<emotion>happy</emotion>")
        )
        assertTrue("内部状态标签仍应过滤：$segments", renderedContent.contains("<status").not())
        assertTrue("未注册 XML 仍应过滤：$segments", renderedContent.contains("<note").not())
        assertTrue("XML 之后的文本不能丢失：$segments", renderedContent.contains("结尾。"))
    }

    @Test
    fun streamSegments_waitsUntilRegisteredPluginXmlIsClosed() = runBlocking {
        requireNativeStreamSplitter()
        registerPluginXmlTag("plan")
        val chunks = listOf(
            "开头。<plan>",
            "先检查",
            "</plan>",
            "结尾。"
        )
        val chunkStream: Stream<String> = stream {
            chunks.forEach { emit(it) }
        }
        val collected = mutableListOf<String>()
        WaifuMessageProcessor.streamSegments(chunkStream).collect { collected.add(it) }

        assertTrue(
            "未闭合的插件 XML 不能提前输出：$collected",
            collected.none { it.contains("<plan>") && !it.contains("</plan>") }
        )
        assertTrue(
            "闭合后应输出完整插件 XML：$collected",
            collected.any { it.contains("<plan>先检查</plan>") }
        )
        assertTrue("插件 XML 后的文本不能丢失：$collected", collected.any { it.contains("结尾。") })
    }

    private fun registerPluginXmlTag(tagName: String) {
        val pluginId = "test.waifu.processor.$tagName"
        registeredPluginIds.add(pluginId)
        XmlRenderPluginRegistry.register(
            object : XmlRenderPlugin {
                override val id: String = pluginId

                override fun supports(candidateTagName: String): Boolean {
                    return candidateTagName.equals(tagName, ignoreCase = true)
                }

                override suspend fun resolve(
                    context: Context,
                    xmlContent: String,
                    tagName: String,
                    textColor: Color,
                    xmlStream: Stream<String>?
                ): XmlRenderResult {
                    return XmlRenderResult.Text(xmlContent)
                }
            }
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
