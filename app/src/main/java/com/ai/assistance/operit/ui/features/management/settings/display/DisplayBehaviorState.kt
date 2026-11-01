package com.ai.assistance.operit.ui.features.management.settings.display

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.core.tools.permissions.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.OrientationReloadPolicy
import com.ai.assistance.operit.data.preferences.RootCommandExecutionMode
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.services.floating.StatusIndicatorStyle
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class DisplayToggle(val default: Boolean) {
    ENTER(false),
    NEW_CHAT(false),
    LONG_TEXT(true),
    NOTIFY(true),
    SOUND(false),
    VIBRATION(false),
    KEEP_SCREEN(true),
    ANIMATION(true),
    FPS(false),
    ACCESSIBILITY(false),
    KEEP_ALIVE(false),
    HIDE_TASK(false),
    VIRTUAL_SCREEN(true),
}

internal enum class DisplayNumber(val default: Int, val range: IntRange) {
    TEXT_THRESHOLD(3000, 1000..100000),
    QUALITY(75, 50..100),
    SCALE(75, 50..100),
    WEB_WAIT(0, 0..10),
    HOOK_TIMEOUT(10, 1..60),
    BITRATE(3000, 1500..20000),
}

internal enum class DisplayChoice(val default: String) {
    COLLAPSE(ToolCollapseMode.ALL.value),
    ORIENTATION(OrientationReloadPolicy.ASK.name),
    IDENTITY(UserPreferencesManager.SOFTWARE_IDENTITY_OPERIT),
    INDICATOR(StatusIndicatorStyle.FULLSCREEN_RAINBOW.name),
    FORMAT("JPG"),
    ROOT(RootCommandExecutionMode.AUTO.name),
}

internal data class DisplayBehaviorValues(
    val toggles: Map<DisplayToggle, Boolean> = DisplayToggle.entries.associateWith { it.default },
    val numbers: Map<DisplayNumber, Int> = DisplayNumber.entries.associateWith { it.default },
    val choices: Map<DisplayChoice, String> = DisplayChoice.entries.associateWith { it.default },
    val suCommand: String = "su",
) {
    operator fun get(key: DisplayToggle): Boolean = toggles.getValue(key)

    operator fun get(key: DisplayNumber): Int = numbers.getValue(key)

    operator fun get(key: DisplayChoice): String = choices.getValue(key)
}

/** 单独确认数字编辑，非法输入不写入，避免把半成品数字保存成其他值。 */
internal fun parseDisplayThreshold(text: String): Int? =
    text.toIntOrNull()?.takeIf { it in DisplayNumber.TEXT_THRESHOLD.range }

/** 沿用原偏好来源；页面离开时排空已提交操作，避免最后一次修改随组合销毁而丢失。 */
internal class DisplayBehaviorController(context: Context) {
    private val context = context.applicationContext
    private val display = DisplayPreferencesManager.getInstance(this.context)
    private val api = ApiPreferences.getInstance(this.context)
    private val user = UserPreferencesManager.getInstance(this.context)
    private val indicator =
        this.context.getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operations = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var revision = 0
    private var pending = 0
    private var numberJob: Job? = null
    private val numberChanges = linkedMapOf<DisplayNumber, Int>()

    var values by mutableStateOf(DisplayBehaviorValues())
        private set

    var ready by mutableStateOf(false)
        private set

    var rootAvailable by mutableStateOf(false)
        private set

    var saving by mutableStateOf(false)
        private set

    var saved by mutableStateOf(false)
        private set

    var error by mutableStateOf<Exception?>(null)
        private set

    init {
        scope.launch {
            try {
                for (operation in operations) {
                    try {
                        operation()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (exception: Exception) {
                        AppLogger.e("DisplayBehavior", "读取或保存显示与行为设置失败", exception)
                        error = exception
                    } finally {
                        pending--
                        saving = pending > 0 || numberChanges.isNotEmpty()
                        if (!saving && ready && error == null) saved = true
                    }
                }
            } finally {
                scope.cancel()
            }
        }
    }

    private fun enqueue(operation: suspend () -> Unit) {
        pending++
        saving = true
        check(operations.trySend(operation).isSuccess)
    }

    fun refresh() = enqueue {
        val loadRevision = revision
        val toggles = DisplayToggle.entries.associateWith { readToggle(it) }
        val numbers = DisplayNumber.entries.associateWith { readNumber(it) }
        val choices = DisplayChoice.entries.associateWith { readChoice(it) }
        val command = androidPermissionPreferences.customSuCommandFlow.first()
        rootAvailable =
            androidPermissionPreferences.preferredPermissionLevelFlow.first() ==
                AndroidPermissionLevel.ROOT
        // 读取过程中发生的用户编辑由队列随后保存，不能被旧快照覆盖。
        if (revision == loadRevision) {
            values = DisplayBehaviorValues(toggles, numbers, choices, command)
            error = null
        }
        ready = true
    }

    fun set(key: DisplayToggle, value: Boolean) {
        revision++
        values = values.copy(toggles = values.toggles + (key to value))
        enqueue { writeToggle(key, value) }
    }

    fun set(key: DisplayNumber, value: Int) {
        require(value in key.range)
        if (values[key] == value) return
        revision++
        values = values.copy(numbers = values.numbers + (key to value))
        numberChanges[key] = value
        saving = true
        numberJob?.cancel()
        numberJob =
            scope.launch {
                delay(300)
                flushNumbers()
            }
    }

    fun set(key: DisplayChoice, value: String) {
        revision++
        values = values.copy(choices = values.choices + (key to value))
        enqueue { writeChoice(key, value) }
    }

    fun setSuCommand(value: String) {
        require(value.isNotBlank())
        revision++
        values = values.copy(suCommand = value.trim())
        enqueue { androidPermissionPreferences.saveCustomSuCommand(value.trim()) }
    }

    fun reset() {
        flushNumbers()
        revision++
        values = DisplayBehaviorValues()
        enqueue {
            // 只重置本页，显示偏好重置不得再删除全局头像与用户名。
            display.resetDisplaySettings()
            api.saveKeepScreenOn(true)
            user.saveConvertLongPastedTextToFile(true)
            user.saveLongPastedTextFileThreshold(
                UserPreferencesManager.DEFAULT_LONG_PASTED_TEXT_FILE_THRESHOLD
            )
            user.saveUiAccessibilityMode(false)
            user.saveSoftwareIdentity(UserPreferencesManager.SOFTWARE_IDENTITY_OPERIT)
            androidPermissionPreferences.resetRootExecutionSettings()
            withContext(Dispatchers.IO) {
                check(
                    indicator
                        .edit()
                        .putString(
                            "status_indicator_style",
                            StatusIndicatorStyle.FULLSCREEN_RAINBOW.name,
                        )
                        .commit()
                )
            }
            AIForegroundService.refreshBackgroundKeepAlive(context)
        }
    }

    fun flushNumbers() {
        numberJob?.cancel()
        numberJob = null
        val changes = numberChanges.toMap()
        numberChanges.clear()
        if (changes.isNotEmpty())
            enqueue { changes.forEach { (key, value) -> writeNumber(key, value) } }
    }

    fun close() {
        flushNumbers()
        operations.close()
    }

    private suspend fun readToggle(key: DisplayToggle): Boolean =
        when (key) {
            DisplayToggle.ENTER -> display.enableEnterToSend.first()
            DisplayToggle.NEW_CHAT -> display.startWithNewChat.first()
            DisplayToggle.LONG_TEXT -> user.convertLongPastedTextToFile.first()
            DisplayToggle.NOTIFY -> display.enableReplyNotification.first()
            DisplayToggle.SOUND -> display.enableReplyNotificationSound.first()
            DisplayToggle.VIBRATION -> display.enableReplyNotificationVibration.first()
            DisplayToggle.KEEP_SCREEN -> api.keepScreenOnFlow.first()
            DisplayToggle.ANIMATION -> display.enableNavigationAnimation.first()
            DisplayToggle.FPS -> display.showFpsCounter.first()
            DisplayToggle.ACCESSIBILITY -> user.uiAccessibilityMode.first()
            DisplayToggle.KEEP_ALIVE -> display.enableBackgroundKeepAlive.first()
            DisplayToggle.HIDE_TASK -> display.hideRuntimeTaskView.first()
            DisplayToggle.VIRTUAL_SCREEN -> display.enableExperimentalVirtualDisplay.first()
        }

    private suspend fun readNumber(key: DisplayNumber): Int =
        when (key) {
            DisplayNumber.TEXT_THRESHOLD -> user.longPastedTextFileThreshold.first()
            DisplayNumber.QUALITY -> display.screenshotQuality.first()
            DisplayNumber.SCALE -> display.screenshotScalePercent.first()
            DisplayNumber.WEB_WAIT -> display.visitWebWaitSeconds.first()
            DisplayNumber.HOOK_TIMEOUT -> display.toolPkgHookTimeoutSeconds.first()
            DisplayNumber.BITRATE -> display.virtualDisplayBitrateKbps.first()
        }

    private suspend fun readChoice(key: DisplayChoice): String =
        when (key) {
            DisplayChoice.COLLAPSE -> display.toolCollapseMode.first().value
            DisplayChoice.ORIENTATION -> display.orientationReloadPolicy.first().name
            DisplayChoice.IDENTITY -> user.softwareIdentity.first()
            DisplayChoice.INDICATOR ->
                indicator.getString(
                    "status_indicator_style",
                    StatusIndicatorStyle.FULLSCREEN_RAINBOW.name,
                )!!
            DisplayChoice.FORMAT ->
                display.screenshotFormat.first().uppercase().let { if (it == "JPEG") "JPG" else it }
            DisplayChoice.ROOT -> androidPermissionPreferences.rootExecutionModeFlow.first().name
        }

    private suspend fun writeToggle(key: DisplayToggle, value: Boolean) {
        when (key) {
            DisplayToggle.ENTER -> display.saveDisplaySettings(enableEnterToSend = value)
            DisplayToggle.NEW_CHAT -> display.saveDisplaySettings(startWithNewChat = value)
            DisplayToggle.LONG_TEXT -> user.saveConvertLongPastedTextToFile(value)
            DisplayToggle.NOTIFY -> display.saveDisplaySettings(enableReplyNotification = value)
            DisplayToggle.SOUND -> display.saveDisplaySettings(enableReplyNotificationSound = value)
            DisplayToggle.VIBRATION ->
                display.saveDisplaySettings(enableReplyNotificationVibration = value)
            DisplayToggle.KEEP_SCREEN -> api.saveKeepScreenOn(value)
            DisplayToggle.ANIMATION ->
                display.saveDisplaySettings(enableNavigationAnimation = value)
            DisplayToggle.FPS -> display.saveDisplaySettings(showFpsCounter = value)
            DisplayToggle.ACCESSIBILITY -> user.saveUiAccessibilityMode(value)
            DisplayToggle.KEEP_ALIVE -> {
                display.saveDisplaySettings(enableBackgroundKeepAlive = value)
                AIForegroundService.refreshBackgroundKeepAlive(context)
            }
            DisplayToggle.HIDE_TASK -> display.saveDisplaySettings(hideRuntimeTaskView = value)
            DisplayToggle.VIRTUAL_SCREEN ->
                display.saveDisplaySettings(enableExperimentalVirtualDisplay = value)
        }
    }

    private suspend fun writeNumber(key: DisplayNumber, value: Int) {
        when (key) {
            DisplayNumber.TEXT_THRESHOLD -> user.saveLongPastedTextFileThreshold(value)
            DisplayNumber.QUALITY -> display.saveDisplaySettings(screenshotQuality = value)
            DisplayNumber.SCALE -> display.saveDisplaySettings(screenshotScalePercent = value)
            DisplayNumber.WEB_WAIT -> display.saveDisplaySettings(visitWebWaitSeconds = value)
            DisplayNumber.HOOK_TIMEOUT ->
                display.saveDisplaySettings(toolPkgHookTimeoutSeconds = value)
            DisplayNumber.BITRATE -> display.saveDisplaySettings(virtualDisplayBitrateKbps = value)
        }
    }

    private suspend fun writeChoice(key: DisplayChoice, value: String) {
        when (key) {
            DisplayChoice.COLLAPSE ->
                display.saveDisplaySettings(
                    toolCollapseMode = ToolCollapseMode.entries.single { it.value == value }
                )
            DisplayChoice.ORIENTATION ->
                display.saveDisplaySettings(
                    orientationReloadPolicy = OrientationReloadPolicy.valueOf(value)
                )
            DisplayChoice.IDENTITY -> user.saveSoftwareIdentity(value)
            DisplayChoice.INDICATOR ->
                withContext(Dispatchers.IO) {
                    check(indicator.edit().putString("status_indicator_style", value).commit())
                }
            DisplayChoice.FORMAT -> display.saveDisplaySettings(screenshotFormat = value)
            DisplayChoice.ROOT ->
                androidPermissionPreferences.saveRootExecutionMode(
                    RootCommandExecutionMode.valueOf(value)
                )
        }
    }
}
