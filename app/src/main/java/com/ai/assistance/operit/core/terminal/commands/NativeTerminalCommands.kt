package com.ai.assistance.operit.core.terminal.commands

import android.content.Context
import org.json.JSONObject
import java.io.File

object NativeTerminalCommands {
    private const val ASSET_ROOT = "terminal_commands"
    private const val COMMAND_DIRECTORY = "/usr/local/bin"

    fun sessionInitializer(context: Context): String {
        val manifest = context.assets.open("$ASSET_ROOT/manifest.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText())
        }
        check(manifest.getInt("format") == 1) { "终端命令资源格式不受支持" }
        check(manifest.getString("target") == "aarch64-unknown-linux-gnu") { "终端命令架构不匹配" }
        val directory = File(context.filesDir, "terminal-commands")
        val binary = manifest.getJSONObject("binary")
        val installer = manifest.getJSONObject("installer")
        check(binary.getString("file") == "aarch64/apply_patch") { "终端命令资源路径无效" }
        check(installer.getString("file") == "install.sh") { "终端命令安装脚本路径无效" }
        val executable = NativeCommandAssets.install(File(directory, "apply_patch"), binary.getString("sha256")) {
            context.assets.open("$ASSET_ROOT/${binary.getString("file")}")
        }
        val script = NativeCommandAssets.install(File(directory, "install.sh"), installer.getString("sha256")) {
            context.assets.open("$ASSET_ROOT/${installer.getString("file")}")
        }
        // 文件目录由终端统一挂载，初始化在 Ubuntu 内执行，兼容普通会话和隐藏执行。
        return "bash ${quote(script.absolutePath)} ${quote(executable.absolutePath)} ${quote(binary.getString("sha256"))} ${quote(COMMAND_DIRECTORY)}"
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
