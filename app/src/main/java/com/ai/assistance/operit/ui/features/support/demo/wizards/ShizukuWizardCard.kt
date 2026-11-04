package com.ai.assistance.operit.ui.features.support.demo.wizards

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

@Composable
fun ShizukuWizardCard(
        isShizukuInstalled: Boolean,
        isShizukuRunning: Boolean,
        hasShizukuPermission: Boolean,
        showWizard: Boolean,
        onToggleWizard: (Boolean) -> Unit,
        onInstallFromStore: () -> Unit,
        onInstallBundled: () -> Unit,
        onOpenShizuku: () -> Unit,
        onWatchTutorial: () -> Unit,
        onRequestPermission: () -> Unit,
        updateNeeded: Boolean = false,
        onUpdateShizuku: () -> Unit = {},
        installedVersion: String? = null,
        bundledVersion: String? = null
) {
    Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题栏
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 根据是否需要更新而显示不同的图标
                    val titleIcon =
                            if (isShizukuInstalled &&
                                            isShizukuRunning &&
                                            hasShizukuPermission &&
                                            updateNeeded
                            ) {
                                Icons.Default.Update
                            } else {
                                Icons.Default.Edit
                            }

                    Icon(
                            imageVector = titleIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // 根据是否需要更新而显示不同的标题
                    val titleText =
                            if (isShizukuInstalled &&
                                            isShizukuRunning &&
                                            hasShizukuPermission &&
                                            updateNeeded
                            ) {
                                stringResource(R.string.shizuku_wizard_update_title)
                            } else {
                                stringResource(R.string.shizuku_wizard_title)
                            }

                    Text(
                            titleText,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                    )
                }

                TextButton(
                        onClick = { onToggleWizard(!showWizard) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                            if (showWizard) stringResource(R.string.wizard_collapse)
                            else stringResource(R.string.wizard_expand),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 进度和状态
            val isComplete = isShizukuInstalled && isShizukuRunning && hasShizukuPermission

            Surface(
                    color =
                            if (isComplete) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 进度指示器
                    LinearProgressIndicator(
                            progress =
                                    when {
                                        !isShizukuInstalled -> 0f
                                        !isShizukuRunning -> 0.33f
                                        !hasShizukuPermission -> 0.66f
                                        else -> 1f
                                    },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 当前状态
                    val statusText =
                            when {
                                !isShizukuInstalled -> stringResource(R.string.shizuku_wizard_step1)
                                !isShizukuRunning -> stringResource(R.string.shizuku_wizard_step2)
                                !hasShizukuPermission ->
                                        stringResource(R.string.shizuku_wizard_step3)
                                else -> stringResource(R.string.shizuku_wizard_completed)
                            }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isComplete) {
                            Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                                text = statusText,
                                style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                        ),
                                color =
                                        if (isComplete) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 详细设置内容，仅在展开时显示
            AnimatedVisibility(visible = showWizard) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    when {
                    // 第一步：安装Shizuku
                    !isShizukuInstalled -> {
                        Column {
                            Text(
                                    stringResource(R.string.shizuku_wizard_install_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ElevatedButton(
                                        onClick = onInstallBundled,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 12.dp),
                                        shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                            stringResource(R.string.shizuku_wizard_install_bundled),
                                            fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // 第二步：启动Shizuku服务
                    !isShizukuRunning -> {
                        Column {
                            Text(
                                    stringResource(R.string.shizuku_wizard_start_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Surface(
                                    color =
                                            MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.3f
                                            ),
                                    shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                            text =
                                                    stringResource(
                                                            R.string.shizuku_wizard_method1_title
                                                    ),
                                            style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Bold
                                                    ),
                                            color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                            text =
                                                    stringResource(
                                                            R.string.shizuku_wizard_method1_steps
                                                    ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                    color =
                                            MaterialTheme.colorScheme.secondaryContainer.copy(
                                                    alpha = 0.3f
                                            ),
                                    shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                            text =
                                                    stringResource(
                                                            R.string.shizuku_wizard_method2_title
                                                    ),
                                            style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Bold
                                                    ),
                                            color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                            text =
                                                    stringResource(
                                                            R.string.shizuku_wizard_method2_steps
                                                    ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                        onClick = onWatchTutorial,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 12.dp),
                                        shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                            stringResource(R.string.wizard_open_docs),
                                            fontSize = 14.sp
                                    )
                                }

                                FilledTonalButton(
                                        onClick = onOpenShizuku,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 12.dp),
                                        shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                            stringResource(R.string.shizuku_wizard_open_shizuku),
                                            fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // 第三步：授予权限
                    !hasShizukuPermission -> {
                        Column {
                            Text(
                                    stringResource(R.string.shizuku_wizard_permission_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                            text =
                                                    stringResource(
                                                            R.string
                                                                    .shizuku_wizard_permission_notice
                                                    ),
                                            style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                    ),
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                            text =
                                                    stringResource(
                                                            R.string
                                                                    .shizuku_wizard_permission_notice_details
                                                    ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                    onClick = onRequestPermission,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(
                                        stringResource(R.string.shizuku_wizard_grant_permission),
                                        fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // 全部完成
                    else -> {
                        Column {
                            Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                            stringResource(R.string.shizuku_wizard_success_message),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            // 添加版本更新部分
                            if (updateNeeded) {
                                Spacer(modifier = Modifier.height(16.dp))

                                Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                    imageVector = Icons.Default.Update,
                                                    contentDescription = null,
                                                    tint =
                                                            MaterialTheme.colorScheme
                                                                    .onSecondaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Text(
                                                    stringResource(
                                                            R.string.shizuku_wizard_update_available
                                                    ),
                                                    style =
                                                            MaterialTheme.typography.bodyMedium
                                                                    .copy(
                                                                            fontWeight =
                                                                                    FontWeight.Bold
                                                                    ),
                                                    color =
                                                            MaterialTheme.colorScheme
                                                                    .onSecondaryContainer
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                                stringResource(
                                                        R.string.shizuku_wizard_update_message
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color =
                                                        MaterialTheme.colorScheme
                                                                .onSecondaryContainer
                                        )

                                        // 显示版本信息
                                        if (installedVersion != null && bundledVersion != null) {
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Surface(
                                                    color =
                                                            MaterialTheme.colorScheme.background
                                                                    .copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                            text =
                                                                    stringResource(
                                                                            R.string
                                                                                    .shizuku_wizard_current_version,
                                                                            installedVersion
                                                                    ),
                                                            style =
                                                                    MaterialTheme.typography
                                                                            .bodySmall,
                                                            color =
                                                                    MaterialTheme.colorScheme
                                                                            .onSurfaceVariant
                                                    )

                                                    Spacer(modifier = Modifier.height(2.dp))

                                                    Text(
                                                            text =
                                                                    stringResource(
                                                                            R.string
                                                                                    .shizuku_wizard_latest_version,
                                                                            bundledVersion
                                                                    ),
                                                            style =
                                                                    MaterialTheme.typography
                                                                            .bodySmall,
                                                            color =
                                                                    MaterialTheme.colorScheme
                                                                            .primary
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        FilledTonalButton(
                                                onClick = onUpdateShizuku,
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(vertical = 12.dp),
                                                shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                        imageVector = Icons.Default.Update,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                )

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Text(
                                                        stringResource(
                                                                R.string.shizuku_wizard_update
                                                        ),
                                                        fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
