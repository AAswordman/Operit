package com.ai.assistance.operit.ui.features.management.settings.screens

import com.ai.assistance.operit.ui.theme.script.text.themeTextStroke
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.themeScriptBorder
import com.ai.assistance.operit.ui.theme.script.themeScriptTextStyle
import com.ai.assistance.operit.ui.theme.script.themeScriptDecoration
import com.ai.assistance.operit.ui.theme.script.themeScriptShape
import com.ai.assistance.operit.ui.theme.script.themeScriptFill
import com.ai.assistance.operit.ui.theme.script.themeScriptContentColor
import com.ai.assistance.operit.data.preferences.glass.GlassRegion

import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.glass.rememberGlassAppearance
import com.ai.assistance.operit.ui.theme.liquidGlass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.ai.assistance.operit.R
import androidx.compose.ui.graphics.Color

// 保存滑动状态变量，使其跨重组保持
private val SettingsScreenScrollPosition = mutableStateOf(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        navigateToToolPermissions: () -> Unit,
        navigateToModelConfig: () -> Unit,
        navigateToThemeSettings: () -> Unit,
        navigateToGlobalDisplaySettings: () -> Unit,
        navigateToModelPrompts: () -> Unit,
        navigateToMemory: () -> Unit,
        navigateToDataStorageManagement: () -> Unit,
        navigateToDataAndBackup: () -> Unit,

        navigateToSpeechServicesSettings: () -> Unit,
        navigateToPerformanceMonitor: () -> Unit,
        navigateToAbout: () -> Unit
) {
        val context = LocalContext.current

        // 创建和记住滚动状态，设置为上次保存的位置
        val scrollState = rememberScrollState(SettingsScreenScrollPosition.value)

        // 当滚动状态改变时更新保存的位置
        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        SettingsScreenScrollPosition.value = position
                }
        }

        val cardContainerColor = MaterialTheme.colorScheme.surfaceContainer

        Column(
                modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(scrollState)
        ) {
                // ======= 个性化配置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_personalization),
                        icon = Icons.Default.Person,
                        containerColor = cardContainerColor
                ) {


                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_theme_appearance),
                                subtitle = stringResource(id = R.string.settings_theme_subtitle),
                                icon = Icons.Default.Palette,
                                onClick = navigateToThemeSettings
                        )

                        CompactSettingsItem(
                                title = stringResource(R.string.settings_global_display),
                                subtitle = stringResource(R.string.settings_global_display_subtitle),
                                icon = Icons.Default.Visibility,
                                onClick = navigateToGlobalDisplaySettings
                        )

                }

                // ======= AI模型配置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_ai_model),
                        icon = Icons.Default.Settings,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.model_workspace_title),
                                subtitle = stringResource(id = R.string.model_workspace_subtitle),
                                icon = Icons.Default.Api,
                                onClick = navigateToModelConfig
                        )


                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_speech_services),
                                subtitle = stringResource(id = R.string.settings_speech_services_subtitle),
                                icon = Icons.Default.RecordVoiceOver,
                                onClick = navigateToSpeechServicesSettings
                        )

                }

                // ======= 提示词配置 =======
                SettingsSection(
                        title = stringResource(R.string.settings_prompt_section),
                        icon = Icons.Default.Message,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(R.string.settings_prompt_title),
                                subtitle = stringResource(id = R.string.settings_system_prompts_subtitle),
                                icon = Icons.Default.ChatBubble,
                                onClick = navigateToModelPrompts
                        )

                        CompactSettingsItem(
                                title = stringResource(R.string.memory_management_title),
                                subtitle = stringResource(R.string.memory_management_subtitle),
                                icon = Icons.Default.DeviceHub,
                                onClick = navigateToMemory
                        )
                }

                // ======= 数据和权限 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_data_permissions),
                        icon = Icons.Default.Security,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_tool_permissions),
                                subtitle = stringResource(id = R.string.settings_tool_permissions_subtitle),
                                icon = Icons.Default.AdminPanelSettings,
                                onClick = navigateToToolPermissions
                        )

                        CompactSettingsItem(
                                title = stringResource(id = R.string.data_backup_title),
                                subtitle = stringResource(id = R.string.data_backup_settings_desc),
                                icon = Icons.Default.CloudUpload,
                                onClick = navigateToDataAndBackup
                        )

                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_data_storage),
                                subtitle = stringResource(id = R.string.settings_data_storage_subtitle),
                                icon = Icons.Default.Storage,
                                onClick = navigateToDataStorageManagement
                        )





                        CompactSettingsItem(
                                title = stringResource(R.string.offline_resources_title),
                                subtitle = stringResource(R.string.offline_resources_subtitle),
                                icon = Icons.Default.Download,
                                onClick = {
                                        context.startActivity(android.content.Intent(context, com.ai.assistance.operit.ui.features.management.resources.OfflineResourcesActivity::class.java))
                                }
                        )


                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_performance_monitor),
                                subtitle = stringResource(id = R.string.settings_performance_monitor_subtitle),
                                icon = Icons.Default.Speed,
                                onClick = navigateToPerformanceMonitor
                        )

                        CompactSettingsItem(
                                title = stringResource(R.string.nav_about),
                                subtitle = stringResource(R.string.settings_about_subtitle),
                                icon = Icons.Default.Info,
                                onClick = navigateToAbout
                        )
                }

                // 底部间距
                Spacer(modifier = Modifier.height(16.dp))
        }
}
@Composable
private fun SettingsSection(
        title: String,
        icon: ImageVector,
        containerColor: Color,
        content: @Composable ColumnScope.() -> Unit
) {
        val glassEnabled = rememberGlassAppearance().settingsCards
        // 开启颜色定制后让分组标题与图标跟随主色，默认主题继续使用黑白正文色。
        val sectionTitleColor = if (LocalThemePreferenceSnapshot.current.useCustomColors) {
                MaterialTheme.colorScheme.primary
        } else {
                MaterialTheme.colorScheme.onSurface
        }
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                            .liquidGlass(glassEnabled, RoundedCornerShape(10.dp), containerColor,
                                region = GlassRegion.SETTINGS_CARDS,
                            )
                            .padding(if (glassEnabled) 8.dp else 0.dp)
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = sectionTitleColor,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = sectionTitleColor
                        )
                }

                // 内容区域
                Card(
                        shape = themeScriptShape("settingsCard", MaterialTheme.shapes.medium),
                        border = themeScriptBorder("settingsCard", null),
                        modifier = Modifier.fillMaxWidth()
                            .liquidGlass(glassEnabled, MaterialTheme.shapes.medium, containerColor,
                                region = GlassRegion.SETTINGS_CARDS,
                            ),
                        colors = CardDefaults.cardColors(
                                containerColor = themeScriptFill("settingsCard", if (glassEnabled) Color.Transparent else containerColor),
                                contentColor = MaterialTheme.colorScheme.onSurface
                        )
                ) {
                        Column(
                                modifier = Modifier.themeScriptDecoration("settingsCard", drawFill = false, drawStroke = false, originalShape = MaterialTheme.shapes.medium).padding(12.dp),
                                content = content
                        )
                }
        }
}

@Composable
private fun CompactSettingsItem(
        title: String,
        subtitle: String,
        icon: ImageVector,
        onClick: () -> Unit
) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .clip(themeScriptShape("listItem", RoundedCornerShape(6.dp)))
                        .clickable { onClick() }
                        .themeScriptDecoration("listItem", originalShape = RoundedCornerShape(6.dp))
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = themeScriptContentColor("listItem", MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = title,
                                style = themeScriptTextStyle("listItem", MaterialTheme.typography.bodyMedium),
                                fontWeight = FontWeight.Medium,
                                color = themeScriptContentColor("listItem", MaterialTheme.colorScheme.onSurface),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        , textStroke = themeTextStroke("listItem"))
                        Text(
                                text = subtitle,
                                style = themeScriptTextStyle("listItem", MaterialTheme.typography.bodySmall),
                                color = themeScriptContentColor("listItem", MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        , textStroke = themeTextStroke("listItem"))
                }

                Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                )
        }
}
