package com.ai.assistance.operit.ui.features.chat.components.part

import java.io.File
import java.security.MessageDigest
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
    fun `full static font preserves historical icons outside the former subset`() {
        val html = render(SHOPPING_CART_SPAN)
        assertTrue(html.contains(SHOPPING_CART_SPAN))

        // 这个 Google Fonts v370 静态实例经 fontTools 检查有 4,277 个 GSUB 连字，
        // 包括不在原 140 图标子集里的 shopping_cart。锁定文件可防止以后误换回子集。
        val font = File("src/main/assets/${StatusCardHtmlDocument.ICON_FONT_ASSET}")
        assertTrue(font.isFile)
        assertEquals(FULL_STATIC_FONT_BYTES, font.length())
        assertEquals(FULL_STATIC_FONT_SHA256, sha256(font))
    }

    private fun render(body: String = ICON_SPAN): String =
        StatusCardHtmlDocument.render(
            bodyContent = body,
            textColorHex = "#123456",
            iconFontBase64 = FAKE_FONT
        )

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val FAKE_FONT = "d09GMgABAAAAAA"
        const val ICON_SPAN = "<span class=\"material-symbols-rounded\">favorite</span>"
        const val SHOPPING_CART_SPAN =
            "<span class=\"material-symbols-rounded\">shopping_cart</span>"
        const val FULL_STATIC_FONT_BYTES = 456_052L
        const val FULL_STATIC_FONT_SHA256 =
            "19a71b30e6267416493a0e4e925a6f14be60a9c3a83148a0dc0dcf507a8fb382"
    }
}
