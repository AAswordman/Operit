package com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/**
 * Config content component for the MCP server details diaAppLogger.
 *
 * @param localPluginConfig The current plugin configuration
 * @param onConfigChanged Callback to be invoked when the config is changed
 * @param installedPath The path where the plugin is installed
 * @param onSaveConfig Callback to be invoked when the save button is clicked
 * @param modifier Optional modifier for the component
 */
@Composable
fun MCPServerConfigContent(
    localPluginConfig: String,
    onConfigChanged: (String) -> Unit,
    installedPath: String?,
    onSaveConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (installedPath != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        stringResource(R.string.mcp_server_plugin_install_path_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        installedPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
            stringResource(R.string.mcp_server_json_config_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = localPluginConfig,
            onValueChange = { onConfigChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = {
                Text(
                    "{\"key\": \"value\"}",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            textStyle = MaterialTheme.typography.bodySmall
        )

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    stringResource(R.string.mcp_server_config_effective_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Button(
            onClick = onSaveConfig,
            modifier = Modifier.align(Alignment.End),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp,
                vertical = 6.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                stringResource(R.string.mcp_server_save_config),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
