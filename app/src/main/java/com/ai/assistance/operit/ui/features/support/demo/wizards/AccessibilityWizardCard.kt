package com.ai.assistance.operit.ui.features.support.demo.wizards

import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

/**
 * 无障碍服务设置向导卡片
 *
 * @param isProviderInstalled 服务提供者App是否已安装
 * @param isServiceEnabled 无障碍服务是否已启用
 * @param showWizard 是否显示向导详情
 * @param onToggleWizard 切换向导显示状态的回调
 * @param onInstallProvider 安装服务提供者的回调
 * @param onOpenAccessibilitySettings 打开无障碍设置页面的回调
 * @param updateNeeded 是否需要更新
 * @param installedVersion 已安装的版本
 * @param bundledVersion 内置的版本
 * @param onUpdateProvider 更新服务提供者的回调
 */
@Composable
fun AccessibilityWizardCard(
    isProviderInstalled: Boolean,
    isServiceEnabled: Boolean,
    showWizard: Boolean,
    onToggleWizard: () -> Unit,
    onInstallProvider: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    updateNeeded: Boolean,
    installedVersion: String?,
    bundledVersion: String,
    onUpdateProvider: () -> Unit
) {
    var showWarningDialog by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
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
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.accessibility_wizard_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                TextButton(
                    onClick = onToggleWizard,
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
            val isComplete = isProviderInstalled && isServiceEnabled
            val progress = when {
                !isProviderInstalled -> 0f
                !isServiceEnabled -> 0.5f
                else -> 1f
            }

            Surface(
                color = if (isComplete) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 进度指示器
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 当前状态
                    val statusText = when {
                        !isProviderInstalled -> stringResource(R.string.accessibility_wizard_step1)
                        !isServiceEnabled -> stringResource(R.string.accessibility_wizard_step2)
                        else -> stringResource(R.string.accessibility_wizard_completed)
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
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (isComplete) MaterialTheme.colorScheme.primary
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
                    // 第一步：安装服务提供者
                    !isProviderInstalled -> {
                        Column {
                            Text(
                                stringResource(R.string.accessibility_wizard_install_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { showWarningDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(
                                    stringResource(R.string.accessibility_wizard_install_provider),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // 第二步：启用服务
                    !isServiceEnabled -> {
                        Column {
                            Text(
                                stringResource(R.string.accessibility_wizard_enable_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.accessibility_wizard_enable_note),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.accessibility_wizard_enable_note_details),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = onOpenAccessibilitySettings,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(
                                    stringResource(R.string.accessibility_wizard_open_settings),
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
                                        stringResource(R.string.accessibility_wizard_success_message),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            // 如果需要更新，显示更新提示
                            if (updateNeeded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                UpdateAvailableInfo(
                                    installedVersion = installedVersion,
                                    bundledVersion = bundledVersion,
                                    onUpdate = onUpdateProvider
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 警告对话框
    if (showWarningDialog) {
        val warningTitle = stringResource(R.string.accessibility_risk_warning_title)
        val warningMessage = stringResource(R.string.accessibility_risk_warning_message) + "\n\n" +
                stringResource(R.string.accessibility_risk_warning_additional)
        val warningInputError = stringResource(R.string.accessibility_risk_warning_input_error)
        val warningConfirm = stringResource(R.string.accessibility_risk_warning_confirm)
        val expectedText = stringResource(R.string.a11y_wizard_risk_acknowledgment)
        val expectedTextEn = "I understand and acknowledge the risks of account bans and restricted account functions caused by accessibility permissions, and I bear the consequences myself"

        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = warningTitle,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = warningMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { 
                            confirmText = it
                            isError = false
                        },
                        label = { Text(stringResource(R.string.accessibility_risk_warning_input)) },
                        isError = isError,
                        supportingText = if (isError) {
                            { Text(warningInputError) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (confirmText.trim().equals(expectedText, ignoreCase = true) ||
                            confirmText.trim().equals(expectedTextEn, ignoreCase = true)) {
                            showWarningDialog = false
                            onInstallProvider()
                        } else {
                            isError = true
                        }
                    }
                ) {
                    Text(warningConfirm)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showWarningDialog = false
                        confirmText = ""
                        isError = false
                    }
                ) {
                    Text(stringResource(R.string.accessibility_risk_warning_cancel))
                }
            }
        )
    }
}
}

/**
 * 显示有可用更新的信息组件
 */
@Composable
private fun UpdateAvailableInfo(
    installedVersion: String?,
    bundledVersion: String,
    onUpdate: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.a11y_wizard_new_version_detected),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.a11y_wizard_installed_version,
                    installedVersion ?: "N/A",
                    bundledVersion
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.a11y_update_now))
            }
        }
    }
} 