package com.ai.assistance.operit.ui.features.management.settings.sections

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class ApiKeyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val length = originalText.length

        // 保持长度不变：只在中间用 '*' 替换字符
        val maskedText = if (length > 8) {
            val middleCount = length - 8
            buildString(length) {
                append(originalText.substring(0, 4))
                repeat(middleCount) { append('*') }
                append(originalText.substring(length - 4))
            }
        } else {
            // 长度较短时直接显示原文，避免过度遮挡
            originalText
        }

        return TransformedText(
            text = AnnotatedString(maskedText),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    return offset
                }

                override fun transformedToOriginal(offset: Int): Int {
                    return offset
                }
            }
        )
    }
}
