package com.ai.assistance.operit.data.recovery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.util.AppLogger
import kotlin.system.exitProcess

/** 在默认进程内释放运行时资源，然后退出，供数据库救援流程安全接管文件。 */
class MainProcessShutdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != MainProcessController.ACTION_STOP_MAIN_PROCESS) return

        val appContext = context.applicationContext
        AppLogger.i(TAG, "收到数据库救援的主进程关闭请求，pid=${Process.myPid()}")
        runCatching {
            appContext.stopService(Intent(appContext, AIForegroundService::class.java))
        }.onFailure { error ->
            AppLogger.w(TAG, "停止前台服务失败，继续关闭主进程", error)
        }
        runCatching { AppDatabase.closeDatabase() }
            .onFailure { error -> AppLogger.w(TAG, "关闭 Room 失败，继续关闭主进程", error) }
        runCatching { ObjectBoxManager.closeAll() }
            .onFailure { error -> AppLogger.w(TAG, "关闭 ObjectBox 失败，继续关闭主进程", error) }

        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    companion object {
        private const val TAG = "MainProcessShutdown"
    }
}
