package com.ai.assistance.operit.ui.features.extensions.packages.management

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/** 将 MCP 初始化和刷新放在状态行，明确区分导入与服务启动。 */
@Composable
fun MCPServiceActions(
    readyCount: Int,
    enabledCount: Int,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onStart: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.mcp_management), style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.mcp_services_ready_count, readyCount, enabledCount),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(
            onClick = onStart,
            enabled = !isLoading && !isRefreshing,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (isLoading) R.string.mcp_starting_services else R.string.mcp_start_services))
        }
        IconButton(onClick = onRefresh, enabled = !isRefreshing && !isLoading) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
        }
    }
}