package com.ai.assistance.operit.feature.lifecare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.ai.assistance.operit.util.AppLogger

class LifeCareAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        AppLogger.d(TAG, "LifeCareAlarmReceiver received action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                LifeCompanionCareManager.scheduleDailyMidnightCheck(context)
                LifeCompanionCareManager.runIfNeededOnLaunch(context)
            }
            LifeCompanionCareManager.ACTION_LIFE_CARE_MIDNIGHT_CHECK -> {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Operit:LifeCareAlarm"
                )
                wakeLock?.setReferenceCounted(false)
                try {
                    wakeLock?.acquire(120_000L)
                    LifeCompanionCareManager.runAlarmTriggeredMidnightCheck(context)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Life care alarm handling failed", e)
                } finally {
                    runCatching {
                        if (wakeLock?.isHeld == true) wakeLock.release()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "LifeCareAlarmReceiver"
    }
}

