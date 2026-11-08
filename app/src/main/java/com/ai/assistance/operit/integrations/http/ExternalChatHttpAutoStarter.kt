package com.ai.assistance.operit.integrations.http

import android.content.Context
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.util.logging.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ExternalChatHttpAutoStarter {

    private const val TAG = "ExternalChatHttpAutoStart"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ensureInProgress = AtomicBoolean(false)

    fun ensureRunningIfEnabled(context: Context, reason: String) {
        val appContext = context.applicationContext
        if (!ensureInProgress.compareAndSet(false, true)) {
            AppLogger.d(TAG, "Skip ensure because a previous check is still running: $reason")
            return
        }

        scope.launch {
            try {
                val preferences = ExternalHttpApiPreferences.getInstance(appContext)
                val config = preferences.getConfig()
                if (!config.enabled) {
                    AppLogger.d(TAG, "External HTTP chat disabled, skip auto start: $reason")
                    return@launch
                }
                if (!ExternalHttpApiPreferences.isValidPort(config.port)) {
                    AppLogger.w(TAG, "Skip auto start because configured port is invalid: ${config.port}, reason=$reason")
                    return@launch
                }

                val currentState = AIForegroundService.externalHttpState.value
                if (currentState.isRunning && currentState.port == config.port) {
                    AppLogger.d(TAG, "External HTTP chat service already running on port ${config.port}: $reason")
                    return@launch
                }

                AppLogger.i(TAG, "Auto starting External HTTP chat service on port ${config.port}: $reason")
                AIForegroundService.ensureRunningForExternalHttp(appContext)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to auto start External HTTP chat service: $reason", e)
            } finally {
                ensureInProgress.set(false)
            }
        }
    }
}
