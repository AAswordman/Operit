package com.ai.assistance.operit.ui.features.management.settings.screens.theme.layout

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.layout.ChatLayoutSettings
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemePreviewInput
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemePreviewMessage
import com.ai.assistance.operit.ui.theme.AiMarkdownTextLayoutSettings
import com.ai.assistance.operit.ui.theme.LocalAiMarkdownTextLayoutSettings

/** 只在预览子树注入草稿排版，真实对话继续读取已经保存的全局设置。 */
@Composable
internal fun ThemeLayoutPreviewContent(
    values: ThemePreferenceValues,
    settings: ChatLayoutSettings,
    userName: String,
) {
    CompositionLocalProvider(
        LocalAiMarkdownTextLayoutSettings provides
            AiMarkdownTextLayoutSettings(
                lineHeightMultiplier = settings.aiMarkdownLineHeightMultiplier,
                letterSpacingSp = settings.aiMarkdownLetterSpacing,
                paragraphSpacingDp = settings.aiMarkdownParagraphSpacing,
            )
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(
                        start = 14.dp,
                        top = 10.dp,
                        bottom = 10.dp,
                        end = settings.chatSettingsButtonEndPadding.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.theme_layout_chat_spacing),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Tune,
                            stringResource(R.string.theme_layout_button),
                            Modifier.size(18.dp),
                        )
                    }
                }
            }
            HorizontalDivider()
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = settings.chatAreaHorizontalPadding.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThemePreviewMessage(values, user = true, userName = userName)
                ThemePreviewMessage(
                    values,
                    user = false,
                    userName = userName,
                    markdownPreview = true,
                )
            }
            Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                ThemePreviewInput(values)
            }
        }
    }
}
