package com.ai.assistance.operit.integrations.http.commands

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Process
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

object WebChatCommandServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: LocalServerSocket? = null

    @Synchronized
    fun ensureStarted(context: Context): String {
        val name = "operit.web-chat.${Process.myUid()}"
        if (listener != null) return name
        val server = LocalServerSocket(name)
        val controller = WebChatCommandController(context.applicationContext)
        listener = server
        scope.launch {
            try {
                while (isActive) {
                    val socket = server.accept()
                    scope.launch {
                        try {
                            socket.use { handle(it, controller) }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            AppLogger.w("WebChatCommand", "终端控制连接已结束：${error.javaClass.simpleName}")
                        }
                    }
                }
            } catch (error: IOException) {
                AppLogger.e("WebChatCommand", "终端控制监听已结束", error)
            } finally {
                try {
                    server.close()
                } catch (error: IOException) {
                    AppLogger.w("WebChatCommand", "终端控制监听关闭失败")
                }
                synchronized(this@WebChatCommandServer) {
                    if (listener === server) listener = null
                }
            }
        }
        return name
    }

    private suspend fun handle(socket: LocalSocket, controller: WebChatCommandController) {
        socket.soTimeout = 30_000
        val response = try {
            // 普通 proot 使用应用 UID；真实 root chroot 已有应用沙箱访问权。
            check(trustedWebChatPeer(socket.peerCredentials.uid, Process.myUid())) { "Permission denied" }
            val request = webChatCommandJson.decodeFromString<WebChatCommandRequest>(readWebChatCommand(socket.inputStream))
            WebChatCommandResponse(success = true, output = controller.execute(request))
        } catch (error: TimeoutCancellationException) {
            WebChatCommandResponse(false, "HTTP service control timed out")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 不将请求、Token 或自动登录 URL 写入日志。
            AppLogger.w("WebChatCommand", "终端控制请求失败：${error.javaClass.simpleName}")
            WebChatCommandResponse(false, error.toString())
        }
        try {
            socket.outputStream.write((webChatCommandJson.encodeToString(response) + "\n").toByteArray(Charsets.UTF_8))
            socket.outputStream.flush()
        } catch (error: IOException) {
            AppLogger.w("WebChatCommand", "终端控制客户端已断开")
        }
    }
}
