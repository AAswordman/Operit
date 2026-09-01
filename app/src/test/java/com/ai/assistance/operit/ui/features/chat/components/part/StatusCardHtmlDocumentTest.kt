package com.ai.assistance.operit.ui.features.chat.components.part

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue #930 的回归测试：状态卡片的图标字体必须内联在文档里，
 * 文档不能再为了渲染图标去网络上取任何东西。
 */
class StatusCardHtmlDocumentTest {

    @Test
    fun `document inlines the bundled icon font`() {
        val html = render()
        assertTrue(html.contains("@font-face"))
        assertTrue(
            html.contains("src: url(data:font/woff2;base64,$FAKE_FONT) format('woff2')")
        )
    }

    @Test
    fun `document requests nothing over the network`() {
        // 以前这里 <link> 到 fonts.googleapis.com，5.35 MB 的可变字体下完之前图标都是空的。
        val html = render()
        assertFalse(html.contains("http://"))
        assertFalse(html.contains("https://"))
    }

    @Test
    fun `icon spans still resolve ligatures without the remote stylesheet`() {
        // liga 以前是 Google 那份样式表顺带给的，去掉 <link> 之后必须自己声明，
        // 否则 <span>favorite</span> 会被按字母画出来而不是画成图标。
        val html = render()
        assertTrue(html.contains(".material-symbols-rounded"))
        assertTrue(html.contains("font-family: 'Material Symbols Rounded'"))
        assertTrue(html.contains("-webkit-font-feature-settings: 'liga'"))
    }

    @Test
    fun `document still carries the card body and text color`() {
        val html = render("<div class=\"metric\">mood</div>")
        assertTrue(html.contains("<div class=\"metric\">mood</div>"))
        assertTrue(html.contains("color: #123456;"))
    }

    @Test
    fun `bundled font covers the icons the built-in status cards use`() {
        // 标签市场「AI状态卡片」和「剧情生成」的示例卡片里出现的图标名。
        val builtIn =
            listOf(
                "favorite", "emoji_emotions", "bolt", "star",
                "person_search", "psychology", "pending", "timer"
            )
        assertTrue(StatusCardHtmlDocument.ICON_NAMES.containsAll(builtIn))
    }

    @Test
    fun `icon names stay sorted and unique`() {
        // 字体子集是拿这份名单按顺序向 Google Fonts 请求生成的，
        // 乱序或重复会让下次重新生成的字体和名单对不上。
        val names = StatusCardHtmlDocument.ICON_NAMES
        assertEquals(names.distinct().sorted(), names)
    }

    private fun render(body: String = ICON_SPAN): String =
        StatusCardHtmlDocument.render(
            bodyContent = body,
            textColorHex = "#123456",
            iconFontBase64 = FAKE_FONT
        )

    private companion object {
        const val FAKE_FONT = "d09GMgABAAAAAA"
        const val ICON_SPAN = "<span class=\"material-symbols-rounded\">favorite</span>"
    }
}
