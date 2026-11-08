package com.ai.assistance.operit.ui.features.management.settings.display

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun displayChoiceLabel(values: DisplayBehaviorValues, key: DisplayChoice): String =
    stringResource(
        when (key) {
            DisplayChoice.COLLAPSE ->
                when (values[key]) {
                    "read_only" -> R.string.display_behavior_collapse_read_value
                    "all" -> R.string.display_behavior_collapse_all_value
                    "full" -> R.string.display_behavior_collapse_full_value
                    else -> error("无效的工具折叠模式")
                }
            DisplayChoice.ORIENTATION ->
                when (values[key]) {
                    "ALWAYS" -> R.string.display_behavior_orientation_always
                    "ASK" -> R.string.display_behavior_orientation_ask
                    "NEVER" -> R.string.display_behavior_orientation_never
                    else -> error("无效的方向处理模式")
                }
            DisplayChoice.INDICATOR ->
                when (values[key]) {
                    "FULLSCREEN_RAINBOW" -> R.string.display_behavior_indicator_rainbow
                    "TOP_BAR" -> R.string.display_behavior_indicator_top
                    else -> error("无效的状态提示模式")
                }
            DisplayChoice.ROOT ->
                when (values[key]) {
                    "AUTO" -> R.string.display_behavior_root_auto
                    "FORCE_LIBSU" -> R.string.display_behavior_root_magisk_value
                    "FORCE_EXEC" -> R.string.display_behavior_root_kernel_value
                    else -> error("无效的 Root 执行模式")
                }
            else -> error("该选项直接显示原值")
        }
    )

@Composable
internal fun DisplayToggleSetting(
    controller: DisplayBehaviorController,
    key: DisplayToggle,
    title: Int,
    description: Int,
    enabled: Boolean = true,
) {
    DisplayToggleRow(
        stringResource(title),
        stringResource(description),
        controller.values[key],
        enabled,
    ) {
        controller.set(key, it)
    }
}

@Composable
internal fun DisplayChatTab(controller: DisplayBehaviorController, open: (DisplayEditor) -> Unit) {
    val values = controller.values
    DisplayCard(stringResource(R.string.display_behavior_input_start), Icons.Default.Keyboard) {
        DisplayToggleSetting(
            controller,
            DisplayToggle.ENTER,
            R.string.display_behavior_enter,
            R.string.display_behavior_enter_desc,
        )
        DisplayDivider()
        DisplayToggleSetting(
            controller,
            DisplayToggle.NEW_CHAT,
            R.string.display_behavior_new_chat,
            R.string.display_behavior_new_chat_desc,
        )
    }
    DisplayCard(
        stringResource(R.string.display_behavior_message_organization),
        Icons.Default.Layers,
    ) {
        DisplayNavigationRow(
            stringResource(R.string.display_behavior_collapse_title),
            stringResource(R.string.display_behavior_collapse_desc),
            displayChoiceLabel(values, DisplayChoice.COLLAPSE),
        ) {
            open(DisplayEditor.COLLAPSE)
        }
        DisplayDivider()
        DisplayToggleSetting(
            controller,
            DisplayToggle.LONG_TEXT,
            R.string.display_behavior_long_text,
            R.string.display_behavior_long_text_desc,
        )
        if (values[DisplayToggle.LONG_TEXT]) {
            DisplayDivider()
            DisplayNavigationRow(
                stringResource(R.string.display_behavior_threshold_row),
                null,
                stringResource(
                    R.string.display_behavior_characters_value,
                    NumberFormat.getIntegerInstance(Locale.getDefault())
                        .format(values[DisplayNumber.TEXT_THRESHOLD]),
                ),
                child = true,
                leading = Icons.Default.SubdirectoryArrowRight,
            ) {
                open(DisplayEditor.THRESHOLD)
            }
        }
    }
    DisplayCard(
        stringResource(R.string.display_behavior_reply_reminders),
        Icons.Default.NotificationsNone,
    ) {
        DisplayToggleSetting(
            controller,
            DisplayToggle.NOTIFY,
            R.string.display_behavior_notify,
            R.string.display_behavior_notify_desc,
        )
        DisplayDivider()
        DisplayToggleSetting(
            controller,
            DisplayToggle.SOUND,
            R.string.display_behavior_sound,
            R.string.display_behavior_sound_desc,
            values[DisplayToggle.NOTIFY],
        )
        DisplayDivider()
        DisplayToggleSetting(
            controller,
            DisplayToggle.VIBRATION,
            R.string.display_behavior_vibration,
            R.string.display_behavior_vibration_desc,
            values[DisplayToggle.NOTIFY],
        )
        if (!values[DisplayToggle.NOTIFY])
            DisplayHint(
                stringResource(R.string.display_behavior_notifications_retained),
                Modifier.padding(start = 17.dp, end = 17.dp, bottom = 14.dp),
            )
    }
    DisplayHint(
        stringResource(R.string.display_behavior_chat_footer),
        Modifier.padding(start = 3.dp, end = 3.dp, bottom = 15.dp),
        icon = true,
    )
}

@Composable
internal fun DisplayInterfaceTab(
    controller: DisplayBehaviorController,
    open: (DisplayEditor) -> Unit,
) {
    DisplayCard(
        stringResource(R.string.display_behavior_screen_navigation),
        Icons.Default.Computer,
    ) {
        DisplayToggleSetting(
            controller,
            DisplayToggle.KEEP_SCREEN,
            R.string.display_behavior_keep_screen,
            R.string.display_behavior_keep_screen_desc,
        )
        DisplayDivider()
        DisplayToggleSetting(
            controller,
            DisplayToggle.ANIMATION,
            R.string.display_behavior_animation,
            R.string.display_behavior_animation_desc,
        )
        DisplayDivider()
        DisplayNavigationRow(
            stringResource(R.string.display_behavior_orientation_title),
            stringResource(R.string.display_behavior_orientation_desc),
            displayChoiceLabel(controller.values, DisplayChoice.ORIENTATION),
        ) {
            open(DisplayEditor.ORIENTATION)
        }
    }
    DisplayCard(stringResource(R.string.display_behavior_display_aids), Icons.Default.AutoAwesome) {
        DisplayToggleSetting(
            controller,
            DisplayToggle.FPS,
            R.string.display_behavior_fps,
            R.string.display_behavior_fps_desc,
        )
        DisplayDivider()
        Column(Modifier.padding(horizontal = 17.dp, vertical = 15.dp)) {
            Text(
                stringResource(R.string.display_behavior_identity),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(
                Modifier.fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(4.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                        UserPreferencesManager.SOFTWARE_IDENTITY_OPERIT to
                            R.string.display_behavior_identity_operit,
                        UserPreferencesManager.SOFTWARE_IDENTITY_LINGSHU to
                            R.string.display_behavior_identity_lingshu,
                    )
                    .forEach { (value, title) ->
                        val selected = controller.values[DisplayChoice.IDENTITY] == value
                        Box(
                            Modifier.weight(1f)
                                .heightIn(min = 37.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.background
                                )
                                .selectable(
                                    selected,
                                    role = Role.RadioButton,
                                    onClick = { controller.set(DisplayChoice.IDENTITY, value) },
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(title),
                                fontSize = 12.sp,
                                color =
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
            }
            Text(
                stringResource(R.string.display_behavior_identity_desc),
                Modifier.padding(top = 9.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    DisplayHint(
        stringResource(R.string.display_behavior_interface_footer),
        Modifier.padding(start = 3.dp, end = 3.dp, bottom = 15.dp),
        icon = true,
    )
}

@Composable
internal fun DisplayAutomationTab(
    controller: DisplayBehaviorController,
    open: (DisplayEditor) -> Unit,
) {
    val values = controller.values
    DisplayCard(stringResource(R.string.display_behavior_run_mode), Icons.Default.AutoAwesome) {
        DisplayToggleSetting(
            controller,
            DisplayToggle.ACCESSIBILITY,
            R.string.display_behavior_accessibility,
            R.string.display_behavior_accessibility_desc,
        )
        DisplayDivider()
        DisplayToggleSetting(
            controller,
            DisplayToggle.KEEP_ALIVE,
            R.string.display_behavior_keep_alive,
            R.string.display_behavior_keep_alive_desc,
        )
        DisplayDivider()
        DisplayToggleSetting(
            controller,
            DisplayToggle.HIDE_TASK,
            R.string.display_behavior_hide_task,
            R.string.display_behavior_hide_task_desc,
        )
    }
    DisplayCard(stringResource(R.string.display_behavior_visuals), Icons.Default.Smartphone) {
        DisplayNavigationRow(
            stringResource(R.string.display_behavior_virtual_title),
            stringResource(R.string.display_behavior_virtual_desc),
            if (values[DisplayToggle.VIRTUAL_SCREEN])
                stringResource(
                    R.string.display_behavior_virtual_value,
                    displayBitrate(values[DisplayNumber.BITRATE]),
                )
            else stringResource(R.string.display_behavior_disabled),
        ) {
            open(DisplayEditor.VIRTUAL)
        }
        DisplayDivider()
        DisplayNavigationRow(
            stringResource(R.string.display_behavior_indicator_title),
            stringResource(R.string.display_behavior_indicator_desc),
            displayChoiceLabel(values, DisplayChoice.INDICATOR),
        ) {
            open(DisplayEditor.INDICATOR)
        }
        DisplayDivider()
        DisplayNavigationRow(
            stringResource(R.string.display_behavior_screenshot_title),
            if (values[DisplayChoice.FORMAT] == "PNG")
                stringResource(
                    R.string.display_behavior_screenshot_png_summary,
                    values[DisplayNumber.SCALE],
                )
            else
                stringResource(
                    R.string.display_behavior_screenshot_jpg_summary,
                    values[DisplayNumber.QUALITY],
                    values[DisplayNumber.SCALE],
                ),
            values[DisplayChoice.FORMAT],
        ) {
            open(DisplayEditor.SCREENSHOT)
        }
    }
    val seconds = stringResource(R.string.display_behavior_seconds_suffix)
    DisplayCard(stringResource(R.string.display_behavior_tools_plugins), Icons.Default.Schedule) {
        DisplayRange(
            stringResource(R.string.display_behavior_web_wait),
            stringResource(R.string.display_behavior_web_wait_desc),
            values[DisplayNumber.WEB_WAIT],
            DisplayNumber.WEB_WAIT.range,
            { "$it$seconds" },
            Modifier.padding(horizontal = 17.dp),
            onChange = { controller.set(DisplayNumber.WEB_WAIT, it) },
            onFinish = controller::flushNumbers,
        )
        DisplayDivider()
        DisplayRange(
            stringResource(R.string.display_behavior_hook_timeout),
            stringResource(R.string.display_behavior_hook_timeout_desc),
            values[DisplayNumber.HOOK_TIMEOUT],
            DisplayNumber.HOOK_TIMEOUT.range,
            { "$it$seconds" },
            Modifier.padding(horizontal = 17.dp),
            onChange = { controller.set(DisplayNumber.HOOK_TIMEOUT, it) },
            onFinish = controller::flushNumbers,
        )
        DisplayHint(
            stringResource(R.string.display_behavior_hook_hint),
            Modifier.padding(start = 17.dp, end = 17.dp, bottom = 14.dp),
        )
    }
    if (controller.rootAvailable) {
        DisplayCard(
            stringResource(R.string.display_behavior_root_group),
            Icons.Default.Terminal,
            stringResource(R.string.display_behavior_root_permission),
        ) {
            DisplayNavigationRow(
                stringResource(R.string.display_behavior_root_row),
                stringResource(R.string.display_behavior_root_desc),
                displayChoiceLabel(values, DisplayChoice.ROOT),
            ) {
                open(DisplayEditor.ROOT)
            }
            if (values[DisplayChoice.ROOT] == "FORCE_EXEC") {
                DisplayDivider()
                DisplayNavigationRow(
                    stringResource(R.string.display_behavior_su_title),
                    stringResource(R.string.display_behavior_su_row_desc),
                    values.suCommand,
                ) {
                    open(DisplayEditor.SU)
                }
            }
        }
    }
}

internal fun displayBitrate(value: Int): String =
    if (value % 1000 == 0) "${value / 1000} Mbps" else "${value / 1000.0} Mbps"
