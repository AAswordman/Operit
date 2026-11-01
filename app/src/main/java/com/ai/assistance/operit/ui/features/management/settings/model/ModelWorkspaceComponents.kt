package com.ai.assistance.operit.ui.features.management.settings.model

import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R

/** 页面状态和呈现单独收纳，业务编辑器继续使用原有配置与保存流程。 */
enum class ModelWorkspaceTab { CONNECTION, PARAMETERS, SUMMARY, FUNCTIONS }

@Composable
fun ModelWorkspaceTabs(selected: ModelWorkspaceTab, onSelect: (ModelWorkspaceTab) -> Unit) {
    val titles = listOf(
        R.string.model_workspace_connection,
        R.string.model_workspace_parameters,
        R.string.model_workspace_summary,
        R.string.model_workspace_functions,
    )
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        ModelWorkspaceTab.values().forEachIndexed { index, tab ->
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                text = { Text(stringResource(titles[index]), style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = if (selected == tab) FontWeight.SemiBold else FontWeight.Normal) },
            )
        }
    }
}

@Composable
fun ModelWorkspaceSection(
    title: String,
    subtitle: String? = null,
    stateKey: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.model_config_collapse else R.string.model_config_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            // 保持编辑器在组合中，收起时不测量、不放置内容，保留输入草稿和保存注册。
            Layout(
                modifier = if (expanded) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clearAndSetSemantics {},
                content = { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
            ) { measurables, constraints ->
                if (expanded) {
                    val body = measurables.single().measure(constraints)
                    layout(body.width, body.height) { body.placeRelative(0, 0) }
                } else {
                    layout(constraints.minWidth, 0) {}
                }
            }
        }
    }
}

@Composable
fun ModelWorkspaceDialog(
    title: String,
    onDismiss: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
    // 所有工作区窗口使用居中 Dialog，统一限制高度并给键盘和小屏留出空间。
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(max = maxHeight),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.common_close))
                    }
                }
                Spacer(Modifier.height(8.dp))
                val bodyModifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                Column(
                    modifier = if (scrollable) bodyModifier.verticalScroll(rememberScrollState()) else bodyModifier,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun ModelWorkspaceChoiceDialog(
    title: String,
    value: String,
    choices: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    ModelWorkspaceDialog(title = title, onDismiss = onDismiss) {
        choices.forEach { (choice, label) ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSelected(choice) },
                shape = RoundedCornerShape(10.dp),
                color = if (choice == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (choice == value) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** 行菜单也统一居中，操作项继续使用现有的点击回调。 */
@Composable
fun ModelWorkspaceMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (expanded) {
        ModelWorkspaceDialog(title = stringResource(R.string.model_workspace_choose), onDismiss = onDismissRequest) {
            Column(modifier.fillMaxWidth(), content = content)
        }
    }
}
