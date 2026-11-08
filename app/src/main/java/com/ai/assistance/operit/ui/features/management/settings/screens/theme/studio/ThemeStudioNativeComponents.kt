package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import com.ai.assistance.operit.ui.theme.script.text.themeTextStroke
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.script.ThemeScriptContract
import com.ai.assistance.operit.ui.features.management.settings.screens.theme.workbench.*
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.CompactToolDisplay
import com.ai.assistance.operit.ui.theme.script.*

/** 设置卡片、工具行和按钮直接复用产品组件；浮层通过真实原生窗口检查。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioComponentsPreview(state: ThemeStudioState) {
    if (state.target.id in ThemeScriptContract.popupSurfaceIds) {
        StudioChatPreview(state)
        return
    }
    var showPanel by remember(state.target.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StudioPick(state, "settingsCard", Modifier.fillMaxWidth()) {
            ThemeWorkbenchCard(stringResource(R.string.studio_group_settings), icon = Icons.Default.Settings) {
                ThemeWorkbenchSwitchRow(stringResource(R.string.studio_component_sample), true, onCheckedChange = {})
            }
        }
        StudioPick(state, "listItem", Modifier.fillMaxWidth()) {
            ThemeScriptSurface("listItem") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.studio_sample_history)) },
                    supportingContent = { Text(stringResource(R.string.studio_component_sample)) },
                    leadingContent = { Icon(Icons.Default.Settings, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = themeScriptContentColor("listItem", MaterialTheme.colorScheme.onSurface), supportingColor = themeScriptContentColor("listItem", MaterialTheme.colorScheme.onSurfaceVariant), leadingIconColor = themeScriptContentColor("listItem", MaterialTheme.colorScheme.onSurface)),
                )
            }
        }
        StudioPick(state, "toolCard", Modifier.fillMaxWidth()) {
            CompactToolDisplay("read_file", "<param name=\"path\">theme.json</param>", themeScriptContentColor("toolCard", MaterialTheme.colorScheme.onSurface), enableDialog = false)
        }
        StudioPick(state, "button") {
            ThemeScriptButton(onClick = {}, enabled = state.previewState != "disabled") { Text(stringResource(R.string.studio_sample_button)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeScriptOutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.theme_appearance_disabled)) }
            ThemeScriptTextButton(onClick = {}) { Text(stringResource(R.string.studio_sample_action)) }
        }
        if (state.target.id in setOf("dialog", "sheet", "menu")) {
            Box {
                ThemeScriptButton(onClick = { showPanel = true }) { Text(stringResource(R.string.studio_open_component_preview)) }
                if (state.target.id == "menu") DropdownMenu(
                    expanded = showPanel,
                    onDismissRequest = { showPanel = false },
                    modifier = Modifier.clip(themeScriptShape("menu", RoundedCornerShape(8.dp))).themeScriptDecoration("menu", originalShape = RoundedCornerShape(8.dp)),
                ) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.studio_sample_action)) }, onClick = { showPanel = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.studio_sample_history)) }, onClick = { showPanel = false })
                }
            }
        }
    }
    if (showPanel && state.target.id == "dialog") ThemeScriptAlertDialog(
        onDismissRequest = { showPanel = false },
        title = { Text(state.target.label()) },
        text = { Text(stringResource(R.string.studio_component_sample)) },
        confirmButton = { ThemeScriptTextButton(onClick = { showPanel = false }) { Text(stringResource(R.string.studio_sample_action)) } },
    )
    if (showPanel && state.target.id == "sheet") ModalBottomSheet(
        onDismissRequest = { showPanel = false },
        modifier = Modifier.themeScriptDecoration("sheet", drawFill = false, originalShape = BottomSheetDefaults.ExpandedShape),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = themeScriptShape("sheet", BottomSheetDefaults.ExpandedShape),
        containerColor = themeScriptFill("sheet", MaterialTheme.colorScheme.surfaceContainerLow),
        contentColor = themeScriptContentColor("sheet", MaterialTheme.colorScheme.onSurface),
    ) {
        Text(state.target.label(), Modifier.padding(24.dp), style = themeScriptTextStyle("sheet", MaterialTheme.typography.titleLarge), textStroke = themeTextStroke("sheet"))
        Text(stringResource(R.string.studio_component_sample), Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        Spacer(Modifier.height(48.dp))
    }
}
