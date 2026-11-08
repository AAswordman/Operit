package com.ai.assistance.operit.core.tools.packTool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager

class PackageDebugRefreshReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageDebugRefreshReceiver"

        const val ACTION_DEBUG_REFRESH_PACKAGES = "com.ai.assistance.operit.DEBUG_REFRESH_PACKAGES"
        const val EXTRA_REACTIVATE_ACTIVE_PACKAGES = "reactivate_active_packages"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEBUG_REFRESH_PACKAGES) {
            return
        }

        val reactivateActivePackages =
            intent.getBooleanExtra(EXTRA_REACTIVATE_ACTIVE_PACKAGES, true)

        CoroutineScope(Dispatchers.IO).launch {
            refreshPackages(
                context = context,
                reactivateActivePackages = reactivateActivePackages
            )
        }
    }

    private fun refreshPackages(
        context: Context,
        reactivateActivePackages: Boolean
    ) {
        AppLogger.d(
            TAG,
            "Starting debug package refresh: reactivateActivePackages=$reactivateActivePackages"
        )

        try {
            val aiToolHandler = AIToolHandler.getInstance(context)
            val packageManager = PackageManager.getInstance(context, aiToolHandler)
            val result =
                packageManager.refreshExternalPackagesForDebug(
                    reactivateActivePackages = reactivateActivePackages
                )
            AppLogger.d(TAG, result)
        } catch (error: Exception) {
            AppLogger.e(TAG, "Debug package refresh failed", error)
        }
    }
}
