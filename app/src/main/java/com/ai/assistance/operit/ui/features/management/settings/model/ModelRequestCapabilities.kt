package com.ai.assistance.operit.ui.features.management.settings.model

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/** 五项能力共用一张纵向卡片，整行切换并显示当前开启数量。 */
@Composable
fun ModelRequestCapabilities(
    streaming: Boolean,
    onStreamingChange: (Boolean) -> Unit,
    toolCall: Boolean,
    onToolCallChange: (Boolean) -> Unit,
    images: Boolean,
    onImagesChange: (Boolean) -> Unit,
    audio: Boolean,
    onAudioChange: (Boolean) -> Unit,
    video: Boolean,
    onVideoChange: (Boolean) -> Unit,
    supportsAudioVideo: Boolean,
) {
    val enabledCount = listOf(toolCall, streaming, images, audio && supportsAudioVideo, video && supportsAudioVideo).count { it }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.model_workspace_capabilities),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.model_workspace_capabilities_count, enabledCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                CapabilityRow(stringResource(R.string.model_workspace_tool_call), toolCall, onToolCallChange)
                CapabilityDivider()
                CapabilityRow(stringResource(R.string.model_workspace_streaming), streaming, onStreamingChange)
                CapabilityDivider()
                CapabilityRow(stringResource(R.string.model_workspace_images), images, onImagesChange)
                CapabilityDivider()
                CapabilityRow(stringResource(R.string.model_workspace_audio), audio && supportsAudioVideo, onAudioChange, supportsAudioVideo)
                CapabilityDivider()
                CapabilityRow(stringResource(R.string.model_workspace_video), video && supportsAudioVideo, onVideoChange, supportsAudioVideo)
            }
        }
        Text(
            stringResource(R.string.model_workspace_streaming_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CapabilityDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun CapabilityRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 54.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
