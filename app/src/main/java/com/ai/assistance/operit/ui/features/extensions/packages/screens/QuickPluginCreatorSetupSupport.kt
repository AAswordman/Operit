package com.ai.assistance.operit.ui.features.extensions.packages.screens

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val OPERIT_EDITOR_PACKAGE_NAME = "operit_editor"
private const val SANDBOX_PACKAGE_DEV_INSTALL_SCRIPT_URL =
    "https://cdn.jsdelivr.net/gh/3316891527/Operit@main/tools/sandboxpackage_dev_install_or_update.js"
private const val SANDBOX_PACKAGE_DEV_SCRIPT_RELATIVE_PATH =
    "Download/Operit/skills/SandboxPackage_DEV/scripts/install_or_update.js"

internal fun runQuickPluginCreatorSetup(
    context: Context,
    packageManager: PackageManager,
    toolHandler: AIToolHandler
): ToolResult {
    return try {
        val scriptFile = downloadSandboxPackageDevInstallScript()
        val enableMessage = packageManager.enablePackage(OPERIT_EDITOR_PACKAGE_NAME)
        if (enableMessage.startsWith("Package not found", ignoreCase = true)) {
            return ToolResult(
                toolName = "$OPERIT_EDITOR_PACKAGE_NAME:debug_run_sandbox_script",
                success = false,
                result = StringResultData(""),
                error = enableMessage
            )
        }

        val result =
            toolHandler.executeTool(
                AITool(
                    name = "$OPERIT_EDITOR_PACKAGE_NAME:debug_run_sandbox_script",
                    parameters =
                        listOf(
                            ToolParameter(
                                name = "source_path",
                                value = scriptFile.absolutePath
                            )
                        )
                )
            )

        if (!result.success) {
            ToolResult(
                toolName = result.toolName,
                success = false,
                result = StringResultData(""),
                error = result.error ?: context.getString(R.string.quick_plugin_creator_setup_failed)
            )
        } else {
            ToolResult(
                toolName = result.toolName,
                success = true,
                result = StringResultData(context.getString(R.string.quick_plugin_creator_setup_success))
            )
        }
    } catch (e: Exception) {
        AppLogger.e("QuickPluginCreatorSetup", "Failed to run quick plugin creator setup", e)
        ToolResult(
            toolName = "$OPERIT_EDITOR_PACKAGE_NAME:debug_run_sandbox_script",
            success = false,
            result = StringResultData(""),
            error = e.message ?: e.javaClass.simpleName
        )
    }
}

private fun downloadSandboxPackageDevInstallScript(): File {
    val rootDir = Environment.getExternalStorageDirectory()
    val scriptFile = File(rootDir, SANDBOX_PACKAGE_DEV_SCRIPT_RELATIVE_PATH)
    scriptFile.parentFile?.mkdirs()

    val connection =
        (URL(SANDBOX_PACKAGE_DEV_INSTALL_SCRIPT_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            doInput = true
            setRequestProperty("User-Agent", "Operit-QuickPluginCreator/1.0")
        }

    connection.connect()
    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        throw IllegalStateException("HTTP ${connection.responseCode}")
    }

    BufferedInputStream(connection.inputStream).use { input ->
        FileOutputStream(scriptFile).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
            }
            output.flush()
        }
    }
    connection.disconnect()

    return scriptFile
}
