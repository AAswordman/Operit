package com.ai.assistance.operit.util

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPlugin
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPluginRegistry
import com.ai.assistance.operit.ui.common.markdown.XmlRenderResult
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.Stream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 清理和边界回归直接运行于 JVM，避免缺少原生库时跳过核心断言。 */
class WaifuMessageProcessorReviewTest {
    private val registeredPluginIds = mutableListOf<String>()

    @After
    fun unregisterTestPlugins() {
        registeredPluginIds.forEach(XmlRenderPluginRegistry::unregister)
    }

    @Test
    fun sharedCleaner_keepsSpeechAndNotificationsAsPlainText() {
        val content = "# 清单\n1. **重点** [文档](https://example.com)\n> 引用\n- ~~删除~~ `代码`"

        assertEquals("清单 重点 文档 引用 删除 代码", WaifuMessageProcessor.cleanContentForWaifu(content))
        assertEquals(content, WaifuMessageProcessor.cleanContentForWaifuDisplay(content))
    }

    @Test
    fun sharedCleaner_removesRegisteredPluginXmlFromSpeech() {
        registerPluginXmlTag("plan")
        registerPluginXmlTag("emotion")
        val content = "开头。<plan id=\"1\">私有插件内容</plan><emotion>happy</emotion>结尾。"

        assertEquals("开头。结尾。", WaifuMessageProcessor.cleanContentForWaifu(content))
        assertEquals(content, WaifuMessageProcessor.cleanContentForWaifuDisplay(content))
    }

    @Test
    fun displayCleaner_removesInternalXmlEvenWhenRegistered() {
        registerPluginXmlTag("status")
        registerPluginXmlTag("think")
        val content = "正文。<status type=\"completion\"/><think>内部思考</think><note>未注册内容</note>结尾。"

        assertEquals("正文。结尾。", WaifuMessageProcessor.cleanContentForWaifuDisplay(content))
    }

    @Test
    fun displayCleaner_preservesNestedPluginXmlUntilOuterClosingTag() {
        registerPluginXmlTag("plan")
        val xml = "<plan id=\"1\">外层<plan>内层</plan>外层尾部</plan>"

        assertEquals("开头。${xml}结尾。", WaifuMessageProcessor.buildRenderableContentForWaifu("开头。${xml}结尾。"))
        assertEquals(xml, WaifuMessageProcessor.cleanContentForWaifuDisplay(xml))
        assertTrue(WaifuMessageProcessor.shouldPreserveXmlBlockForWaifu(xml))
        assertFalse(WaifuMessageProcessor.shouldPreserveXmlBlockForWaifu("<plan>外层<plan>内层</plan>"))
    }

    @Test
    fun pluginXml_doesNotCloseOnTagTextInsideAttributesCommentsOrCdata() {
        registerPluginXmlTag("plan")
        val xml = "<plan label=\"</plan>\"><!-- </plan> --><![CDATA[</plan>]]>正文</plan>"

        assertEquals(xml, WaifuMessageProcessor.cleanContentForWaifuDisplay(xml))
        assertTrue(WaifuMessageProcessor.shouldPreserveXmlBlockForWaifu(xml))
        assertFalse(WaifuMessageProcessor.shouldPreserveXmlBlockForWaifu("<plan label=\"</plan>\">正文"))
        assertEquals("开头。", WaifuMessageProcessor.buildRenderableContentForWaifu("开头。<plan>未闭合内容"))
    }

    @Test
    fun orderedLists_preserveExactNumbersAtLineStartAndAfterSentenceEnding() {
        assertEquals(
            listOf("1. 第一项。", "12. 第二项。"),
            WaifuMessageProcessor.splitPlainTextIntoSentences("1. 第一项。\n12. 第二项。", false)
        )
        assertEquals(
            listOf("结束。", "1. 事项。"),
            WaifuMessageProcessor.splitPlainTextIntoSentences("结束。1. 事项。", false)
        )
        assertEquals(
            listOf("结束。", "12. 事项。"),
            WaifuMessageProcessor.splitPlainTextIntoSentences("结束。 12. 事项。", false)
        )
    }

    @Test
    fun orderedLists_doNotTreatTrailingPartialMarkersAsSentenceEndings() {
        assertFalse(WaifuMessageProcessor.hasStableSentenceEnding("结束。1."))
        assertFalse(WaifuMessageProcessor.hasStableSentenceEnding("结束。 12. "))
        assertTrue(WaifuMessageProcessor.hasStableSentenceEnding("结束。1. 完整事项。"))
        assertTrue(WaifuMessageProcessor.hasStableSentenceEnding("普通英文结束."))
    }

    @Test
    fun orderedLists_doNotChangeDecimalsOrRemoveTheMarkerAsPunctuation() {
        assertEquals(
            listOf("1. 数值 1.5。"),
            WaifuMessageProcessor.splitPlainTextIntoSentences("1. 数值 1.5。", false)
        )
        assertEquals(
            listOf("1. 数值 1.5"),
            WaifuMessageProcessor.splitPlainTextIntoSentences("1. 数值 1.5。", true)
        )
    }

    @Test
    fun orderedLists_preserveUserWrittenPlaceholderLiterals() {
        val literal = "{WAIFULISTDOT:99} {WAIFULISTDOT:0} {{WAIFULISTDOT:0} {WAIFULISTDOT:999999999999999999999}"
        assertEquals(
            listOf("$literal。", "1. 事项。"),
            WaifuMessageProcessor.splitPlainTextIntoSentences("$literal。1. 事项。", false)
        )
    }

    @Test
    fun markdownBlocks_restoreEveryLineWithoutDuplicatingExistingMarkers() {
        assertEquals(
            "> 首行\n> 第二行\n> 第三行\n\n",
            WaifuMessageProcessor.restoreMarkdownBlockMarkers(
                "首行\n> 第二行\n第三行\n\n", MarkdownProcessorType.BLOCK_QUOTE
            )
        )
        assertEquals(
            "- 首行\n- 第二行\n- 第三行",
            WaifuMessageProcessor.restoreMarkdownBlockMarkers(
                "首行\n- 第二行\n第三行", MarkdownProcessorType.UNORDERED_LIST
            )
        )
    }

    @Test
    fun markdownBlocks_restoreMarkersAcrossChunkBoundaries() {
        val restorer = WaifuMessageProcessor.MarkdownBlockMarkerRestorer("> ")
        val chunks = listOf("首行\n>", " ", "第二行\n", "第", "三行\n")
        val content = chunks.joinToString("") { restorer.append(it) } + restorer.finish()

        assertEquals("> 首行\n> 第二行\n> 第三行\n", content)
    }

    @Test
    fun markdownBlocks_preserveCrlfAndAnEmptyFinalPrefix() {
        val restorer = WaifuMessageProcessor.MarkdownBlockMarkerRestorer("> ")
        val chunks = listOf("首行\r", "\n", "第二行\r\n", ">")
        val content = chunks.joinToString("") { restorer.append(it) } + restorer.finish()

        assertEquals("> 首行\r\n> 第二行\r\n>", content)
    }

    @Test
    fun inlineMath_doesNotHoldDollarAmountsAsUnclosedFormulas() {
        assertNull(WaifuMessageProcessor.findLastUnclosedInlineMarkdownStart("价格 \$5。"))
        assertNull(WaifuMessageProcessor.findLastUnclosedInlineMarkdownStart("价格 \$5 and \$10。"))
        assertNull(WaifuMessageProcessor.findLastUnclosedInlineMarkdownStart("价格 \\\$5。"))
        assertEquals(3, WaifuMessageProcessor.findLastUnclosedInlineMarkdownStart("公式：\$x + 1"))
        assertNull(WaifuMessageProcessor.findLastUnclosedInlineMarkdownStart("公式：\$x + 1\$。"))
        assertNull(WaifuMessageProcessor.findLastUnclosedInlineMarkdownStart("公式：\$5 + 2\$。"))
    }

    private fun registerPluginXmlTag(tagName: String) {
        val pluginId = "test.waifu.review.cleaner.$tagName"
        registeredPluginIds.add(pluginId)
        XmlRenderPluginRegistry.register(object : XmlRenderPlugin {
            override val id: String = pluginId
            override fun supports(candidateTagName: String): Boolean =
                candidateTagName.equals(tagName, ignoreCase = true)

            override suspend fun resolve(
                context: Context,
                xmlContent: String,
                tagName: String,
                textColor: Color,
                xmlStream: Stream<String>?,
            ): XmlRenderResult = XmlRenderResult.Text(xmlContent)
        })
    }
}
