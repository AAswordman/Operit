package com.ai.assistance.operit.ui.common.displays

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.ui.common.markdown.links.openMarkdownLink

/** 新一代流式Markdown+LaTeX渲染器，完全替换原有实现。 兼容原有API，支持所有Markdown和LaTeX混排。 */
@Composable
fun MarkdownTextComposable(
        text: String,
        textColor: Color,
        modifier: Modifier = Modifier,
        fontSize: TextUnit = Unspecified,
        textAlign: TextAlign? = null,
        isSelectable: Boolean = true, // 保留参数，暂不处理
        onLinkClicked: ((String) -> Unit)? = null,
        enableDialogs: Boolean = true
) {
        // 直接使用新的、基于字符串的渲染器，以获得更好的性能
        val context = LocalContext.current
        StreamMarkdownRenderer(
                content = text,
                modifier = modifier,
                textColor = textColor,
                fontSize = fontSize,
                enableDialogs = enableDialogs,
                onLinkClick = { url -> openMarkdownLink(context, url) }
        )
}
