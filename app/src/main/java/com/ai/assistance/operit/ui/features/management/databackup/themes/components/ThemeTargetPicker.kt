package com.ai.assistance.operit.ui.features.management.databackup.themes.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.character.ActivePrompt
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.ui.common.themetarget.ThemeTargetAvatar
import com.ai.assistance.operit.ui.common.themetarget.ThemeTargetOption
import com.ai.assistance.operit.ui.common.themetarget.ThemeTargetPicker as SharedThemeTargetPicker

/** 导入仅提供角色卡目标，复用主题页的搜索与选中列表。 */
@Composable
internal fun ThemeTargetPicker(
    targets: List<CharacterCard>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val options =
        remember(targets) {
            targets.map { ThemeTargetOption(ActivePrompt.CharacterCard(it.id), it.name) }
        }
    SharedThemeTargetPicker(options, selectedId?.let { ActivePrompt.CharacterCard(it) }, enabled) {
        target ->
        onSelect((target as ActivePrompt.CharacterCard).id)
    }
}

@Composable
internal fun ThemeTargetSelection(card: CharacterCard?, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (card == null) ThemeTransferRoleAvatar(null)
            else ThemeTargetAvatar(ActivePrompt.CharacterCard(card.id))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.theme_transfer_apply_to),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    card?.name ?: stringResource(R.string.theme_transfer_target),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
