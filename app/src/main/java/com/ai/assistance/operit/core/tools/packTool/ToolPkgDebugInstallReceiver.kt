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

class ToolPkgDebugInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ToolPkgDebugInstallReceiver"

        const val ACTION_DEBUG_INSTALL_TOOLPKG = "com.ai.assistance.operit.DEBUG_INSTALL_TOOLPKG"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_RESET_SUBPACKAGE_STATES = "reset_subpackage_states"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEBUG_INSTALL_TOOLPKG) {
            return
        }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)?.trim().orEmpty()
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)?.trim().orEmpty()
        val resetSubpackageStates =
            intent.getBooleanExtra(EXTRA_RESET_SUBPACKAGE_STATES, true)

        if (packageName.isBlank() || filePath.isBlank()) {
            AppLogger.e(
                TAG,
                "Missing required parameters: packageName=$packageName, filePath=$filePath"
            )
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            installToolPkg(
                context = context,
                packageName = packageName,
                filePath = filePath,
                resetSubpackageStates = resetSubpackageStates
            )
        }
    }

    private fun installToolPkg(
        context: Context,
        packageName: String,
        filePath: String,
        resetSubpackageStates: Boolean
    ) {
        AppLogger.d(
            TAG,
            "Starting debug toolpkg install: package=$packageName, filePath=$filePath, resetSubpackageStates=$resetSubpackageStates"
        )

        try {
            val aiToolHandler = AIToolHandler.getInstance(context)
            val packageManager = PackageManager.getInstance(context, aiToolHandler)
            val result =
                packageManager.installDebugToolPkg(
                    containerPackageName = packageName,
                    externalFilePath = filePath,
                    resetSubpackageStatesToManifest = resetSubpackageStates
                )
            AppLogger.d(TAG, result)
        } catch (error: Exception) {
            AppLogger.e(
                TAG,
                "Debug toolpkg install failed: package=$packageName, filePath=$filePath",
                error
            )
        }
    }
}
