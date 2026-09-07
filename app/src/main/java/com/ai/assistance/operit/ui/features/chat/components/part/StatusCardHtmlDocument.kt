package com.ai.assistance.operit.ui.features.chat.components.part

import android.content.Context
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * <html> 状态卡片在 WebView 里渲染时用的完整 HTML 文档。
 *
 * <metric> / <badge> 的图标是 Material Symbols 的连字字形。文档以前用 <link> 去
 * fonts.googleapis.com 取这份字体：卡片文字随文档一起出现，图标却要等整份 5.35 MB 的
 * 可变字体下载完才有，弱网下要 10~40 秒，离线时干脆把图标名按原文画出来（issue #930）。
 * 现在字体子集随 APK 打包，用 data: URI 内联进文档，图标和文字同时出现，也不再联网。
 */
internal object StatusCardHtmlDocument {

    /** 打包在 assets 里的图标字体，只覆盖 [ICON_NAMES] 里的图标。 */
    const val ICON_FONT_ASSET: String = "fonts/material_symbols_rounded_subset.woff2"

    /**
     * 字体子集覆盖的图标名，和标签市场「AI状态卡片」提示词里给模型的清单是同一份。
     *
     * 增删图标要同时改这里、改那份提示词，并用同一批名字重新生成字体
     * （列表保持排序去重，下面这个地址才是可复现的）：
     * https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,1,0&icon_names=<逗号分隔的名字>
     *
     * 四个轴都钉成单值（opsz=24 / wght=400 / FILL=1 / GRAD=0），取回来的是静态实例，
     * 和下面 .material-symbols-rounded 的 font-variation-settings 一致；同样这 140 个图标，
     * 带全部轴插值数据的可变字体要大 9 倍。
     */
    val ICON_NAMES: List<String> = listOf(
        "account_circle", "alarm", "analytics", "auto_awesome", "autorenew",
        "badge", "bar_chart", "battery_alert", "battery_charging_full", "battery_full",
        "bedtime", "block", "bolt", "book", "bookmark",
        "bug_report", "build", "calendar_month", "campaign", "cancel",
        "celebration", "chat", "check_circle", "close", "cloud",
        "cloud_done", "cloud_off", "code", "construction", "dark_mode",
        "data_usage", "description", "developer_board", "diamond", "directions_run",
        "done_all", "draw", "eco", "edit", "electric_bolt",
        "emoji_emotions", "emoji_events", "error", "event", "explore",
        "extension", "face", "favorite", "flight", "forum",
        "grade", "group", "groups", "handshake", "help",
        "history", "home", "hourglass_empty", "hourglass_top", "hub",
        "image", "info", "insights", "key", "leaderboard",
        "light_mode", "lightbulb", "local_cafe", "local_fire_department", "lock",
        "map", "memory", "menu_book", "military_tech", "monitor_heart",
        "monitoring", "mood", "mood_bad", "music_note", "neurology",
        "notifications", "palette", "park", "pause", "pending",
        "person", "person_search", "pets", "pie_chart", "place",
        "play_arrow", "priority_high", "psychology", "psychology_alt", "public",
        "query_stats", "refresh", "report", "restaurant", "rocket_launch",
        "schedule", "science", "search", "security", "self_improvement",
        "sentiment_dissatisfied", "sentiment_neutral", "sentiment_satisfied", "sentiment_very_dissatisfied", "sentiment_very_satisfied",
        "settings", "shield", "show_chart", "smart_toy", "speed",
        "star", "storage", "sync", "task_alt", "terminal",
        "thermostat", "thumb_down", "thumb_up", "timeline", "timer",
        "tips_and_updates", "today", "translate", "travel_explore", "trending_down",
        "trending_up", "tune", "update", "verified", "visibility",
        "water_drop", "waving_hand", "wifi", "wifi_off", "workspace_premium",
    )

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
