package com.ai.assistance.operit.ui.features.packages.components.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.ui.features.packages.components.dialogs.actions.MCPServerDetailsActions
import com.ai.assistance.operit.ui.features.packages.components.dialogs.content.MCPServerConfigContent
import com.ai.assistance.operit.ui.features.packages.components.dialogs.content.MCPServerDetailsContent
import com.ai.assistance.operit.ui.features.packages.components.dialogs.header.MCPServerDetailsHeader
import com.ai.assistance.operit.ui.features.packages.components.dialogs.tabs.MCPServerDetailsTabs
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.mcp.MCPLocalServer

/**
 * A dialog that displays detailed information about an MCP server.
 *
 * @param server The MCP server to display details for
 * @param onDismiss Callback to be invoked when the dialog is dismissed
 * @param onInstall Callback to be invoked when the install button is clicked
 * @param onUninstall Callback to be invoked when the uninstall button is clicked
 * @param installedPath 已安装插件的路径，如果未安装则为null
 * @param pluginConfig 插件配置，只在已安装的插件中提供
 * @param onSaveConfig 保存配置回调
 * @param onUpdateConfig 更新配置回调
 * @param mdFontSize Markdown内容的字体大小
 */
@Composable
fun MCPServerDetailsDialog(
        server: MCPLocalServer.PluginMetadata,
        onDismiss: () -> Unit,
        onInstall: (MCPLocalServer.PluginMetadata) -> Unit,
        onUninstall: (MCPLocalServer.PluginMetadata) -> Unit,
        installedPath: String? = null,
        pluginConfig: String = "",
        onSaveConfig: () -> Unit = {},
        onUpdateConfig: (String) -> Unit = {},
        mdFontSize: Float = 14f,
        onOpenMarketDetail: (MarketV2Entry) -> Unit = {}
) {
    val isInstalled = server.isInstalled
    val supportsLocalConfigEditing = server.type != "remote"
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    // 添加标签页状态
    var selectedTabIndex by remember { mutableStateOf(0) }

    // 本地编辑的配置内容
    var localPluginConfig by remember { mutableStateOf(pluginConfig) }

    // 编辑结果同步到父组件
    LaunchedEffect(localPluginConfig) { onUpdateConfig(localPluginConfig) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
                modifier =
                        Modifier.fillMaxWidth(0.95f) // Take 95% of the screen width
                                .fillMaxHeight(
                                        0.7f
                                ) // Take 70% of the screen height (reduced from 0.85f)
                                .heightIn(
                                        min = 400.dp,
                                        max = screenHeight * 0.7f // Reduced maximum height
                                ) // Responsive height
                                .padding(vertical = 8.dp), // Reduced vertical padding
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header (Logo, title, badges, etc.)
                    MCPServerDetailsHeader(server = server, onDismiss = onDismiss)

                    // Tabs if installed
                    if (isInstalled && supportsLocalConfigEditing) {
                        MCPServerDetailsTabs(
                                selectedTabIndex = selectedTabIndex,
                                onTabSelected = { selectedTabIndex = it }
                        )
                    }

                    // Content based on selected tab - Note the paddingBottom to make room for
                    // actions
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .weight(1f)
                                            .padding(bottom = 56.dp) // Make space for actions
                    ) {
                        if (!isInstalled || !supportsLocalConfigEditing || selectedTabIndex == 0) {
                            // Details tab
                            MCPServerDetailsContent(
                                    server = server,
                                    modifier = Modifier.fillMaxSize(), // Fill the available space
                                    mdFontSize = mdFontSize.sp // Pass the font size parameter
                            )
                        } else {
                            // Config tab
                            MCPServerConfigContent(
                                    localPluginConfig = localPluginConfig,
                                    onConfigChanged = { localPluginConfig = it },
                                    installedPath = installedPath,
                                    onSaveConfig = onSaveConfig,
                                    modifier = Modifier.fillMaxSize() // Fill the available space
                            )
                        }
                    }
                }

                // Bottom action buttons - Fixed at bottom
                Surface(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        tonalElevation = 3.dp, // Slightly elevated
                        shadowElevation = 4.dp // Add shadow for visual separation
                ) {
                    MCPServerDetailsActions(
                            server = server,
                            isInstalled = isInstalled,
                            onInstall = onInstall,
                            onUninstall = onUninstall,
                            installedPath = installedPath,
                            onOpenMarketDetail = onOpenMarketDetail
                    )
                }
            }
        }
    }
}
