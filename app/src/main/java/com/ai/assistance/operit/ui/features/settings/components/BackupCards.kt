package com.ai.assistance.operit.ui.features.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatV1

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewCard(
    totalChatCount: Int,
    totalCharacterCardCount: Int,
    totalMemoryCount: Int,
    totalLinkCount: Int,
    activeProfileName: String
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.backup_data_overview),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.backup_current_profile, activeProfileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NativeThemeStatV1(
                    label = stringResource(R.string.backup_chat_count),
                    value = "$totalChatCount",
                    leading = { modifier ->
                        Icon(Icons.Default.History, contentDescription = null, modifier = modifier)
                    },
                )
                NativeThemeStatV1(
                    label = stringResource(R.string.backup_character_card_count),
                    value = "$totalCharacterCardCount",
                    leading = { modifier ->
                        Icon(Icons.Default.Person, contentDescription = null, modifier = modifier)
                    },
                )
                NativeThemeStatV1(
                    label = stringResource(R.string.backup_memory_count),
                    value = "$totalMemoryCount",
                    leading = { modifier ->
                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = modifier)
                    },
                )
                NativeThemeStatV1(
                    label = stringResource(R.string.backup_memory_link_count),
                    value = "$totalLinkCount",
                    leading = { modifier ->
                        Icon(Icons.Default.Link, contentDescription = null, modifier = modifier)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupFilesStatisticsCard(
    chatBackupCount: Int,
    characterCardBackupCount: Int,
    memoryBackupCount: Int,
    modelConfigBackupCount: Int,
    roomDbBackupCount: Int,
    isScanning: Boolean,
    onRefresh: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "📁 " + stringResource(R.string.backup_files_statistics),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.backup_files_statistics_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    FilledTonalButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.backup_refresh),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BackupFileStatItem(
                    icon = Icons.Default.History,
                    count = chatBackupCount,
                    label = stringResource(R.string.backup_chat_files),
                    color = MaterialTheme.colorScheme.primary
                )
                BackupFileStatItem(
                    icon = Icons.Default.Person,
                    count = characterCardBackupCount,
                    label = stringResource(R.string.backup_character_card_files),
                    color = MaterialTheme.colorScheme.primary
                )
                BackupFileStatItem(
                    icon = Icons.Default.Psychology,
                    count = memoryBackupCount,
                    label = stringResource(R.string.backup_memory_files),
                    color = MaterialTheme.colorScheme.secondary
                )
                BackupFileStatItem(
                    icon = Icons.Default.Settings,
                    count = modelConfigBackupCount,
                    label = stringResource(R.string.backup_model_config_files),
                    color = MaterialTheme.colorScheme.tertiary
                )
                BackupFileStatItem(
                    icon = Icons.Default.Storage,
                    count = roomDbBackupCount,
                    label = stringResource(R.string.backup_room_db_files),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!isScanning) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "💡 " + stringResource(R.string.backup_files_location_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BackupFileStatItem(
    icon: ImageVector,
    count: Int,
    label: String,
    color: Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
