package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// DataStore 实例
private val Context.displayPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "display_preferences"
)

/**
 * DisplayPreferencesManager
 * 管理系统显示与行为相关的偏好设置
 * 使用单例模式，避免重复创建实例
 */
class DisplayPreferencesManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: DisplayPreferencesManager? = null

        fun getInstance(context: Context): DisplayPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                val instance = DisplayPreferencesManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        // 显示相关设置的 Key
        private val KEY_SHOW_FPS_COUNTER = booleanPreferencesKey("show_fps_counter")
        private val KEY_ENABLE_REPLY_NOTIFICATION = booleanPreferencesKey("enable_reply_notification")
        private val KEY_ENABLE_REPLY_NOTIFICATION_SOUND =
            booleanPreferencesKey("enable_reply_notification_sound")
        private val KEY_ENABLE_REPLY_NOTIFICATION_VIBRATION =
            booleanPreferencesKey("enable_reply_notification_vibration")
        private val KEY_ENABLE_ENTER_TO_SEND = booleanPreferencesKey("enable_enter_to_send")
        private val KEY_ENABLE_NAVIGATION_ANIMATION =
            booleanPreferencesKey("enable_navigation_animation")
        private val KEY_START_WITH_NEW_CHAT =
            booleanPreferencesKey("start_with_new_chat")

        // 全局用户资料的 Key
        private val KEY_GLOBAL_USER_AVATAR_URI = stringPreferencesKey("global_user_avatar_uri")
        private val KEY_GLOBAL_USER_NAME = stringPreferencesKey("global_user_name")

        // 自动化显示与行为相关设置的 Key
        private val KEY_ENABLE_BACKGROUND_KEEP_ALIVE =
            booleanPreferencesKey("enable_background_keep_alive")
        private val KEY_ENABLE_EXPERIMENTAL_VIRTUAL_DISPLAY =
            booleanPreferencesKey("enable_experimental_virtual_display")
        private val KEY_HIDE_RUNTIME_TASK_VIEW =
            booleanPreferencesKey("hide_runtime_task_view")

        private val KEY_SCREENSHOT_FORMAT = stringPreferencesKey("screenshot_format")
        private val KEY_SCREENSHOT_QUALITY = intPreferencesKey("screenshot_quality")
        private val KEY_SCREENSHOT_SCALE_PERCENT = intPreferencesKey("screenshot_scale_percent")
        private val KEY_VISIT_WEB_WAIT_SECONDS = intPreferencesKey("visit_web_wait_seconds")
        private val KEY_TOOLPKG_HOOK_TIMEOUT_SECONDS = intPreferencesKey("toolpkg_hook_timeout_seconds")

        // 虚拟屏幕相关设置的 Key
        private val KEY_VIRTUAL_DISPLAY_BITRATE_KBPS = intPreferencesKey("virtual_display_bitrate_kbps")

        // 工具折叠设置（多个只读工具 / 多个任意工具 / 全部工具）
        private val KEY_TOOL_COLLAPSE_MODE = stringPreferencesKey("tool_collapse_mode")
        // 滚动行为相关设置的 Key
        private val KEY_STREAM_SCROLL_MAX_SPEED_DP =
            intPreferencesKey("stream_scroll_max_speed_dp")
        private val KEY_ENABLE_NON_STREAMING_SCROLL_TO_TOP =
            booleanPreferencesKey("enable_non_streaming_scroll_to_top")

        /** 流式输出滚动速度上限的起步档（dp/s） */
        const val STREAM_SCROLL_SPEED_MIN_DP = 50

        /** 流式输出滚动速度上限的档位步进（dp/s） */
        const val STREAM_SCROLL_SPEED_STEP_DP = 50

        /**
         * 流式输出滚动速度上限的最高有效限速档（dp/s）。
         * 即倒数第二档；再往上一档为 [STREAM_SCROLL_SPEED_UNLIMITED]。
         */
        const val STREAM_SCROLL_SPEED_MAX_LIMITED_DP = 1000

        /**
         * 最后一档：不设上限。
         * 此时滚动速度完全跟随模型吞吐量，模型输出多快屏幕就滚多快。
         */
        const val STREAM_SCROLL_SPEED_UNLIMITED = Int.MAX_VALUE

        /** 流式输出滚动速度上限的默认值（dp/s），默认即启用限速 */
        const val STREAM_SCROLL_SPEED_DEFAULT_DP = 600

        /**
         * 全部可选档位：50..1000（步进 50，共 20 档）+ 不限速（第 21 档）。
         */
        val STREAM_SCROLL_SPEED_OPTIONS: List<Int> =
            (STREAM_SCROLL_SPEED_MIN_DP..STREAM_SCROLL_SPEED_MAX_LIMITED_DP step STREAM_SCROLL_SPEED_STEP_DP)
                .toList() + STREAM_SCROLL_SPEED_UNLIMITED
    }

    /**
     * 是否显示FPS计数器
     * 默认值：false
     */
    val showFpsCounter: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SHOW_FPS_COUNTER] ?: false
        }

    /**
     * 是否启用回复通知
     * 默认值：true
     */
    val enableReplyNotification: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_ENABLE_REPLY_NOTIFICATION] ?: true
        }

    /**
     * 是否启用回复通知提示音
     * 默认值：false
     */
    val enableReplyNotificationSound: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_ENABLE_REPLY_NOTIFICATION_SOUND] ?: false
        }

    /**
     * 是否启用回复通知震动
     * 默认值：false
     */
    val enableReplyNotificationVibration: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_ENABLE_REPLY_NOTIFICATION_VIBRATION] ?: false
        }

    /**
     * 是否启用回车发送
     * 默认值：false
     */
    val enableEnterToSend: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_ENABLE_ENTER_TO_SEND] ?: false
        }

    /**
     * 是否启用新版导航动画
     * 默认值：true
     */
    val enableNavigationAnimation: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_ENABLE_NAVIGATION_ANIMATION] ?: true
        }

    /**
     * 是否每次启动应用时新建空白聊天
     * 默认值：false
     */
    val startWithNewChat: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_START_WITH_NEW_CHAT] ?: false
        }

    /**
     * 全局用户头像URI
     */
    val globalUserAvatarUri: Flow<String?> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_GLOBAL_USER_AVATAR_URI]
        }

    /**
     * 全局用户名称
     */
    val globalUserName: Flow<String?> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_GLOBAL_USER_NAME]
        }

    val enableBackgroundKeepAlive: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_ENABLE_BACKGROUND_KEEP_ALIVE] ?: false
        }

    val enableExperimentalVirtualDisplay: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_ENABLE_EXPERIMENTAL_VIRTUAL_DISPLAY] ?: true
        }

    val hideRuntimeTaskView: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_HIDE_RUNTIME_TASK_VIEW] ?: false
        }

    val screenshotFormat: Flow<String> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SCREENSHOT_FORMAT] ?: "JPG"
        }

    val screenshotQuality: Flow<Int> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SCREENSHOT_QUALITY] ?: 75
        }

    val screenshotScalePercent: Flow<Int> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_SCREENSHOT_SCALE_PERCENT] ?: 75
        }

    val visitWebWaitSeconds: Flow<Int> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_VISIT_WEB_WAIT_SECONDS] ?: 0
        }

    val toolPkgHookTimeoutSeconds: Flow<Int> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_TOOLPKG_HOOK_TIMEOUT_SECONDS] ?: 10
        }

    val virtualDisplayBitrateKbps: Flow<Int> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_VIRTUAL_DISPLAY_BITRATE_KBPS] ?: 3000
        }

    val toolCollapseMode: Flow<ToolCollapseMode> =
        context.displayPreferencesDataStore.data.map { preferences ->
            ToolCollapseMode.fromValue(preferences[KEY_TOOL_COLLAPSE_MODE])
        }

    /**
     * 流式输出时页面自动滚动的速度上限（dp/s）。
     * 取 [STREAM_SCROLL_SPEED_UNLIMITED] 时表示最后一档“不设上限”，滚动速度跟随模型吞吐量。
     * 默认值：[STREAM_SCROLL_SPEED_DEFAULT_DP]
     */
    val streamScrollMaxSpeedDp: Flow<Int> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_STREAM_SCROLL_MAX_SPEED_DP] ?: STREAM_SCROLL_SPEED_DEFAULT_DP
        }

    /**
     * 是否在非流式输出时将页面定位到消息开头
     * 默认值：true
     */
    val enableNonStreamingScrollToTop: Flow<Boolean> =
        context.displayPreferencesDataStore.data.map { preferences ->
            preferences[KEY_ENABLE_NON_STREAMING_SCROLL_TO_TOP] ?: true
        }

    /**
     * 保存显示设置
     */
    suspend fun saveDisplaySettings(
        showFpsCounter: Boolean? = null,
        enableReplyNotification: Boolean? = null,
        enableReplyNotificationSound: Boolean? = null,
        enableReplyNotificationVibration: Boolean? = null,
        enableEnterToSend: Boolean? = null,
        enableNavigationAnimation: Boolean? = null,
        startWithNewChat: Boolean? = null,
        globalUserAvatarUri: String? = null,
        globalUserName: String? = null,
        enableBackgroundKeepAlive: Boolean? = null,
        enableExperimentalVirtualDisplay: Boolean? = null,
        hideRuntimeTaskView: Boolean? = null,
        screenshotFormat: String? = null,
        screenshotQuality: Int? = null,
        screenshotScalePercent: Int? = null,
        visitWebWaitSeconds: Int? = null,
        toolPkgHookTimeoutSeconds: Int? = null,
        virtualDisplayBitrateKbps: Int? = null,
        toolCollapseMode: ToolCollapseMode? = null,
        streamScrollMaxSpeedDp: Int? = null,
        enableNonStreamingScrollToTop: Boolean? = null
    ) {
        context.displayPreferencesDataStore.edit { preferences ->
            showFpsCounter?.let { preferences[KEY_SHOW_FPS_COUNTER] = it }
            enableReplyNotification?.let { preferences[KEY_ENABLE_REPLY_NOTIFICATION] = it }
            enableReplyNotificationSound?.let {
                preferences[KEY_ENABLE_REPLY_NOTIFICATION_SOUND] = it
            }
            enableReplyNotificationVibration?.let {
                preferences[KEY_ENABLE_REPLY_NOTIFICATION_VIBRATION] = it
            }
            enableEnterToSend?.let { preferences[KEY_ENABLE_ENTER_TO_SEND] = it }
            enableNavigationAnimation?.let {
                preferences[KEY_ENABLE_NAVIGATION_ANIMATION] = it
            }
            startWithNewChat?.let {
                preferences[KEY_START_WITH_NEW_CHAT] = it
            }
            globalUserAvatarUri?.let { preferences[KEY_GLOBAL_USER_AVATAR_URI] = it }
            globalUserName?.let { preferences[KEY_GLOBAL_USER_NAME] = it }
            enableBackgroundKeepAlive?.let {
                preferences[KEY_ENABLE_BACKGROUND_KEEP_ALIVE] = it
            }
            enableExperimentalVirtualDisplay?.let {
                preferences[KEY_ENABLE_EXPERIMENTAL_VIRTUAL_DISPLAY] = it
            }
            hideRuntimeTaskView?.let {
                preferences[KEY_HIDE_RUNTIME_TASK_VIEW] = it
            }
            screenshotFormat?.let { preferences[KEY_SCREENSHOT_FORMAT] = it }
            screenshotQuality?.let { preferences[KEY_SCREENSHOT_QUALITY] = it }
            screenshotScalePercent?.let { preferences[KEY_SCREENSHOT_SCALE_PERCENT] = it }
            visitWebWaitSeconds?.let { preferences[KEY_VISIT_WEB_WAIT_SECONDS] = it.coerceAtLeast(0) }
            toolPkgHookTimeoutSeconds?.let {
                preferences[KEY_TOOLPKG_HOOK_TIMEOUT_SECONDS] = it.coerceIn(1, 60)
            }
            virtualDisplayBitrateKbps?.let { preferences[KEY_VIRTUAL_DISPLAY_BITRATE_KBPS] = it }
            toolCollapseMode?.let { preferences[KEY_TOOL_COLLAPSE_MODE] = it.value }
            streamScrollMaxSpeedDp?.let {
                preferences[KEY_STREAM_SCROLL_MAX_SPEED_DP] =
                    if (it == STREAM_SCROLL_SPEED_UNLIMITED) {
                        STREAM_SCROLL_SPEED_UNLIMITED
                    } else {
                        it.coerceIn(STREAM_SCROLL_SPEED_MIN_DP, STREAM_SCROLL_SPEED_MAX_LIMITED_DP)
                    }
            }
            enableNonStreamingScrollToTop?.let {
                preferences[KEY_ENABLE_NON_STREAMING_SCROLL_TO_TOP] = it
            }
        }
    }

    fun isExperimentalVirtualDisplayEnabled(): Boolean {
        return runBlocking {
            enableExperimentalVirtualDisplay.first()
        }
    }

    fun getScreenshotFormat(): String {
        return runBlocking {
            screenshotFormat.first()
        }
    }

    fun getScreenshotQuality(): Int {
        return runBlocking {
            screenshotQuality.first()
        }
    }

    fun getScreenshotScalePercent(): Int {
        return runBlocking {
            screenshotScalePercent.first()
        }
    }

    fun getVisitWebWaitSeconds(): Int {
        return runBlocking {
            visitWebWaitSeconds.first()
        }
    }

    fun getToolPkgHookTimeoutSeconds(): Int {
        return runBlocking {
            toolPkgHookTimeoutSeconds.first()
        }
    }

    fun getVirtualDisplayBitrateKbps(): Int {
        return runBlocking {
            virtualDisplayBitrateKbps.first()
        }
    }

    /**
     * 重置所有显示设置为默认值
     */
    suspend fun resetDisplaySettings() {
        context.displayPreferencesDataStore.edit { preferences ->
            preferences[KEY_SHOW_FPS_COUNTER] = false
            preferences[KEY_ENABLE_REPLY_NOTIFICATION] = true
            preferences[KEY_ENABLE_REPLY_NOTIFICATION_SOUND] = false
            preferences[KEY_ENABLE_REPLY_NOTIFICATION_VIBRATION] = false
            preferences[KEY_ENABLE_ENTER_TO_SEND] = false
            preferences.remove(KEY_ENABLE_NAVIGATION_ANIMATION)
            preferences[KEY_START_WITH_NEW_CHAT] = false
            preferences.remove(KEY_GLOBAL_USER_AVATAR_URI)
            preferences.remove(KEY_GLOBAL_USER_NAME)
            preferences[KEY_ENABLE_BACKGROUND_KEEP_ALIVE] = false
            preferences[KEY_ENABLE_EXPERIMENTAL_VIRTUAL_DISPLAY] = true
            preferences[KEY_HIDE_RUNTIME_TASK_VIEW] = false
            preferences.remove(KEY_SCREENSHOT_FORMAT)
            preferences.remove(KEY_SCREENSHOT_QUALITY)
            preferences.remove(KEY_SCREENSHOT_SCALE_PERCENT)
            preferences.remove(KEY_VISIT_WEB_WAIT_SECONDS)
            preferences.remove(KEY_TOOLPKG_HOOK_TIMEOUT_SECONDS)
            preferences.remove(KEY_VIRTUAL_DISPLAY_BITRATE_KBPS)
            preferences.remove(KEY_TOOL_COLLAPSE_MODE)
        }
    }
}
