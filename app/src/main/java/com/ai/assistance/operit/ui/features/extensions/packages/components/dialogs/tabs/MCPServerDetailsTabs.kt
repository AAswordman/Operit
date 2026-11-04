package com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/**
 * Tabs component for the MCP server details diaAppLogger.
 *
 * @param selectedTabIndex The currently selected tab index
 * @param onTabSelected Callback to be invoked when a tab is selected
 */
@Composable
fun MCPServerDetailsTabs(selectedTabIndex: Int, onTabSelected: (Int) -> Unit) {
    val horizontalPadding = 16.dp
    val tabSpacing = 4.dp

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column {
            // 轻量级自定义TabRow
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .height(32.dp)
                                    .padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // 详情选项卡
                Surface(
                        modifier =
                                Modifier.weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .clickable { onTabSelected(0) },
                        color =
                                if (selectedTabIndex == 0)
                                        MaterialTheme.colorScheme.primaryContainer.copy(
                                                alpha = 0.2f
                                        )
                                else MaterialTheme.colorScheme.surface
                ) {
                    Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint =
                                        if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary
                                        else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                                stringResource(R.string.mcp_plugin_details),
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                        if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary
                                        else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(tabSpacing))

                // 配置选项卡
                Surface(
                        modifier =
                                Modifier.weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .clickable { onTabSelected(1) },
                        color =
                                if (selectedTabIndex == 1)
                                        MaterialTheme.colorScheme.primaryContainer.copy(
                                                alpha = 0.2f
                                        )
                                else MaterialTheme.colorScheme.surface
                ) {
                    Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint =
                                        if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary
                                        else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                                stringResource(R.string.mcp_config_settings),
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                        if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary
                                        else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
                        )
                    }
                }
            }

            // 选项卡指示器实现（下划线）
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(horizontal = horizontalPadding)
                                    .height(1.dp)
                                    .background(
                                            MaterialTheme.colorScheme.outlineVariant.copy(
                                                    alpha = 0.5f
                                            )
                                    )
            ) {
                // 第一个标签的区域
                Box(
                        modifier =
                                Modifier.weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                                if (selectedTabIndex == 0)
                                                        MaterialTheme.colorScheme.primary
                                                else
                                                        MaterialTheme.colorScheme.outlineVariant
                                                                .copy(alpha = 0.5f)
                                        )
                )

                // 间隔区域
                Spacer(modifier = Modifier.width(tabSpacing).fillMaxHeight())

                // 第二个标签的区域
                Box(
                        modifier =
                                Modifier.weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                                if (selectedTabIndex == 1)
                                                        MaterialTheme.colorScheme.primary
                                                else
                                                        MaterialTheme.colorScheme.outlineVariant
                                                                .copy(alpha = 0.5f)
                                        )
                )
            }
        }
    }
}
