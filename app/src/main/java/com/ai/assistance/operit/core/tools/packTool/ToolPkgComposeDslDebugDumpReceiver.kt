package com.ai.assistance.operit.core.tools.packTool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.ui.common.composedsl.ToolPkgComposeDslDebugSnapshotStore
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ToolPkgComposeDslDebugDumpReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ToolPkgComposeDslDebugDumpReceiver"

        const val ACTION_DUMP_COMPOSE_DSL_UI = "com.ai.assistance.operit.DUMP_COMPOSE_DSL_UI"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DUMP_COMPOSE_DSL_UI) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outputDir = ToolPkgComposeDslDebugSnapshotStore.dumpCurrentSnapshot(context)
                AppLogger.d(
                    TAG,
                    "compose_dsl debug dump written to ${outputDir.absolutePath}"
                )
            } catch (error: Exception) {
                AppLogger.e(TAG, "compose_dsl debug dump failed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
