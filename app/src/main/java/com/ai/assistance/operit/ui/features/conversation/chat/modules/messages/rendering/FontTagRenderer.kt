package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object FontTagRenderer {

    @Composable
    fun Render(xmlContent: String, modifier: Modifier, textColor: Color) {
        val innerText = extractContentFromXml(xmlContent, "font")
        val colorAttr = extractXmlAttribute(xmlContent, "color")
        val sizeAttr = extractXmlAttribute(xmlContent, "size")
        val faceAttr = extractXmlAttribute(xmlContent, "face")
        val styleAttr = extractXmlAttribute(xmlContent, "style")
        val bgColorAttr = extractXmlAttribute(xmlContent, "bgcolor")

        val fontColor = parseFontColor(colorAttr, textColor)
        val fontSize = parseFontSize(sizeAttr)
        val fontFamily = parseFontFamily(faceAttr)
        val (weight, fontStyle, decoration) = parseFontStyle(styleAttr)
        val bgColor = parseFontColor(bgColorAttr, Color.Transparent)

        val base = MaterialTheme.typography.bodyMedium
        val resolvedStyle = base.copy(
            fontSize = fontSize ?: base.fontSize,
            fontFamily = fontFamily ?: base.fontFamily,
            fontWeight = weight ?: base.fontWeight,
            fontStyle = fontStyle ?: base.fontStyle,
            textDecoration = decoration ?: base.textDecoration
        )

        CanvasFontTextBlock(
            text = innerText,
            style = resolvedStyle,
            textColor = fontColor,
            backgroundColor = bgColor,
            modifier = modifier,
        )
    }

    private fun extractContentFromXml(content: String, tagName: String): String {
        val startTag = "<$tagName"
        val endTag = "</$tagName>"

        val startTagEnd = content.indexOf('>', content.indexOf(startTag)) + 1
        val endIndex = content.lastIndexOf(endTag)

        return if (startTagEnd > 0 && endIndex > startTagEnd) {
            content.substring(startTagEnd, endIndex).trim()
        } else {
            content
        }
    }

    private fun extractXmlAttribute(content: String, attributeName: String): String? {
        val regex = ("\\b" + Regex.escape(attributeName) + "\\s*=\\s*([\"'])(.*?)\\1").toRegex()
        return regex.find(content)?.groupValues?.getOrNull(2)
    }

    private fun parseFontColor(value: String?, fallback: Color): Color {
        if (value.isNullOrBlank()) return fallback
        return try {
            val parsed = android.graphics.Color.parseColor(value.trim())
            Color(parsed)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun parseFontSize(value: String?): TextUnit? {
        if (value.isNullOrBlank()) return null
        val raw = value.trim().lowercase()

        val htmlSize = raw.toIntOrNull()
        if (htmlSize != null && htmlSize in 1..7) {
            return when (htmlSize) {
                1 -> 10.sp
                2 -> 12.sp
                3 -> 14.sp
                4 -> 16.sp
                5 -> 18.sp
                6 -> 20.sp
                else -> 24.sp
            }
        }

        val number = raw
            .removeSuffix("sp")
            .removeSuffix("px")
            .removeSuffix("dp")
            .toFloatOrNull() ?: return null

        return number.sp
    }

    private fun parseFontFamily(value: String?): FontFamily? {
        if (value.isNullOrBlank()) return null
        val face = value.trim().lowercase()
        return when (face) {
            "monospace", "mono" -> FontFamily.Monospace
            "serif" -> FontFamily.Serif
            "sans-serif", "sansserif", "sans" -> FontFamily.SansSerif
            "cursive" -> FontFamily.Cursive
            else -> null
        }
    }

    private fun parseFontStyle(value: String?): Triple<FontWeight?, FontStyle?, TextDecoration?> {
        if (value.isNullOrBlank()) return Triple(null, null, null)
        val style = value.lowercase()
        val weight = if (style.contains("bold")) FontWeight.Bold else null
        val fontStyle = if (style.contains("italic")) FontStyle.Italic else null

        val decorations = mutableListOf<TextDecoration>()
        if (style.contains("underline")) decorations.add(TextDecoration.Underline)
        if (style.contains("line-through") || style.contains("strikethrough")) decorations.add(TextDecoration.LineThrough)
        val decoration = when (decorations.size) {
            0 -> null
            1 -> decorations.first()
            else -> TextDecoration.combine(decorations)
        }

        return Triple(weight, fontStyle, decoration)
    }
}
