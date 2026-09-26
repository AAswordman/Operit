package com.ai.assistance.operit.data.recovery

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants

/** 负责识别和交接默认应用进程，避免救援进程误操作仍在使用中的数据库。 */
internal object MainProcessController {
    const val ACTION_STOP_MAIN_PROCESS =
        "com.ai.assistance.operit.action.STOP_MAIN_PROCESS_FOR_RECOVERY"

    enum class State {
        RUNNING,
        NOT_RUNNING,
        UNKNOWN
    }

    data class ProcessInfo(
        val pid: Int,
        val processName: String,
        val uid: Int,
        val importance: Int
    )

    data class Inspection(
        val state: State,
        val process: ProcessInfo? = null
    )

    fun inspect(context: Context): Inspection {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return Inspection(State.UNKNOWN)
        val processes = activityManager.runningAppProcesses ?: return Inspection(State.UNKNOWN)
        val mainProcess =
            processes
                .asSequence()
                .filter { process ->
                    process.pid != Process.myPid() &&
                        process.uid == Process.myUid() &&
                        process.processName == context.packageName &&
                        isProcessAlive(process.pid)
                }
                .map { process ->
                    ProcessInfo(
                        pid = process.pid,
                        processName = process.processName,
                        uid = process.uid,
                        importance = process.importance
                    )
                }
                .firstOrNull()
        return if (mainProcess == null) {
            Inspection(State.NOT_RUNNING)
        } else {
            Inspection(State.RUNNING, mainProcess)
        }
    }

    /** 请求默认进程关闭自身，接收器会先释放数据库再结束进程。 */
    fun requestStop(context: Context) {
        val intent =
            Intent(context, MainProcessShutdownReceiver::class.java)
                .setAction(ACTION_STOP_MAIN_PROCESS)
        context.sendBroadcast(intent)
    }

    /** 等待默认进程真正退出，避免只发出关闭请求就替换数据库文件。 */
    fun stopAndWait(context: Context, timeoutMs: Long = 5_000L): Boolean {
        val initial = inspect(context)
        when (initial.state) {
            State.NOT_RUNNING -> return true
            State.UNKNOWN -> return false
            State.RUNNING -> Unit
        }
        requestStop(context)

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (inspect(context).state == State.NOT_RUNNING) return true
            Thread.sleep(50L)
        }
        return inspect(context).state == State.NOT_RUNNING
    }

    private fun isProcessAlive(pid: Int): Boolean {
        return try {
            Os.kill(pid, 0)
            true
        } catch (error: ErrnoException) {
            error.errno != OsConstants.ESRCH
        }
    }
}
