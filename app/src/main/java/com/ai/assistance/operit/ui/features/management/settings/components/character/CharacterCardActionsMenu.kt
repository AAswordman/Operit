package com.ai.assistance.operit.ui.features.management.settings.components.character

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/** 仅负责角色卡按钮弹出的菜单，原有操作条件和确认流程由调用方保留。 */
@Composable
fun CharacterCardActionsMenu(
    expanded: Boolean,
    isActive: Boolean,
    isDefault: Boolean,
    onDismiss: () -> Unit,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(208.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        fun invoke(action: () -> Unit) {
            onDismiss()
            action()
        }
        if (!isActive) {
            CharacterAction(
                Icons.Default.Check,
                stringResource(R.string.set_active),
                onClick = { invoke(onSetActive) },
            )
            HorizontalDivider(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
        CharacterAction(
            Icons.Default.Edit,
            stringResource(R.string.edit),
            onClick = { invoke(onEdit) },
        )
        CharacterAction(
            Icons.Default.ContentCopy,
            stringResource(R.string.duplicate),
            onClick = { invoke(onDuplicate) },
        )
        CharacterAction(
            Icons.Default.Share,
            stringResource(R.string.export),
            onClick = { invoke(onExport) },
        )
        HorizontalDivider(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        if (isDefault)
            CharacterAction(
                Icons.Default.Restore,
                stringResource(R.string.reset),
                destructive = true,
                onClick = { invoke(onReset) },
            )
        else
            CharacterAction(
                Icons.Outlined.DeleteOutline,
                stringResource(R.string.delete),
                destructive = true,
                onClick = { invoke(onDelete) },
            )
    }
}

@Composable
private fun CharacterAction(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color =
        if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        leadingIcon = {
            Box(
                Modifier.background(color.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                    .padding(7.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
            }
        },
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
    )
}
