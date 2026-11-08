package com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager

/** 双方风格同时可见，右侧保留当前选择，不复用一组会联动的状态。 */
@Composable
internal fun ThemeMessageStyleSelector(title: String, selected: String, onSelected: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            themeMessageStyleLabel(selected),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ThemeWorkbenchSegments(
        options = listOf(
            UserPreferencesManager.CHAT_STYLE_BUBBLE to stringResource(R.string.chat_style_bubble),
            UserPreferencesManager.CHAT_STYLE_CURSOR to stringResource(R.string.chat_style_cursor),
        ),
        selected = selected,
        onSelected = onSelected,
    )
}

@Composable
internal fun themeMessageStyleLabel(style: String): String = stringResource(
    if (style == UserPreferencesManager.CHAT_STYLE_BUBBLE) R.string.chat_style_bubble else R.string.chat_style_cursor,
)