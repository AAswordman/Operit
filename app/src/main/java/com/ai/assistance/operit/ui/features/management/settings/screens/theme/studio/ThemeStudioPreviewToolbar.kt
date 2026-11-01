package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.ActionSheetItem
import com.ai.assistance.operit.ui.components.menu.WorkbenchDropdownMenu

/** 场景与浅深模式只控制预览，在设计工作区内共用一条工具栏。 */
@Composable
internal fun StudioPreviewToolbar(state: ThemeStudioState, onFullPreview: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioSceneSelector(state, Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        listOf("light" to R.string.studio_light, "dark" to R.string.studio_dark).forEach { (mode, label) ->
            TextButton(
                onClick = { state.mode = mode },
                modifier = Modifier.width(52.dp).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.mode == mode) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onFullPreview) {
            Icon(Icons.Default.Fullscreen, stringResource(R.string.studio_full_preview))
        }
        IconButton(onClick = { state.previewCollapsed = !state.previewCollapsed }) {
            Icon(
                if (state.previewCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                stringResource(if (state.previewCollapsed) R.string.studio_expand_preview else R.string.studio_collapse_preview),
            )
        }
    }
}

@Composable
private fun StudioSceneSelector(state: ThemeStudioState, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp),
            color = StudioPanel,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, StudioLine),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.studio_preview_scene),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(stringResource(if (state.scene != StudioScene.Chat) state.scene.label else if (state.previewInputStyle == "agent") R.string.studio_agent_preview else R.string.studio_classic_preview), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
            }
        }
        WorkbenchDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (style in listOf("classic", "agent")) {
                ActionSheetItem(
                    label = stringResource(if (style == "classic") R.string.studio_classic_preview else R.string.studio_agent_preview),
                    icon = Icons.Default.Chat,
                    selected = state.scene == StudioScene.Chat && state.previewInputStyle == style,
                    onClick = { expanded = false; state.previewInputStyle = style; state.selectScene(StudioScene.Chat) },
                )
            }
            listOf(StudioScene.Components, StudioScene.Navigation).forEach { scene ->
                ActionSheetItem(
                    label = stringResource(scene.label),
                    icon = if (scene == StudioScene.Components) Icons.Default.Settings else Icons.Default.Menu,
                    selected = state.scene == scene,
                    onClick = { expanded = false; state.selectScene(scene) },
                )
            }
        }
    }
}
