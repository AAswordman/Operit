package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.AndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.RootCommandExecutionMode
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.services.floating.StatusIndicatorStyle
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GlobalDisplaySettingsScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val userPreferences = remember { UserPreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val toolCollapseMode by displayPreferencesManager.toolCollapseMode.collectAsState(initial = ToolCollapseMode.ALL)
    val showFpsCounter by displayPreferencesManager.showFpsCounter.collectAsState(initial = false)
    val enableReplyNotification by displayPreferencesManager.enableReplyNotification.collectAsState(initial = true)
    val enableReplyNotificationSound by displayPreferencesManager.enableReplyNotificationSound.collectAsState(initial = false)
    val enableReplyNotificationVibration by displayPreferencesManager.enableReplyNotificationVibration.collectAsState(initial = false)
    val enableEnterToSend by displayPreferencesManager.enableEnterToSend.collectAsState(initial = false)
    val enableNavigationAnimation by displayPreferencesManager.enableNavigationAnimation.collectAsState(initial = true)
    val startWithNewChat by displayPreferencesManager.startWithNewChat.collectAsState(initial = false)
    val enableBackgroundKeepAlive by displayPreferencesManager.enableBackgroundKeepAlive.collectAsState(initial = false)
    val enableExperimentalVirtualDisplay by displayPreferencesManager.enableExperimentalVirtualDisplay.collectAsState(initial = true)
    val hideRuntimeTaskView by displayPreferencesManager.hideRuntimeTaskView.collectAsState(initial = false)
    val globalUserName by displayPreferencesManager.globalUserName.collectAsState(initial = null)
    val screenshotFormat by displayPreferencesManager.screenshotFormat.collectAsState(initial = "JPG")
    val screenshotQuality by displayPreferencesManager.screenshotQuality.collectAsState(initial = 75)
    val screenshotScalePercent by displayPreferencesManager.screenshotScalePercent.collectAsState(initial = 75)
    val visitWebWaitSeconds by displayPreferencesManager.visitWebWaitSeconds.collectAsState(initial = 0)
    val toolPkgHookTimeoutSeconds by displayPreferencesManager.toolPkgHookTimeoutSeconds.collectAsState(initial = 10)
    val virtualDisplayBitrateKbps by displayPreferencesManager.virtualDisplayBitrateKbps.collectAsState(initial = 3000)
    val keepScreenOn by apiPreferences.keepScreenOnFlow.collectAsState(initial = true)
    val convertLongPastedTextToFile by userPreferences.convertLongPastedTextToFile.collectAsState(initial = true)
    val longPastedTextFileThreshold by userPreferences.longPastedTextFileThreshold.collectAsState(
        initial = UserPreferencesManager.DEFAULT_LONG_PASTED_TEXT_FILE_THRESHOLD
    )

    val hasBackgroundImage = LocalThemePreferenceSnapshot.current.useBackgroundImage
    val uiAccessibilityMode by userPreferences.uiAccessibilityMode.collectAsState(initial = false)
    val softwareIdentity by userPreferences.softwareIdentity.collectAsState(
        initial = UserPreferencesManager.SOFTWARE_IDENTITY_OPERIT
    )
    val preferredPermissionLevel by androidPermissionPreferences.preferredPermissionLevelFlow.collectAsState(initial = null)
    val rootExecutionMode by androidPermissionPreferences.rootExecutionModeFlow.collectAsState(initial = RootCommandExecutionMode.AUTO)
    val customSuCommand by androidPermissionPreferences.customSuCommandFlow.collectAsState(initial = AndroidPermissionPreferences.DEFAULT_SU_COMMAND)

    var userNameInput by remember { mutableStateOf(globalUserName ?: "") }
    var customSuCommandInput by remember { mutableStateOf(customSuCommand) }
    val collapseModeOptions = remember {
        listOf(ToolCollapseMode.READ_ONLY, ToolCollapseMode.ALL, ToolCollapseMode.FULL)
    }
    var collapseModeSliderValue by remember(toolCollapseMode) {
        mutableFloatStateOf(collapseModeOptions.indexOf(toolCollapseMode).coerceAtLeast(0).toFloat())
    }
    var visitWebWaitSliderValue by remember(visitWebWaitSeconds) {
        mutableFloatStateOf(visitWebWaitSeconds.toFloat())
    }
    var toolPkgHookTimeoutSliderValue by remember(toolPkgHookTimeoutSeconds) {
        mutableFloatStateOf(toolPkgHookTimeoutSeconds.toFloat())
    }
    var qualitySliderValue by remember(screenshotQuality) {
        mutableFloatStateOf(screenshotQuality.toFloat())
    }
    var scaleSliderValue by remember(screenshotScalePercent) {
        mutableFloatStateOf(screenshotScalePercent.toFloat())
    }
    val collapseModeLabelRes: (ToolCollapseMode) -> Int = { mode ->
        when (mode) {
            ToolCollapseMode.READ_ONLY -> R.string.tool_collapse_mode_read_only
            ToolCollapseMode.ALL -> R.string.tool_collapse_mode_all
            ToolCollapseMode.FULL -> R.string.tool_collapse_mode_full
        }
    }

    // 自动化状态指示样式（使用与 FloatingChatService 相同的 SharedPreferences）
    val statusIndicatorPrefs = remember {
        context.getSharedPreferences("floating_chat_prefs", android.content.Context.MODE_PRIVATE)
    }
    var statusIndicatorStyle by remember {
        mutableStateOf(
            run {
                val defaultName = StatusIndicatorStyle.FULLSCREEN_RAINBOW.name
                val stored = statusIndicatorPrefs.getString("status_indicator_style", defaultName)
                try {
                    StatusIndicatorStyle.valueOf(stored ?: defaultName)
                } catch (_: IllegalArgumentException) {
                    StatusIndicatorStyle.FULLSCREEN_RAINBOW
                }
            }
        )
    }

    LaunchedEffect(globalUserName) {
        userNameInput = globalUserName ?: ""
    }

    LaunchedEffect(customSuCommand) {
        customSuCommandInput = customSuCommand
    }

    LaunchedEffect(
        collapseModeSliderValue,
        visitWebWaitSliderValue,
        toolPkgHookTimeoutSliderValue,
        qualitySliderValue,
        scaleSliderValue
    ) {
        val localCollapseMode =
            collapseModeOptions[collapseModeSliderValue.roundToInt().coerceIn(0, collapseModeOptions.lastIndex)]
        val localVisitWebWaitSeconds = visitWebWaitSliderValue.roundToInt().coerceIn(0, 10)
        val localToolPkgHookTimeoutSeconds = toolPkgHookTimeoutSliderValue.roundToInt().coerceIn(1, 60)
        val localScreenshotQuality = qualitySliderValue.roundToInt().coerceIn(50, 100)
        val localScreenshotScalePercent = scaleSliderValue.roundToInt().coerceIn(50, 100)

        val hasPendingSliderChanges =
            localCollapseMode != toolCollapseMode ||
                localVisitWebWaitSeconds != visitWebWaitSeconds ||
                localToolPkgHookTimeoutSeconds != toolPkgHookTimeoutSeconds ||
                localScreenshotQuality != screenshotQuality ||
                localScreenshotScalePercent != screenshotScalePercent

        if (!hasPendingSliderChanges) return@LaunchedEffect

        kotlinx.coroutines.delay(300)

        displayPreferencesManager.saveDisplaySettings(
            toolCollapseMode = if (localCollapseMode != toolCollapseMode) localCollapseMode else null,
            visitWebWaitSeconds = if (localVisitWebWaitSeconds != visitWebWaitSeconds) localVisitWebWaitSeconds else null,
            toolPkgHookTimeoutSeconds = if (localToolPkgHookTimeoutSeconds != toolPkgHookTimeoutSeconds) localToolPkgHookTimeoutSeconds else null,
            screenshotQuality = if (localScreenshotQuality != screenshotQuality) localScreenshotQuality else null,
            screenshotScalePercent = if (localScreenshotScalePercent != screenshotScalePercent) localScreenshotScalePercent else null
        )
    }

    val componentBackgroundColor = if (hasBackgroundImage) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    }
    val selectedCollapseMode =
        collapseModeOptions[collapseModeSliderValue.roundToInt().coerceIn(0, collapseModeOptions.lastIndex)]

    CustomScaffold() { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(scrollState)
        ) {
            SectionTitle(
                text = stringResource(R.string.message_display_settings),
                icon = Icons.Default.Message
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(componentBackgroundColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.tool_collapse_mode_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(id = R.string.tool_collapse_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = collapseModeLabelRes(selectedCollapseMode)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Slider(
                    value = collapseModeSliderValue,
                    onValueChange = { collapseModeSliderValue = it },
                    valueRange = 0f..(collapseModeOptions.size - 1).toFloat(),
                    steps = collapseModeOptions.size - 2,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    collapseModeOptions.forEachIndexed { index, mode ->
                        val selected = selectedCollapseMode == mode
                        Text(
                            text = stringResource(id = collapseModeLabelRes(mode)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    collapseModeSliderValue = index.toFloat()
                                }
                                .padding(top = 2.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = userNameInput,
                onValueChange = { userNameInput = it },
                label = { Text(stringResource(R.string.global_user_name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true,
                trailingIcon = {
                    if (userNameInput != globalUserName) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    displayPreferencesManager.saveDisplaySettings(
                                        globalUserName = userNameInput,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = stringResource(R.string.save),
                            )
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ======= 系统显示设置 =======
            SectionTitle(
                text = stringResource(R.string.system_display_settings),
                icon = Icons.Default.Settings
            )

            DisplayToggleItem(
                title = stringResource(R.string.show_fps_counter),
                subtitle = stringResource(R.string.show_fps_counter_description),
                checked = showFpsCounter,
                onCheckedChange = {
                    scope.launch {
                        displayPreferencesManager.saveDisplaySettings(showFpsCounter = it)
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            DisplayToggleItem(
                title = stringResource(R.string.enable_reply_notification),
                subtitle = stringResource(R.string.enable_reply_notification_description),
                checked = enableReplyNotification,
                onCheckedChange = {
                    scope.launch {
                        displayPreferencesManager.saveDisplaySettings(enableReplyNotification = it)
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            DisplayToggleItem(
                title = stringResource(R.string.enable_reply_notification_sound),
                subtitle = stringResource(R.string.enable_reply_notification_sound_description),
                checked = enableReplyNotificationSound,
                onCheckedChange = {
                    scope.launch {
                        displayPreferencesManager.saveDisplaySettings(enableReplyNotificationSound = it)
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            DisplayToggleItem(
                title = stringResource(R.string.enable_reply_notification_vibration),
                subtitle = stringResource(R.string.enable_reply_notification_vibration_description),
                checked = enableReplyNotificationVibration,
                onCheckedChange = {
                    scope.launch {
                        displayPreferencesManager.saveDisplaySettings(enableReplyNotificationVibration = it)
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            DisplayToggleItem(
                title = stringResource(R.string.enable_enter_to_send),
                subtitle = stringResource(R.string.enable_enter_to_send_description),
                checked = enableEnterToSend,
                onCheckedChange = {
                    scope.launch {
                        displayPreferencesManager.saveDisplaySettings(enableEnterToSend = it)
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            DisplayToggleItem(
                title = stringResource(R.string.long_pasted_text_to_file),
                subtitle = stringResource(R.string.long_pasted_text_to_file_desc),
                checked = convertLongPastedTextToFile,
                onCheckedChange = {
                    scope.launch {
                        userPreferences.saveConvertLongPastedTextToFile(it)
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            if (convertLongPastedTextToFile) {
                EditableNumberSetting(
                    title = stringResource(R.string.long_pasted_text_threshold),
                    subtitle = stringResource(R.string.long_pasted_text_threshold_desc),
                    value = longPastedTextFileThreshold.toFloat(),
                    onValueChange = { threshold ->
                        scope.launch {
                            userPreferences.saveLongPastedTextFileThreshold(threshold.toInt())
                        }
                    },
                    valueRange = 1_000f..100_000f,
                    unitText = stringResource(R.string.long_pasted_text_threshold_unit),
                    backgroundColor = componentBackgroundColor
                )
            }

            DisplayToggleItem(
                title = stringResource(R.string.keep_screen_on),
                subtitle = stringResource(R.string.keep_screen_on_description),
                checked = keepScreenOn,
                onCheckedChange = {
                    scope.launch {
                        apiPreferences.saveKeepScreenOn(it)
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            DisplayToggleItem(
                title = stringResource(R.string.enable_navigation_animation),
                subtitle = stringResource(R.string.enable_navigation_animation_description),
                checked = enableNavigationAnimation,
                onCheckedChange = {
                    scope.launch {
                        displayPreferencesManager.saveDisplaySettings(
                            enableNavigationAnimation = it
                        )
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            DisplayToggleItem(
                title = stringResource(R.string.start_with_new_chat),
                subtitle = stringResource(R.string.start_with_new_chat_description),
                checked = startWithNewChat,
                onCheckedChange = {
                    scope.launch {
                        displayPreferencesManager.saveDisplaySettings(startWithNewChat = it)
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(componentBackgroundColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.visit_web_wait_time_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.visit_web_wait_time_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = visitWebWaitSliderValue,
                        onValueChange = { visitWebWaitSliderValue = it.roundToInt().toFloat() },
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.visit_web_wait_time_value, visitWebWaitSliderValue.roundToInt()),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(componentBackgroundColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.toolpkg_hook_timeout_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.toolpkg_hook_timeout_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = toolPkgHookTimeoutSliderValue,
                        onValueChange = { toolPkgHookTimeoutSliderValue = it.roundToInt().toFloat() },
                        valueRange = 1f..60f,
                        steps = 58,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.toolpkg_hook_timeout_value,
                            toolPkgHookTimeoutSliderValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(componentBackgroundColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.software_identity_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(id = R.string.software_identity_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = softwareIdentity == UserPreferencesManager.SOFTWARE_IDENTITY_OPERIT,
                        onClick = {
                            if (softwareIdentity != UserPreferencesManager.SOFTWARE_IDENTITY_OPERIT) {
                                scope.launch {
                                    userPreferences.saveSoftwareIdentity(UserPreferencesManager.SOFTWARE_IDENTITY_OPERIT)
                                }
                            }
                        },
                        label = { Text(stringResource(id = R.string.software_identity_option_operit)) }
                    )
                    FilterChip(
                        selected = softwareIdentity == UserPreferencesManager.SOFTWARE_IDENTITY_LINGSHU,
                        onClick = {
                            if (softwareIdentity != UserPreferencesManager.SOFTWARE_IDENTITY_LINGSHU) {
                                scope.launch {
                                    userPreferences.saveSoftwareIdentity(UserPreferencesManager.SOFTWARE_IDENTITY_LINGSHU)
                                }
                            }
                        },
                        label = { Text(stringResource(id = R.string.software_identity_option_lingshu)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ======= 自动化状态指示样式 =======
            SectionTitle(
                text = stringResource(id = R.string.global_display_automation_behavior),
                icon = Icons.Default.AutoAwesome
            )

            DisplayToggleItem(
                title = stringResource(R.string.ui_accessibility_mode),
                subtitle = stringResource(R.string.ui_accessibility_mode_description),
                checked = uiAccessibilityMode,
                onCheckedChange = {
                    scope.launch {
                        userPreferences.saveUiAccessibilityMode(it)
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            DisplayToggleItem(
                title = stringResource(R.string.enable_background_keep_alive),
                subtitle = stringResource(R.string.enable_background_keep_alive_description),
                checked = enableBackgroundKeepAlive,
                onCheckedChange = {
                    scope.launch {
                        displayPreferencesManager.saveDisplaySettings(
                            enableBackgroundKeepAlive = it
                        )
                        AIForegroundService.refreshBackgroundKeepAlive(context)
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            DisplayToggleItem(
                title = stringResource(R.string.experimental_virtual_display),
                subtitle = stringResource(R.string.experimental_virtual_display_description),
                checked = enableExperimentalVirtualDisplay,
                onCheckedChange = {
                    scope.launch {
                        displayPreferencesManager.saveDisplaySettings(
                            enableExperimentalVirtualDisplay = it
                        )
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            DisplayToggleItem(
                title = stringResource(R.string.hide_runtime_task_view),
                subtitle = stringResource(R.string.hide_runtime_task_view_description),
                checked = hideRuntimeTaskView,
                onCheckedChange = {
                    scope.launch {
                        displayPreferencesManager.saveDisplaySettings(
                            hideRuntimeTaskView = it
                        )
                    }
                },
                backgroundColor = componentBackgroundColor
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(componentBackgroundColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.global_display_virtual_screen_bitrate),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = virtualDisplayBitrateKbps == 1500,
                        onClick = {
                            scope.launch {
                                displayPreferencesManager.saveDisplaySettings(virtualDisplayBitrateKbps = 1500)
                            }
                        },
                        label = { Text("1.5 Mbps") }
                    )
                    FilterChip(
                        selected = virtualDisplayBitrateKbps == 3000,
                        onClick = {
                            scope.launch {
                                displayPreferencesManager.saveDisplaySettings(virtualDisplayBitrateKbps = 3000)
                            }
                        },
                        label = { Text("3 Mbps") }
                    )
                    FilterChip(
                        selected = virtualDisplayBitrateKbps == 5000,
                        onClick = {
                            scope.launch {
                                displayPreferencesManager.saveDisplaySettings(virtualDisplayBitrateKbps = 5000)
                            }
                        },
                        label = { Text("5 Mbps") }
                    )
                    FilterChip(
                        selected = virtualDisplayBitrateKbps == 10000,
                        onClick = {
                            scope.launch {
                                displayPreferencesManager.saveDisplaySettings(virtualDisplayBitrateKbps = 10000)
                            }
                        },
                        label = { Text("10 Mbps") }
                    )
                    FilterChip(
                        selected = virtualDisplayBitrateKbps == 20000,
                        onClick = {
                            scope.launch {
                                displayPreferencesManager.saveDisplaySettings(virtualDisplayBitrateKbps = 20000)
                            }
                        },
                        label = { Text("20 Mbps") }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(componentBackgroundColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.global_display_status_indicator_style),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = statusIndicatorStyle == StatusIndicatorStyle.FULLSCREEN_RAINBOW,
                        onClick = {
                            statusIndicatorStyle = StatusIndicatorStyle.FULLSCREEN_RAINBOW
                            statusIndicatorPrefs.edit()
                                .putString(
                                    "status_indicator_style",
                                    StatusIndicatorStyle.FULLSCREEN_RAINBOW.name
                                )
                                .apply()
                        },
                        label = { Text(stringResource(R.string.display_rainbow_border)) }
                    )
                    FilterChip(
                        selected = statusIndicatorStyle == StatusIndicatorStyle.TOP_BAR,
                        onClick = {
                            statusIndicatorStyle = StatusIndicatorStyle.TOP_BAR
                            statusIndicatorPrefs.edit()
                                .putString(
                                    "status_indicator_style",
                                    StatusIndicatorStyle.TOP_BAR.name
                                )
                                .apply()
                        },
                        label = { Text(stringResource(R.string.display_top_hint)) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(componentBackgroundColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.global_display_screenshot_settings),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(id = R.string.global_display_image_format),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = screenshotFormat.equals("PNG", ignoreCase = true),
                        onClick = {
                            scope.launch {
                                displayPreferencesManager.saveDisplaySettings(screenshotFormat = "PNG")
                            }
                        },
                        label = { Text(stringResource(R.string.display_png_default)) }
                    )
                    FilterChip(
                        selected = screenshotFormat.equals("JPG", ignoreCase = true) ||
                                screenshotFormat.equals("JPEG", ignoreCase = true),
                        onClick = {
                            scope.launch {
                                displayPreferencesManager.saveDisplaySettings(screenshotFormat = "JPG")
                            }
                        },
                        label = { Text(stringResource(R.string.display_jpg_smaller)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(id = R.string.global_display_image_quality),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = qualitySliderValue,
                        onValueChange = { qualitySliderValue = it },
                        valueRange = 50f..100f,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${qualitySliderValue.roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(id = R.string.global_display_resolution_scale),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = scaleSliderValue,
                        onValueChange = { scaleSliderValue = it },
                        valueRange = 50f..100f,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${scaleSliderValue.roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (preferredPermissionLevel == AndroidPermissionLevel.ROOT) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(componentBackgroundColor)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.root_execution_mode_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(id = R.string.root_execution_mode_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = rootExecutionMode == RootCommandExecutionMode.AUTO,
                            onClick = {
                                scope.launch {
                                    androidPermissionPreferences.saveRootExecutionMode(RootCommandExecutionMode.AUTO)
                                }
                            },
                            label = { Text(stringResource(R.string.root_execution_mode_auto)) }
                        )
                        FilterChip(
                            selected = rootExecutionMode == RootCommandExecutionMode.FORCE_LIBSU,
                            onClick = {
                                scope.launch {
                                    androidPermissionPreferences.saveRootExecutionMode(RootCommandExecutionMode.FORCE_LIBSU)
                                }
                            },
                            label = { Text(stringResource(R.string.root_execution_mode_force_libsu)) }
                        )
                        FilterChip(
                            selected = rootExecutionMode == RootCommandExecutionMode.FORCE_EXEC,
                            onClick = {
                                scope.launch {
                                    androidPermissionPreferences.saveRootExecutionMode(RootCommandExecutionMode.FORCE_EXEC)
                                }
                            },
                            label = { Text(stringResource(R.string.root_execution_mode_force_exec)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customSuCommandInput,
                        onValueChange = { customSuCommandInput = it },
                        label = { Text(stringResource(id = R.string.root_custom_su_command)) },
                        supportingText = {
                            Text(stringResource(id = R.string.root_custom_su_command_description))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (customSuCommandInput.trim() != customSuCommand.trim()) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            androidPermissionPreferences.saveCustomSuCommand(customSuCommandInput)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = stringResource(id = R.string.save)
                                    )
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ======= 重置按钮 =======
            Button(
                onClick = {
                    scope.launch {
                        displayPreferencesManager.resetDisplaySettings()
                        androidPermissionPreferences.resetRootExecutionSettings()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.reset_all_display_settings),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // 底部间距
            Spacer(modifier = Modifier.height(16.dp))
        }
        }

    }
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DisplayToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backgroundColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun EditableNumberSetting(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    unitText: String,
    backgroundColor: Color
) {
    val focusManager = LocalFocusManager.current
    val decimalFormat = remember { DecimalFormat("0") }
    var textValue by remember(value) { mutableStateOf(decimalFormat.format(value)) }

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
                    onValueChange = { newText -> textValue = newText },
                    modifier = Modifier
                        .width(64.dp)
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
                            val enteredValue = textValue.toFloatOrNull()
                            if (enteredValue != null) {
                                val constrainedValue = enteredValue.coerceIn(valueRange)
                                onValueChange(constrainedValue)
                                textValue = decimalFormat.format(constrainedValue)
                            }
                            focusManager.clearFocus()
                        }
                    ),
                    singleLine = true
                )
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
