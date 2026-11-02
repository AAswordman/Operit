package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.ThemeScriptPopupPanel

/** 样例内容复用真实弹起菜单容器，位置、形状和描边随当前工程立即变化。 */
@Composable
internal fun StudioChatPopupPreview(state: ThemeStudioState) {
    val id = state.target.id
    val items = when (id) {
        "attachmentMenu" -> listOf(
            R.string.attachment_photo,
            R.string.attachment_camera,
            R.string.attachment_memory,
            R.string.attachment_file,
            R.string.attachment_screen_content,
            R.string.attachment_notifications,
            R.string.attachment_location,
            R.string.chat_continue_generation,
            R.string.floating_voice_call,
            R.string.attachment_package,
        )
        "modelMenu" -> listOf(R.string.thinking_quality, R.string.model_config, R.string.studio_sample_action)
        "chatSettingsMenu" -> listOf(R.string.memory, R.string.agent_menu_tools, R.string.agent_menu_plugins)
        else -> error("Unknown chat popup preview: $id")
    }
    val icon = when (id) {
        "attachmentMenu" -> Icons.Default.AttachFile
        "modelMenu" -> Icons.Default.SmartToy
        else -> Icons.Default.Settings
    }
    Box(Modifier.fillMaxSize()) {
        Text(
            state.target.label(),
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ThemeScriptPopupPanel(
            surfaceId = id,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.width(if (id == "attachmentMenu") 200.dp else 300.dp).heightIn(max = 420.dp),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                items.forEach { label ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
