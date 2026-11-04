package com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import androidx.compose.ui.res.stringResource

/**
 * Content component for the MCP server details diaAppLogger.
 * 
 * @param server The MCP server to display details for
 * @param modifier Modifier for the component
 * @param mdFontSize Font size for markdown content
 */
@Composable
fun MCPServerDetailsContent(
    server: MCPLocalServer.PluginMetadata,
    modifier: Modifier = Modifier,
    mdFontSize: TextUnit = 14.sp
) {
    LazyColumn(
            modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
    ) {

        // 描述内容
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = server.description.takeIf { it.isNotBlank() } ?: stringResource(R.string.mcp_no_description),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = mdFontSize),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
