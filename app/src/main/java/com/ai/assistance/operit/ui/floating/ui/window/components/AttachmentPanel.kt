package com.ai.assistance.operit.ui.floating.ui.window.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import com.ai.assistance.operit.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.PackageSelectorDialog

/**
 * 专为浮动窗口设计的简化附件选择面板
 * 使用LazyRow支持水平滚动，适应不同宽度的窗口
 * 不使用ActivityResultLauncher，只提供特殊附件选项
 */
@Composable
fun FloatingAttachmentPanel(
    visible: Boolean,
    onAttachScreenContent: () -> Unit,
    onAttachNotifications: () -> Unit,
    onAttachLocation: () -> Unit,
    onAttachScreenOcr: () -> Unit,
    onAttachPackage: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    var showPackageDialog by remember { mutableStateOf(false) }

    // 定义附件选项列表，便于使用LazyRow
    val attachmentOptions = listOf(
        AttachmentOptionData(
            icon = Icons.Default.ScreenshotMonitor,
            label = stringResource(R.string.screen_content),
            onClick = onAttachScreenContent
        ),
        AttachmentOptionData(
            icon = Icons.Default.Notifications,
            label = stringResource(R.string.current_notifications),
            onClick = onAttachNotifications
        ),
        AttachmentOptionData(
            icon = Icons.Default.LocationOn,
            label = stringResource(R.string.current_location),
            onClick = onAttachLocation
        ),
        AttachmentOptionData(
            icon = Icons.Default.Crop,
            label = stringResource(R.string.screen_ocr_select),
            onClick = onAttachScreenOcr
        ),
        AttachmentOptionData(
            icon = Icons.Default.AutoAwesome,
            label = stringResource(R.string.attachment_package),
            onClick = { showPackageDialog = true },
            dismissPanelOnClick = false
        )
    )

    // 附件选择面板 - 使用展开动画，从下方向上展开
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(200), expandFrom = Alignment.Bottom) + fadeIn(),
        exit = shrinkVertically(animationSpec = tween(200), shrinkTowards = Alignment.Bottom) + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                // 顶部指示器
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HorizontalDivider(
                        modifier = Modifier
                            .width(32.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp)),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 使用LazyRow让附件选项可以水平滚动
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(attachmentOptions) { option ->
                        AttachmentOption(
                            icon = option.icon,
                            label = option.label,
                            onClick = {
                                option.onClick()
                                if (option.dismissPanelOnClick) {
                                    onDismiss()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 说明文本 - 添加水平padding并居中对齐
                Text(
                    text = stringResource(R.string.floating_window_attachment_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    PackageSelectorDialog(
        visible = showPackageDialog,
        onDismiss = { showPackageDialog = false },
        onPackageSelected = { packageName ->
            onAttachPackage(packageName)
            showPackageDialog = false
            onDismiss()
        }
    )
}

// 附件选项数据类
private data class AttachmentOptionData(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val dismissPanelOnClick: Boolean = true
)

@Composable
private fun AttachmentOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            // 设置最小宽度，确保选项不会太窄
            .width(72.dp)
    ) {
        // 图标区域 - 圆角方形，稍微减小尺寸
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    MaterialTheme.colorScheme.secondaryContainer.copy(
                        alpha = 0.7f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 标签 - 单行显示，超出部分省略
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
