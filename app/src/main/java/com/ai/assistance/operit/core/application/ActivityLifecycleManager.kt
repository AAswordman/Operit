package com.ai.assistance.operit.core.application

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.ai.assistance.operit.util.AppLogger
import android.view.WindowManager
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.api.chat.ChatRuntimeStateApplicationState
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleEvent
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookParams
import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookPluginRegistry
import com.ai.assistance.operit.integrations.http.ExternalChatHttpAutoStarter
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.core.tools.agent.ShowerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * A robust manager to track the current foreground activity using Android's standard
 * ActivityLifecycleCallbacks. This avoids reflection and provides a stable way to get
 * the current activity context when needed.
 */
object ActivityLifecycleManager : Application.ActivityLifecycleCallbacks {

    private const val TAG = "ActivityLifecycleManager"
    private var currentActivity: WeakReference<Activity>? = null
    private lateinit var apiPreferences: ApiPreferences
    private lateinit var appContext: android.content.Context
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activityCount = 0
    private var startedActivityCount = 0
    private var isAppInForeground = false
    private val _applicationVisibilityState =
        MutableStateFlow(ChatRuntimeStateApplicationState.BACKGROUND)
    val applicationVisibilityState: StateFlow<ChatRuntimeStateApplicationState> =
        _applicationVisibilityState.asStateFlow()
    private var keepScreenOnPreferenceRequestCount = 0
    private var keepScreenOnForcedRequestCount = 0

    @Volatile
    private var lastMicEnsureAtMs: Long = 0L

    /**
     * Initializes the manager and registers it with the application.
     * This should be called once from the Application's `onCreate` method.
     * @param application The application instance.
     */
    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        apiPreferences = ApiPreferences.getInstance(application.applicationContext)
        appContext = application.applicationContext
    }

    /**
     * Retrieves the current foreground activity, if available.
     * @return The current Activity, or null if no activity is in the foreground or tracked.
     */
    fun getCurrentActivity(): Activity? {
        return currentActivity?.get()
    }

    /**
     * Checks the user preference and applies the keep screen on flag to the current activity's window.
     * This operation is performed on the main thread.
     *
     * @param enable True to add the `FLAG_KEEP_SCREEN_ON`, false to clear it.
     */
    fun checkAndApplyKeepScreenOn(enable: Boolean) {
        applyKeepScreenOnRequest(enable = enable, respectUserPreference = true)
    }

    fun forceKeepScreenOn(enable: Boolean) {
        applyKeepScreenOnRequest(enable = enable, respectUserPreference = false)
    }

    private fun applyKeepScreenOnRequest(enable: Boolean, respectUserPreference: Boolean) {
        scope.launch {
            try {
                if (enable && respectUserPreference && !apiPreferences.keepScreenOnFlow.first()) {
                    return@launch
                }

                if (respectUserPreference) {
                    if (enable) {
                        keepScreenOnPreferenceRequestCount += 1
                    } else if (keepScreenOnPreferenceRequestCount > 0) {
                        keepScreenOnPreferenceRequestCount -= 1
                    }
                } else {
                    if (enable) {
                        keepScreenOnForcedRequestCount += 1
                    } else if (keepScreenOnForcedRequestCount > 0) {
                        keepScreenOnForcedRequestCount -= 1
                    }
                }

                val activity = getCurrentActivity()
                if (activity == null) {
                    AppLogger.w(TAG, "Cannot apply screen on flag: current activity is null.")
                    return@launch
                }

                // Window operations must be done on the UI thread.
                activity.runOnUiThread {
                    val window = activity.window
                    if (keepScreenOnPreferenceRequestCount + keepScreenOnForcedRequestCount > 0) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        AppLogger.d(TAG, "FLAG_KEEP_SCREEN_ON added.")
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        AppLogger.d(TAG, "FLAG_KEEP_SCREEN_ON cleared.")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to apply screen on flag", e)
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activityCount++
        AppLogger.d(TAG, "Activity created: ${activity.javaClass.simpleName}, count=$activityCount")
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_CREATE,
            params =
                AppLifecycleHookParams(
                    context = appContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name
                        )
                )
        )
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount += 1
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_START,
            params =
                AppLifecycleHookParams(
                    context = appContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name
                        )
                )
        )
        if (!isAppInForeground && startedActivityCount > 0) {
            isAppInForeground = true
            _applicationVisibilityState.value = ChatRuntimeStateApplicationState.FOREGROUND
            ExternalChatHttpAutoStarter.ensureRunningIfEnabled(
                context = appContext,
                reason = "application_foreground"
            )
            AppLifecycleHookPluginRegistry.dispatchAsync(
                event = AppLifecycleEvent.APPLICATION_FOREGROUND,
                params = AppLifecycleHookParams(context = appContext)
            )
        }
    }

    override fun onActivityResumed(activity: Activity) {
        // When an activity is resumed, it becomes the current foreground activity.
        currentActivity = WeakReference(activity)

        try {
            val now = System.currentTimeMillis()
            if (now - lastMicEnsureAtMs >= 2500L) {
                lastMicEnsureAtMs = now
                AIForegroundService.ensureMicrophoneForeground(activity.applicationContext)
            }
        } catch (_: Exception) {
        }
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_RESUME,
            params =
                AppLifecycleHookParams(
                    context = appContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name
                        )
                )
        )
    }

    override fun onActivityPaused(activity: Activity) {
        // If the paused activity is the one we are currently tracking, clear it.
        if (currentActivity?.get() == activity) {
            currentActivity?.clear()
        }
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_PAUSE,
            params =
                AppLifecycleHookParams(
                    context = appContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name
                        )
                )
        )
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_STOP,
            params =
                AppLifecycleHookParams(
                    context = appContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name
                        )
                )
        )
        if (isAppInForeground && startedActivityCount == 0) {
            isAppInForeground = false
            _applicationVisibilityState.value = ChatRuntimeStateApplicationState.BACKGROUND
            AppLifecycleHookPluginRegistry.dispatchAsync(
                event = AppLifecycleEvent.APPLICATION_BACKGROUND,
                params = AppLifecycleHookParams(context = appContext)
            )
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // Not used, but required by the interface.
    }

    override fun onActivityDestroyed(activity: Activity) {
        // If the destroyed activity is the one we are tracking, ensure it is cleared.
        if (currentActivity?.get() == activity) {
            currentActivity?.clear()
        }
        
        activityCount--
        AppLogger.d(TAG, "Activity destroyed: ${activity.javaClass.simpleName}, count=$activityCount")
        AppLifecycleHookPluginRegistry.dispatchAsync(
            event = AppLifecycleEvent.ACTIVITY_DESTROY,
            params =
                AppLifecycleHookParams(
                    context = appContext,
                    extras =
                        mapOf(
                            "activityClassName" to activity.javaClass.name
                        )
                )
        )
        
        // 当最后一个 Activity 被销毁时（包括从最近任务列表滑动关闭），清理虚拟屏幕和 Shower 连接
        if (activityCount <= 0) {
            AppLogger.d(TAG, "最后一个 Activity 被销毁，清理虚拟屏幕资源")
            try {
                VirtualDisplayOverlay.hideAll()
                AppLogger.d(TAG, "已关闭 VirtualDisplayOverlay")
            } catch (e: Exception) {
                AppLogger.e(TAG, "清理 VirtualDisplayOverlay 失败", e)
            }
            try {
                ShowerController.shutdown()
                AppLogger.d(TAG, "已关闭 ShowerController")
            } catch (e: Exception) {
                AppLogger.e(TAG, "清理 ShowerController 失败", e)
            }
        }
    }
} 
