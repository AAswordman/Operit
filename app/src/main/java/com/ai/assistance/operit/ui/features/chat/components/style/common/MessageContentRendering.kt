package com.ai.assistance.operit.ui.features.chat.components.style.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.features.chat.components.rememberRevisableTextStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamRollbackPrefix

@Composable
internal fun MessageTextContent(
    text: String,
    textColor: Color,
    renderMarkdownAndLatex: Boolean,
    modifier: Modifier = Modifier,
    enableDialogs: Boolean = true,
) {
    if (renderMarkdownAndLatex) {
        StreamMarkdownRenderer(
            content = text,
            textColor = textColor,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            modifier = modifier,
            enableDialogs = enableDialogs,
            fillMaxWidth = false,
        )
    } else {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
    }
}

@Composable
internal fun PlainTextStreamingMessageContent(
    content: String,
    contentStream: Stream<String>?,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val displayStream = rememberRevisableTextStream(contentStream)
    var renderedText by
        remember(displayStream) {
            mutableStateOf(
                (displayStream as? StreamRollbackPrefix)?.rollbackPrefix
                    ?: if (displayStream == null) content else "",
            )
        }

    LaunchedEffect(content, displayStream) {
        if (displayStream == null) {
            renderedText = content
        }
    }

    LaunchedEffect(displayStream) {
        if (displayStream == null) {
            return@LaunchedEffect
        }

        val textBuilder =
            StringBuilder((displayStream as? StreamRollbackPrefix)?.rollbackPrefix ?: "")
        renderedText = textBuilder.toString()
        displayStream.collect { chunk ->
            textBuilder.append(chunk)
            renderedText = textBuilder.toString()
        }
    }

    Text(
        text = renderedText,
        color = textColor,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}
