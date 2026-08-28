package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

internal enum class ThemeSettingsTab(val titleRes: Int) {
    COLORS_AND_MODE(R.string.theme_title_color),
    TYPOGRAPHY(R.string.theme_font_settings),
    BACKGROUND(R.string.theme_tab_background),
    CONVERSATION(R.string.theme_tab_chat),
    MESSAGE_DETAILS(R.string.display_options_title),
    COMPOSER(R.string.theme_tab_input),
    APP_CHROME(R.string.theme_tab_interface),
}

@Composable
internal fun ThemeSettingsTabbedContent(
    selectedTab: ThemeSettingsTab,
    onSelectedTabChange: (ThemeSettingsTab) -> Unit,
    colorsAndModeContent: @Composable () -> Unit,
    typographyContent: @Composable () -> Unit,
    backgroundContent: @Composable () -> Unit,
    conversationContent: @Composable () -> Unit,
    messageDetailsContent: @Composable () -> Unit,
    composerContent: @Composable () -> Unit,
    appChromeContent: @Composable () -> Unit,
    footerContent: @Composable () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    var compactDetailVisible by remember { mutableStateOf(false) }
    val categoryScrollState = rememberScrollState()

    BoxWithConstraints(modifier = modifier) {
        val compact = maxWidth < 720.dp
        LaunchedEffect(compact) {
            if (!compact) compactDetailVisible = false
        }
        LaunchedEffect(selectedTab, compactDetailVisible) {
            scrollState.scrollTo(0)
        }
        BackHandler(enabled = compact && compactDetailVisible) {
            compactDetailVisible = false
        }

        val selectedContent: @Composable () -> Unit =
            when (selectedTab) {
                ThemeSettingsTab.COLORS_AND_MODE -> colorsAndModeContent
                ThemeSettingsTab.TYPOGRAPHY -> typographyContent
                ThemeSettingsTab.BACKGROUND -> backgroundContent
                ThemeSettingsTab.CONVERSATION -> conversationContent
                ThemeSettingsTab.MESSAGE_DETAILS -> messageDetailsContent
                ThemeSettingsTab.COMPOSER -> composerContent
                ThemeSettingsTab.APP_CHROME -> appChromeContent
            }

        Column(modifier = Modifier.fillMaxWidth()) {
            if (compact) {
                if (compactDetailVisible) {
                    ThemeSettingsCompactDetailHeader(
                        category = selectedTab,
                        onBack = { compactDetailVisible = false },
                    )
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(scrollState)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        selectedContent()
                    }
                } else {
                    ThemeSettingsCategoryList(
                        selectedTab = selectedTab,
                        onSelectedTabChange = {
                            onSelectedTabChange(it)
                            compactDetailVisible = true
                        },
                        scrollState = categoryScrollState,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Row(modifier = Modifier.weight(1f)) {
                    ThemeSettingsCategoryList(
                        selectedTab = selectedTab,
                        onSelectedTabChange = onSelectedTabChange,
                        scrollState = categoryScrollState,
                        modifier = Modifier.width(224.dp),
                    )
                    VerticalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                    )
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(scrollState)
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        selectedContent()
                    }
                }
            }

            HorizontalDivider()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, bottom = 8.dp),
            ) {
                footerContent()
            }
        }
    }
}

@Composable
private fun ThemeSettingsCategoryList(
    selectedTab: ThemeSettingsTab,
    onSelectedTabChange: (ThemeSettingsTab) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(scrollState).padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        ThemeSettingsTab.values().forEachIndexed { index, tab ->
            if (index > 0) {
                HorizontalDivider()
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(
                            role = Role.Button,
                            onClick = { onSelectedTabChange(tab) },
                        )
                        .semantics {
                            role = Role.Button
                        }
                        .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(tab.titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (selectedTab == tab) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint =
                        if (selectedTab == tab) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun ThemeSettingsCompactDetailHeader(
    category: ThemeSettingsTab,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(text = stringResource(category.titleRes), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun ThemeSettingsFooter(
    showSaveSuccessMessage: Boolean,
    onShowSaveSuccessMessageChange: (Boolean) -> Unit,
    saveEnabled: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    Button(
        onClick = onSave,
        enabled = saveEnabled,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp),
                strokeWidth = 2.dp,
            )
        }
        Text(stringResource(id = R.string.save_action))
    }

    OutlinedButton(
        onClick = onReset,
        enabled = !isSaving,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(stringResource(id = R.string.theme_reset))
    }

    if (showSaveSuccessMessage) {
        LaunchedEffect(key1 = showSaveSuccessMessage) {
            kotlinx.coroutines.delay(2000)
            onShowSaveSuccessMessageChange(false)
        }

        Text(
            text = stringResource(id = R.string.theme_saved),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
