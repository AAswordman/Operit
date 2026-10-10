package com.ai.assistance.operit.ui.features.chat.components.part

import com.ai.assistance.operit.ui.common.markdown.lazy.rememberMarkdownCardValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable

object DetailsTagRenderer {

    @Composable
    fun Render(
        xmlContent: String,
        modifier: Modifier,
        textColor: Color,
        enableDialogs: Boolean = true
    ) {
        val tagName = extractTagName(xmlContent) ?: "details"
        val inner = extractContentFromXml(xmlContent, tagName)
        val summary = extractSummary(inner)
        val body = removeSummary(inner).trim()

        val defaultExpanded = hasOpenAttribute(xmlContent, tagName)
        var expanded by rememberMarkdownCardValue("details-expanded", defaultExpanded)
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 90f else 0f,
            animationSpec = tween(durationMillis = 300),
            label = "detailsArrowRotation"
        )

        Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            CanvasExpandableHeaderRow(
                title = if (summary.isNotBlank()) summary else "Details",
                semanticDescription = if (expanded) "Collapse" else "Expand",
                expanded = expanded,
                titleColor = textColor.copy(alpha = 0.85f),
                rotationDegrees = rotation,
                onClick = { expanded = !expanded },
            )

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                if (body.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 8.dp, start = 24.dp)
                    ) {
                        MarkdownTextComposable(
                            text = body,
                            textColor = textColor.copy(alpha = 0.85f),
                            modifier = Modifier,
                            enableDialogs = enableDialogs
                        )
                    }
                }
            }
        }
    }

    private fun extractContentFromXml(content: String, tagName: String): String {
        val startTag = "<$tagName"
        val endTag = "</$tagName>"

        val startTagEnd = content.indexOf('>', content.indexOf(startTag)) + 1
        val endIndex = content.lastIndexOf(endTag)

        return if (startTagEnd > 0 && endIndex > startTagEnd) {
            content.substring(startTagEnd, endIndex)
        } else {
            content
        }
    }

    private fun extractTagName(content: String): String? {
        val regex = "<\\s*([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
        return regex.find(content)?.groupValues?.getOrNull(1)
    }

    private fun extractSummary(detailsInner: String): String {
        val regex = "<summary>(.*?)</summary>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return regex.find(detailsInner)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    private fun removeSummary(detailsInner: String): String {
        val regex = "<summary>.*?</summary>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return detailsInner.replaceFirst(regex, "")
    }

    private fun hasOpenAttribute(detailsXml: String, tagName: String): Boolean {
        val openRegex = ("<" + Regex.escape(tagName) + "\\b[^>]*\\bopen\\b").toRegex(RegexOption.IGNORE_CASE)
        return openRegex.containsMatchIn(detailsXml)
    }
}
