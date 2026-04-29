package com.ai.assistance.operit.ui.features.settings.screens

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import java.text.DecimalFormat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.ui.features.github.GitHubLoginWebViewDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

// 保存滑动状态变量，使其跨重组保持
private val SettingsScreenScrollPosition = mutableStateOf(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        onNavigateToUserPreferences: () -> Unit,
        navigateToGitHubAccount: () -> Unit,
        navigateToToolPermissions: () -> Unit,
        navigateToModelConfig: () -> Unit,
        navigateToThemeSettings: () -> Unit,
        navigateToGlobalDisplaySettings: () -> Unit,
        navigateToModelPrompts: () -> Unit,
        navigateToFunctionalConfig: () -> Unit,
        navigateToChatHistorySettings: () -> Unit,
        navigateToChatBackupSettings: () -> Unit,
        navigateToLanguageSettings: () -> Unit,
        navigateToSpeechServicesSettings: () -> Unit,
        navigateToExternalHttpChatSettings: () -> Unit,
        navigateToPersonaCardGeneration: () -> Unit,
        navigateToWaifuModeSettings: () -> Unit,
        navigateToTokenUsageStatistics: () -> Unit,
        navigateToContextSummarySettings: () -> Unit,
        navigateToLayoutAdjustmentSettings: () -> Unit
) {
        val context = LocalContext.current
        var permissionCheckTick by remember { mutableStateOf(0) }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
        ) { granted ->
                if (!granted) {
                        openNotificationSettings(context)
                }
                permissionCheckTick++
        }
        val userPreferences = remember { UserPreferencesManager.getInstance(context) }
        val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
        val scope = rememberCoroutineScope()
        var showGitHubLogin by remember { mutableStateOf(false) }

        val isGitHubLoggedIn = githubAuth.isLoggedInFlow.collectAsState(initial = false).value
        val gitHubUser = githubAuth.userInfoFlow.collectAsState(initial = null).value

        // 创建和记住滚动状态，设置为上次保存的位置
        val scrollState = rememberScrollState(SettingsScreenScrollPosition.value)

        // 当滚动状态改变时更新保存的位置
        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        SettingsScreenScrollPosition.value = position
                }
        }

        val hasBackgroundImage = userPreferences.useBackgroundImage.collectAsState(initial = false).value
        val hasUsagePermission = remember(permissionCheckTick) { hasUsageAccessPermission(context) }
        val hasNotificationPermission = remember(permissionCheckTick) { hasNotificationPermission(context) }
        val allGranted = hasUsagePermission && hasNotificationPermission
        
        val cardContainerColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.surface
        } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        }

        Column(
                modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(scrollState)
        ) {
                // ======= 账号 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_account),
                        icon = Icons.Default.AccountCircle,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(R.string.github_account),
                                subtitle = if (isGitHubLoggedIn && gitHubUser != null) {
                                        "@${gitHubUser!!.login}"
                                } else {
                                        stringResource(R.string.github_account_not_logged_in)
                                },
                                icon = Icons.Default.Person,
                                onClick = navigateToGitHubAccount
                        )

                        if (isGitHubLoggedIn) {
                                CompactSettingsItem(
                                        title = stringResource(R.string.logout),
                                        subtitle = stringResource(R.string.github_account_logout_desc),
                                        icon = Icons.Default.Logout,
                                        onClick = {
                                                scope.launch { githubAuth.logout() }
                                        }
                                )
                        } else {
                                CompactSettingsItem(
                                        title = stringResource(R.string.login_github),
                                        subtitle = stringResource(R.string.github_account_login_desc),
                                        icon = Icons.Default.Login,
                                        onClick = { showGitHubLogin = true }
                                )
                        }
                }

                if (showGitHubLogin) {
                        GitHubLoginWebViewDialog(
                                onDismissRequest = { showGitHubLogin = false }
                        )
                }

                // ======= 个性化配置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_personalization),
                        icon = Icons.Default.Person,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_user_preferences),
                                subtitle = stringResource(id = R.string.settings_user_preferences_subtitle),
                                icon = Icons.Default.Face,
                                onClick = onNavigateToUserPreferences
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(R.string.language_settings),
                                subtitle = stringResource(id = R.string.settings_language_subtitle),
                                icon = Icons.Default.Language,
                                onClick = navigateToLanguageSettings
                        )
                        
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
                        
                        CompactSettingsItem(
                                title = stringResource(R.string.layout_adjustment),
                                subtitle = stringResource(R.string.layout_adjustment_subtitle),
                                icon = Icons.Default.AspectRatio,
                                onClick = navigateToLayoutAdjustmentSettings
                        )
                }

                // ======= AI模型配置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_ai_model),
                        icon = Icons.Default.Settings,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_model_parameters),
                                subtitle = stringResource(id = R.string.settings_model_params_subtitle),
                                icon = Icons.Default.Api,
                                onClick = navigateToModelConfig
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_functional_model),
                                subtitle = stringResource(id = R.string.settings_functional_model_subtitle),
                                icon = Icons.Default.Tune,
                                onClick = navigateToFunctionalConfig
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
                        
                        // 新增：人设卡生成
                        CompactSettingsItem(
                                title = stringResource(R.string.persona_card_generation),
                                subtitle = stringResource(R.string.persona_card_generation_desc),
                                icon = Icons.Default.Face,
                                onClick = navigateToPersonaCardGeneration
                        )
                        
                        // 新增：Waifu模式设置
                        CompactSettingsItem(
                                title = stringResource(R.string.waifu_mode_settings),
                                subtitle = stringResource(R.string.waifu_mode_settings_desc),
                                icon = Icons.Default.EmojiEmotions,
                                onClick = navigateToWaifuModeSettings
                        )
                }

                // ======= 上下文和总结设置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_context_summary),
                        icon = Icons.Default.Analytics,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_section_context_summary),
                                subtitle = stringResource(id = R.string.settings_context_summary_subtitle),
                                icon = Icons.Default.Tune,
                                onClick = navigateToContextSummarySettings
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
                                title = stringResource(id = R.string.settings_data_backup),
                                subtitle = stringResource(id = R.string.settings_data_backup_desc),
                                icon = Icons.Default.CloudUpload,
                                onClick = navigateToChatBackupSettings
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_chat_history_management),
                                subtitle = stringResource(id = R.string.settings_chat_history_management_subtitle),
                                icon = Icons.Default.ManageHistory,
                                onClick = navigateToChatHistorySettings
                        )

                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_token_usage_stats),
                                subtitle = stringResource(id = R.string.settings_token_usage_subtitle),
                                icon = Icons.Default.Analytics,
                                onClick = navigateToTokenUsageStatistics
                        )
                }

                // ======= 外部调用 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_external_calls),
                        icon = Icons.Default.SettingsEthernet,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_external_http_chat),
                                subtitle = stringResource(id = R.string.settings_external_http_chat_subtitle),
                                icon = Icons.Default.SettingsEthernet,
                                onClick = navigateToExternalHttpChatSettings
                        )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                        onClick = {
                                requestUsageAndNotificationPermissions(
                                        context = context,
                                        notificationPermissionLauncher = notificationPermissionLauncher::launch
                                )
                                permissionCheckTick++
                        },
                        modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                                containerColor = if (allGranted) Color(0xFF4CAF50) else Color.White,
                                contentColor = if (allGranted) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                ) {
                        Text(
                                text = if (allGranted) {
                                        stringResource(R.string.settings_quick_permissions_granted)
                                } else {
                                        stringResource(R.string.settings_quick_permissions_button)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                        )
                }

                Text(
                        text = stringResource(R.string.settings_quick_permissions_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, start = 4.dp, end = 4.dp)
                )

                Text(
                        text = stringResource(
                                R.string.settings_quick_permissions_status_line,
                                if (hasNotificationPermission) {
                                        stringResource(R.string.settings_quick_permissions_status_granted)
                                } else {
                                        stringResource(R.string.settings_quick_permissions_status_denied)
                                },
                                if (hasUsagePermission) {
                                        stringResource(R.string.settings_quick_permissions_status_granted)
                                } else {
                                        stringResource(R.string.settings_quick_permissions_status_denied)
                                }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                )

                // 底部间距
                Spacer(modifier = Modifier.height(16.dp))
        }
}

private fun requestUsageAndNotificationPermissions(
        context: Context,
        notificationPermissionLauncher: (String) -> Unit
) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasNotificationPermission) {
                        notificationPermissionLauncher(Manifest.permission.POST_NOTIFICATIONS)
                }
        }

        if (!hasUsageAccessPermission(context)) {
                openUsageAccessSettings(context)
        }
}

private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
}

private fun hasUsageAccessPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                )
        } else {
                @Suppress("DEPRECATION")
                appOpsManager.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                )
        }
        return mode == AppOpsManager.MODE_ALLOWED
}

private fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
}

private fun openNotificationSettings(context: Context) {
        val intent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        else -> {
                                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                                putExtra("app_package", context.packageName)
                                putExtra("app_uid", context.applicationInfo.uid)
                        }
                }
        }
        runCatching { context.startActivity(intent) }
}

@Composable
private fun SettingsSection(
        title: String,
        icon: ImageVector,
        containerColor: Color,
        content: @Composable ColumnScope.() -> Unit
) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                // 分组标题
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                        )
                }
                
                // 内容区域
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                                containerColor = containerColor
                        )
                ) {
                        Column(
                                modifier = Modifier.padding(12.dp),
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
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onClick() }
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                }
                
                Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                )
        }
}

@Composable
private fun CompactToggleWithDescription(
        title: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                        )
                        Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                        )
                }
                Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.scale(0.8f)
                )
        }
}

@Composable
private fun CompactSlider(
        title: String,
        subtitle: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        decimalFormatPattern: String,
        unitText: String? = null,
        backgroundColor: Color
) {
        val focusManager = LocalFocusManager.current
        val df = remember(decimalFormatPattern) { DecimalFormat(decimalFormatPattern) }

        var sliderValue by remember(value) { mutableStateOf(value) }
        var textValue by remember(value) { mutableStateOf(df.format(value)) }

        Column(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(backgroundColor)
                        .padding(8.dp)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                )
                                Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(
                                        value = textValue,
                                        onValueChange = { newText ->
                                                textValue = newText
                                                newText.toFloatOrNull()?.let {
                                                        sliderValue = it.coerceIn(valueRange)
                                                }
                                        },
                                        modifier = Modifier
                                                .width(40.dp)
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                        textStyle = TextStyle(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                                onDone = {
                                                        val finalValue = textValue.toFloatOrNull()?.coerceIn(valueRange) ?: sliderValue
                                                        onValueChange(finalValue)
                                                        textValue = df.format(finalValue)
                                                        focusManager.clearFocus()
                                                }
                                        ),
                                        singleLine = true
                                )

                                if (unitText != null) {
                                        Text(
                                                text = unitText,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                ),
                                                modifier = Modifier.padding(start = 2.dp)
                                        )
                                }
                        }
                }
        }
}


