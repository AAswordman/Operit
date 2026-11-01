package com.ai.assistance.operit.ui.features.management.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RoomDbBackupListItem(
    file: File,
    onRestoreClick: () -> Unit
) {
    val parsed = remember(file.name) {
        val name = file.name
        when {
            name.startsWith("room_db_backup_") && name.endsWith(".zip") -> {
                Pair(
                    R.string.backup_room_db_backup_type_auto,
                    name.removePrefix("room_db_backup_").removeSuffix(".zip")
                )
            }

            name.startsWith("room_db_manual_backup_") && name.endsWith(".zip") -> {
                val raw = name.removePrefix("room_db_manual_backup_").removeSuffix(".zip")
                val formatted = try {
                    val input = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                    val output = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    output.format(input.parse(raw)!!)
                } catch (_: Exception) {
                    raw
                }
                Pair(R.string.backup_room_db_backup_type_manual, formatted)
            }

            else -> Pair(R.string.backup_room_db_backup_type_manual, name)
        }
    }
    val typeLabel = stringResource(parsed.first)
    val displayTime = parsed.second

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.backup_room_db_restore_to_day, "$typeLabel $displayTime"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRestoreClick) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = stringResource(R.string.backup_room_db_restore_confirm_action)
                )
            }
        }
    }
}
