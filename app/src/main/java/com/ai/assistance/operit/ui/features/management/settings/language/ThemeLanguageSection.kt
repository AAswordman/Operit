package com.ai.assistance.operit.ui.features.management.settings.language

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.android.LocaleUtils

/** 语言属于应用全局配置，不进入角色主题的保存、重置和应用到全部角色的数据。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeLanguageSection(
    cardColors: CardColors,
    enabled: Boolean,
    onLanguageSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val storedCode by
        preferences.appLanguage.collectAsState(initial = preferences.getCurrentLanguage())
    val supported = remember { LocaleUtils.getSupportedLanguages() }
    val selectedCode =
        remember(context, storedCode) {
            if (storedCode.isBlank() || storedCode == LocaleUtils.LanguageCodes.AUTO)
                LocaleUtils.LanguageCodes.AUTO
            else LocaleUtils.getCurrentLanguage(context)
        }
    val followSystem = stringResource(R.string.language_follow_system)
    val displayLocale = LocalConfiguration.current.locales[0]
    fun label(language: LocaleUtils.Language): String =
        if (language.code == LocaleUtils.LanguageCodes.AUTO) followSystem
        else Locale.forLanguageTag(language.code).getDisplayName(displayLocale)
            .replaceFirstChar { it.titlecase(displayLocale) }
    val selected = supported.first { it.code == selectedCode }
    var expanded by remember { mutableStateOf(false) }
    com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchCard(
        stringResource(R.string.language_settings),
        icon = Icons.Default.Language,
    ) {
        com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchRow(
            title = stringResource(R.string.select_language),
            value = label(selected),
            enabled = enabled,
            subtitle = stringResource(R.string.language_global_info),
            onClick = { expanded = true },
        )
    }
    if (expanded)
        com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDialog(
            stringResource(R.string.language_settings),
            { expanded = false },
        ) {
            Text(
                stringResource(R.string.language_global_info),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            supported.forEach { language ->
                com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench
                    .ThemeWorkbenchRow(
                        title = label(language),
                        enabled = enabled,
                        trailing = {
                            if (language.code == selectedCode) Icon(Icons.Default.Check, null)
                        },
                        onClick = {
                            expanded = false
                            if (language.code != selectedCode) onLanguageSelected(language.code)
                        },
                    )
            }
        }
}
