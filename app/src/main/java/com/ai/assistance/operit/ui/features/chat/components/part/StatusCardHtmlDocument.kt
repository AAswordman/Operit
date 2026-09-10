package com.ai.assistance.operit.ui.features.chat.components.part

import android.content.Context
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * <html> 状态卡片在 WebView 里渲染时用的完整 HTML 文档。
 *
 * <metric> / <badge> 的图标是 Material Symbols 的连字字形。文档以前用 <link> 去
 * fonts.googleapis.com 取这份字体：卡片文字随文档一起出现，图标却要等整份 5.36 MB 的
 * 可变字体下载完才有，弱网下要 10~40 秒，离线时干脆把图标名按原文画出来（issue #930）。
 * 现在完整的静态字体实例随 APK 打包，用 data: URI 内联进文档，图标和文字同时出现，
 * 也不再联网。
 */
internal object StatusCardHtmlDocument {

    /**
     * Google Fonts Material Symbols Rounded v370 的完整静态实例。
     * 四个轴固定为 opsz=24 / wght=400 / FILL=1 / GRAD=0，和下面的渲染样式一致。
     * 该 WOFF2 为 456,052 字节；同版本、同样完整图标范围的四轴可变字体为 5,360,840 字节。
     *
     * 字体来源：
     * https://fonts.gstatic.com/s/materialsymbolsrounded/v370/syl0-zNym6YjUruM-QrEh7-nyTnjDwKNJ_190FjpZIvDmUSVOK7BDJ_vb9vUSzq3wzLK-P0J-V_Zs-QtQth3-jOcbTCVpeRL2w5rwZu2rIelXxc.woff2
     * 对应 CSS：
     * https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,1,0&display=block
     * SHA-256：19a71b30e6267416493a0e4e925a6f14be60a9c3a83148a0dc0dcf507a8fb382
     *
     * 保留完整字体很重要：状态卡片 renderer 一直接受任意合法 Material Symbols 名称，
     * 历史消息和已安装的旧标签也可能包含当前内置示例没有列出的图标。
     */
    const val ICON_FONT_ASSET: String = "fonts/material_symbols_rounded_static.woff2"

    @Volatile
    private var cachedIconFontBase64: String? = null

    /** 每张卡片都是一个独立 WebView，字体只读一次并缓存，否则按卡片数重复读盘和编码。 */
    private fun iconFontBase64(context: Context): String {
        cachedIconFontBase64?.let { return it }
        val encoded =
            context.applicationContext.assets.open(ICON_FONT_ASSET).use {
                Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
            }
        cachedIconFontBase64 = encoded
        return encoded
    }

    fun build(context: Context, bodyContent: String, textColor: Color): String =
        render(
            bodyContent = bodyContent,
            textColorHex = String.format("#%06X", 0xFFFFFF and textColor.toArgb()),
            iconFontBase64 = iconFontBase64(context)
        )

    internal fun render(bodyContent: String, textColorHex: String, iconFontBase64: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    @font-face {
                        font-family: 'Material Symbols Rounded';
                        font-style: normal;
                        font-weight: 400;
                        src: url(data:font/woff2;base64,$iconFontBase64) format('woff2');
                    }
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        font-size: 13px;
                        line-height: 1.4;
                        color: $textColorHex;
                        padding: 0;
                        background: transparent;
                    }
                    .material-symbols-rounded {
                        font-family: 'Material Symbols Rounded';
                        font-weight: normal;
                        font-style: normal;
                        font-size: 20px;
                        display: inline-block;
                        line-height: 1;
                        text-transform: none;
                        letter-spacing: normal;
                        word-wrap: normal;
                        white-space: nowrap;
                        direction: ltr;
                        font-variation-settings: 'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24;
                        -webkit-font-feature-settings: 'liga';
                        -webkit-font-smoothing: antialiased;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        margin: 2px 0 3px 0;
                        font-weight: 600;
                        line-height: 1.3;
                        color: inherit;
                    }
                    h1 { font-size: 15px; }
                    h2 { font-size: 14px; }
                    h3 { font-size: 13px; }
                    h4 { font-size: 13px; }
                    h5 { font-size: 12px; }
                    h6 { font-size: 12px; }
                    p {
                        margin: 2px 0;
                        font-size: 13px;
                    }
                    a {
                        color: #007AFF;
                        text-decoration: none;
                    }
                    a:hover {
                        text-decoration: underline;
                    }
                    strong, b {
                        font-weight: 600;
                    }
                </style>
            </head>
            <body>
                $bodyContent
            </body>
            </html>
        """.trimIndent()
    }
}
