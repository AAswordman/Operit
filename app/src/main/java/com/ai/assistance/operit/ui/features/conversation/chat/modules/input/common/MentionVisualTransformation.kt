package com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.mentions.findCommittedMentionTokens

@Composable
fun rememberMentionVisualTransformation(textStyle: TextStyle): VisualTransformation {
    val colors = MaterialTheme.colorScheme
    val baseFontSize =
        if (textStyle.fontSize.isUnspecified) {
            14.sp
        } else {
            textStyle.fontSize
        }
    return remember(baseFontSize, colors.primary) {
        MentionVisualTransformation(
            mentionFontSize = baseFontSize * 0.88f,
            mentionColor = colors.primary,
            mentionBackground = colors.primary.copy(alpha = 0.14f),
        )
    }
}

private class MentionVisualTransformation(
    private val mentionFontSize: TextUnit,
    private val mentionColor: Color,
    private val mentionBackground: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val mentionTokens = findCommittedMentionTokens(text.text)
        if (mentionTokens.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val transformed =
            buildAnnotatedString {
                append(text)
                mentionTokens.forEach { token ->
                    addStyle(
                        SpanStyle(
                            color = mentionColor,
                            background = mentionBackground,
                            fontSize = mentionFontSize,
                            fontWeight = FontWeight.Medium,
                        ),
                        token.start,
                        token.contentEndExclusive,
                    )
                }
            }
        return TransformedText(transformed, OffsetMapping.Identity)
    }
}
