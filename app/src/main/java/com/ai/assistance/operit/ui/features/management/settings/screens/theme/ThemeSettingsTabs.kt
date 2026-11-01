package com.ai.assistance.operit.ui.features.management.settings.screens.theme

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.*
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchDialog
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.ThemeWorkbenchRow

internal enum class ThemeSettingsTab(val titleRes: Int) {
    BASIC(R.string.theme_workbench_tab_theme),
    BACKGROUND(R.string.theme_tab_background),
    CHAT(R.string.theme_workbench_tab_messages),
    INTERFACE(R.string.theme_tab_interface),
    LAYOUT(R.string.theme_layout_tab),
}

@Composable
internal fun ThemeSettingsTabbedContent(
    selectedTab: ThemeSettingsTab,
    onSelectedTabChange: (ThemeSettingsTab) -> Unit,
    basicContent: @Composable () -> Unit,
    backgroundContent: @Composable () -> Unit,
    chatContent: @Composable () -> Unit,
    interfaceContent: @Composable () -> Unit,
    layoutContent: @Composable () -> Unit,
    previewContent: @Composable () -> Unit,
    footerContent: @Composable () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(selectedTab) { scrollState.scrollTo(0) }
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        TabRow(selectedTabIndex = selectedTab.ordinal, containerColor = Color.Transparent) {
            ThemeSettingsTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onSelectedTabChange(tab) },
                    text = {
                        Text(
                            stringResource(tab.titleRes),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }
        Column(
            Modifier.weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            previewContent()
            when (selectedTab) {
                ThemeSettingsTab.BASIC -> basicContent()
                ThemeSettingsTab.BACKGROUND -> backgroundContent()
                ThemeSettingsTab.CHAT -> chatContent()
                ThemeSettingsTab.INTERFACE -> interfaceContent()
                ThemeSettingsTab.LAYOUT -> layoutContent()
            }
        }
        footerContent()
    }
}

@Composable
internal fun ThemeSettingsFooter(
    targetName: String,
    hasUnsavedChanges: Boolean,
    globalLayout: Boolean,
    hasLayoutChanges: Boolean,
    showSaveSuccessMessage: Boolean,
    onShowSaveSuccessMessageChange: (Boolean) -> Unit,
    saveEnabled: Boolean,
    isSaving: Boolean,
    showApplyToAll: Boolean,
    applyToAllEnabled: Boolean,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDiscard: () -> Unit,
    onPreview: () -> Unit,
    onApplyToAllCharacterCards: () -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(showSaveSuccessMessage) {
        if (showSaveSuccessMessage) {
            kotlinx.coroutines.delay(2000)
            onShowSaveSuccessMessageChange(false)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (globalLayout) stringResource(R.string.theme_layout_global_title) else targetName, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(
                    stringResource(
                        when {
                            showSaveSuccessMessage -> R.string.theme_saved
                            hasUnsavedChanges && (globalLayout || hasLayoutChanges) -> R.string.theme_layout_unsaved
                            globalLayout -> R.string.theme_layout_global_hint
                            hasUnsavedChanges -> R.string.theme_workbench_unsaved
                            else -> R.string.theme_workbench_saved
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showMore = true }, enabled = !isSaving) {
                Icon(Icons.Default.MoreHoriz, stringResource(R.string.theme_workbench_more))
            }
            IconButton(onClick = onPreview) {
                Icon(
                    Icons.Default.Visibility,
                    stringResource(R.string.theme_workbench_full_preview),
                )
            }
            Button(onClick = onSave, enabled = saveEnabled) {
                if (isSaving)
                    CircularProgressIndicator(
                        Modifier.size(18.dp).padding(end = 4.dp),
                        strokeWidth = 2.dp,
                    )
                Text(stringResource(R.string.save_action))
            }
        }
    }
    if (showMore)
        ThemeWorkbenchDialog(stringResource(R.string.theme_workbench_more), { showMore = false }) {
            ThemeWorkbenchRow(
                stringResource(R.string.theme_workbench_discard),
                enabled = hasUnsavedChanges,
            ) {
                onDiscard()
                showMore = false
            }
            if (showApplyToAll)
                ThemeWorkbenchRow(
                    stringResource(R.string.theme_apply_to_all_character_cards),
                    enabled = applyToAllEnabled,
                ) {
                    showMore = false
                    onApplyToAllCharacterCards()
                }
            ThemeWorkbenchRow(stringResource(R.string.theme_reset)) {
                showMore = false
                showResetConfirm = true
            }
        }
    if (showResetConfirm)
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.theme_reset)) },
            text = { Text(stringResource(R.string.theme_layout_theme_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        showResetConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
}
