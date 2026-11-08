package com.ai.assistance.operit.ui.features.extensions.packages.components

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PackageItem(
        name: String,
        description: String,
        isImported: Boolean,
        onClick: () -> Unit,
        onToggleImport: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
        ) {
                Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
                ) {
            // 图标
                                        Icon(
                                                imageVector = Icons.Default.Extension,
                                                contentDescription = null,
                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                        )
            
            Spacer(modifier = Modifier.width(8.dp))

            // 文本内容 - 添加右侧边距防止撞到开关
                        Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                        ) {
                                Text(
                                        text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                                )
                        }

            // 开关
                        ExtensionSwitch(
                                checked = isImported,
                                onCheckedChange = onToggleImport,
                        )
                }
        }
}
