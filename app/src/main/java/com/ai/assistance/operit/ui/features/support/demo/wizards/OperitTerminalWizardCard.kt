package com.ai.assistance.operit.ui.features.support.demo.wizards

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

@Composable
fun OperitTerminalWizardCard(
    isPnpmInstalled: Boolean,
    isPipInstalled: Boolean,
    isEnvironmentReady: Boolean,
    showWizard: Boolean,
    onToggleWizard: (Boolean) -> Unit,
    onOpenTerminalScreen: () -> Unit,
    // 保留旧参数以保持兼容性
    isInstalled: Boolean = false,
    installedVersion: String? = null,
    latestVersion: String? = null,
    releaseNotes: String? = null,
    updateNeeded: Boolean = false,
    downloadUrl: String? = null,
    onInstall: () -> Unit = {},
    onUpdate: () -> Unit = {},
    onOpen: () -> Unit = {},
    onDownloadFromUrl: (String) -> Unit = {}
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.configure_terminal_environment),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                TextButton(
                    onClick = { onToggleWizard(!showWizard) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        stringResource(if (showWizard) R.string.wizard_collapse else R.string.wizard_expand),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            // 环境状态信息
            val statusText = when {
                isEnvironmentReady -> stringResource(R.string.nodejs_pip_environment_ready)
                isPnpmInstalled && !isPipInstalled -> stringResource(R.string.pnpm_installed_need_pip)
                !isPnpmInstalled && isPipInstalled -> stringResource(R.string.pip_installed_need_pnpm)
                else -> stringResource(R.string.need_configure_nodejs_pip)
            }
            
            val statusColor = when {
                isEnvironmentReady -> MaterialTheme.colorScheme.tertiary
                isPnpmInstalled || isPipInstalled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isEnvironmentReady) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor
                )
            }
            
            // 详细环境状态显示
            if (!isEnvironmentReady) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // pnpm状态
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isPnpmInstalled) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isPnpmInstalled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "pnpm",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPnpmInstalled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // pip状态
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isPipInstalled) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isPipInstalled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "pip",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPipInstalled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 详细设置内容，仅在展开时显示
            AnimatedVisibility(visible = showWizard) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isEnvironmentReady) {
                        Text(
                            stringResource(R.string.terminal_environment_setup_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = onOpenTerminalScreen,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.go_to_terminal_config), fontSize = 14.sp)
                        }
                    } else {
                        Text(
                            stringResource(R.string.nodejs_pip_environment_configured),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = onOpenTerminalScreen,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.open_terminal), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
} 