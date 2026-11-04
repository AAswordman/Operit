package com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.header

import com.ai.assistance.operit.util.logging.AppLogger
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Header component for the MCP server details diaAppLogger.
 * 
 * @param server The MCP server to display details for
 * @param onDismiss Callback to be invoked when the close button is clicked
 */
@Composable
fun MCPServerDetailsHeader(
    server: MCPLocalServer.PluginMetadata,
    onDismiss: () -> Unit
) {
    // 头部区域 - 更平衡的设计
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo
            var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
            var imageLoadJob by remember { mutableStateOf<Job?>(null) }
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(server.logoUrl) {
                if (!server.logoUrl.isNullOrBlank()) {
                    imageLoadJob?.cancel()
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!.asImageBitmap(),
                        contentDescription = "${server.name} logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(28.dp).padding(4.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 信息区 - 更合理的垂直布局
            Column(modifier = Modifier.weight(1f)) {
                // 标题行 - 拆分标题和关闭按钮，解决挤压问题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 名称和版本区域
                    Column(
                        modifier = Modifier.weight(1f, fill = true)
                    ) {
                        // 名称
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleSmall, // 改为更小的标题
                            fontWeight = FontWeight.Bold,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )

                        // 作者和版本信息放一行
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (server.author.isNotBlank() && server.author != "Unknown") {
                                Text(
                                    text = "by ${server.author}",
                                    style = MaterialTheme.typography.labelSmall, // 更小的文本
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            // 版本信息放在作者旁边
                            if (server.version.isNotBlank()) {
                                if (server.author.isNotBlank() && server.author != "Unknown") {
                                    Text(
                                        text = " · ",
                                        style = MaterialTheme.typography.labelSmall, // 更小的文本
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "v${server.version}",
                                    style = MaterialTheme.typography.labelSmall, // 更小的文本
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // 关闭按钮 - 独立成单独的元素
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.pkg_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp)) // 减少空间

                // 徽章行 - 合理的间距，移除版本信息
                
            }
        }
    }
} 