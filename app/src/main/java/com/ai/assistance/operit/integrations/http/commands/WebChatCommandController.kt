package com.ai.assistance.operit.integrations.http.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.integrations.http.ExternalChatHttpNetworkInfo
import com.ai.assistance.operit.integrations.http.ExternalChatHttpState
import com.ai.assistance.operit.integrations.intent.ExternalChatReceiver
import com.ai.assistance.operit.util.android.LocaleUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class WebChatCommandController(private val context: Context) {
    private val preferences = ExternalHttpApiPreferences.getInstance(context)
    private val mutex = Mutex()
    private fun text(zh: String, en: String) = if (LocaleUtils.usesChineseContent(context)) zh else en

    suspend fun execute(request: WebChatCommandRequest): String = mutex.withLock {
        request.validate()
        when (request.command) {
            "start-server" -> start(request)
            "stop-server" -> {
                preferences.setEnabled(false)
                val state = WebChatForegroundControl.request(context, start = false)
                check(!state.isRunning) { text("HTTP 服务尚未停止", "HTTP service is still running") }
                text("# HTTP 服务已停止，自动启动已关闭。", "# HTTP service stopped; automatic startup disabled.")
            }
            "status" -> describe(AIForegroundService.externalHttpState.value)
            "token-show" -> "# Bearer Token\n${preferences.ensureBearerToken()}"
            "token-reset" -> text("# 新 Bearer Token；旧 Token 不再通过后续请求鉴权。\n", "# New Bearer Token; subsequent requests with the old token are rejected.\n") + preferences.resetBearerToken()
            "examples" -> examples(request.example)
            else -> error("Unknown web-chat command")
        }
    }

    private suspend fun start(request: WebChatCommandRequest): String {
        request.port?.let { preferences.setPort(it) }
        val token = preferences.ensureBearerToken()
        preferences.setEnabled(true)
        val port = preferences.getConfig().port
        // 由前台服务回复本次请求，避免把旧状态或已保存的开关误报为监听成功。
        val state = WebChatForegroundControl.request(context, start = true)
        check(state.isRunning && state.port == port) {
            if (state.lastError != null) state.lastError else text("HTTP 服务未能启动", "HTTP service did not start")
        }
        val description = describe(state) + "\n# Bearer Token\n$token"
        if (!request.login) return description
        try {
            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webChatLoginUrl(port, token))).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException(description + "\n" + text("服务已启动，但无法打开默认浏览器：", "Service started, but the default browser could not be opened: ") + error.message)
        }
        return description + "\n" + text("# 已请求默认浏览器打开本机自动登录链接。", "# Requested the default browser to open the local login link.")
    }

    private suspend fun describe(state: ExternalChatHttpState): String {
        val config = preferences.getConfig()
        val port = if (state.isRunning) requireNotNull(state.port) else config.port
        return buildString {
            appendLine(text("# 状态：", "# Status: ") + if (state.isRunning) text("运行中", "running") else text("未运行", "stopped"))
            appendLine(text("# 自动启动：", "# Automatic startup: ") + if (config.enabled) text("启用", "enabled") else text("停用", "disabled"))
            appendLine("# 0.0.0.0:$port — HTTP Chat / Web Chat / A2A")
            appendLine(text("# 本机网页 URL", "# Local web URL"))
            appendLine("http://127.0.0.1:$port/")
            ExternalChatHttpNetworkInfo.getLocalIpv4Addresses().forEach { address ->
                appendLine(text("# 当前网络网页 URL", "# Current network web URL"))
                appendLine("http://$address:$port/")
            }
            appendLine("# A2A Agent Card")
            appendLine("http://127.0.0.1:$port/.well-known/agent-card.json")
            appendLine("# A2A JSON-RPC")
            append("http://127.0.0.1:$port/a2a")
            if (state.lastError != null) append("\n# " + state.lastError)
        }
    }

    private fun shell(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

    private suspend fun examples(kind: String?): String {
        val port = preferences.getConfig().port
        val token = preferences.ensureBearerToken()
        val base = "http://127.0.0.1:$port"
        val authorization = shell("Authorization: Bearer $token")
        val chat = shell("$base/api/external-chat")
        val header = text("# 以下仅输出示例，不会发送请求。远程调用时将 127.0.0.1 换成 status 中的设备地址。", "# Examples only; no request is sent. For remote calls, replace 127.0.0.1 with a device address from status.")
        val kinds = if (kind == null) listOf("sync", "async", "health", "intent") else listOf(kind)
        return header + "\n\n" + kinds.joinToString("\n\n") { selected ->
            when (selected) {
                "sync" -> text("# 同步调用", "# Synchronous call") + "\n" +
                    "curl -X POST $chat \\\n  -H $authorization \\\n  -H 'Content-Type: application/json; charset=utf-8' \\\n  -d " + shell("""{"message":"你好","response_mode":"sync","show_floating":true,"initial_mode":"WINDOW","return_tool_status":false}""")
                "async" -> text("# 异步回调；请替换 YOUR_PC 为接收回调的地址。", "# Async callback; replace YOUR_PC with your callback receiver.") + "\n" +
                    "curl -X POST $chat \\\n  -H $authorization \\\n  -H 'Content-Type: application/json; charset=utf-8' \\\n  -d " + shell("""{"message":"你好","response_mode":"async_callback","callback_url":"http://YOUR_PC:8080/callback"}""")
                "health" -> text("# 健康检查", "# Health check") + "\n" +
                    "curl -H $authorization " + shell("$base/api/health")
                "intent" -> text("# Intent 广播；在 adb 终端中执行，替换回传目标包名。", "# Intent broadcast; run from an adb terminal and replace the reply package.") + "\n" +
                    "# ${ExternalChatReceiver.ACTION_EXTERNAL_CHAT_RESULT}\n" +
                    "adb shell am broadcast \\\n  -a ${ExternalChatReceiver.ACTION_EXTERNAL_CHAT} \\\n  -p ${shell(context.packageName)} \\\n  --es request_id 'req-001' \\\n  --es message '你好' \\\n  --ez show_floating true \\\n  --ez return_tool_status false \\\n  --es initial_mode 'WINDOW' \\\n  --es reply_package 'YOUR.APP.PACKAGE'"
                else -> error("Unknown example type")
            }
        }
    }
}
