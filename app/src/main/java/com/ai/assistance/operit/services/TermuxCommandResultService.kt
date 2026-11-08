package com.ai.assistance.operit.services

import android.app.IntentService
import android.content.Intent
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.core.tools.system.shell.AndroidShellExecutor.CommandResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Termux命令结果服务
 * 用于接收来自Termux的命令执行结果
 */
class TermuxCommandResultService : IntentService("TermuxCommandResultService") {
    companion object {
        private const val TAG = "TermuxResultService"
        const val EXTRA_EXECUTION_ID = "execution_id"

        // 用于在服务内注册回调的Map
        private val callbackMap = mutableMapOf<Int, ((CommandResult) -> Unit)>()
        
        /**
         * 注册命令执行回调
         * @param executionId 执行ID
         * @param callback 回调函数
         */
        fun registerCallback(executionId: Int, callback: (CommandResult) -> Unit) {
            callbackMap[executionId] = callback
            AppLogger.d(TAG, "已注册回调，ID: $executionId, 当前回调数: ${callbackMap.size}")
        }
        
        /**
         * 移除命令执行回调
         * @param executionId 执行ID
         */
        fun removeCallback(executionId: Int) {
            callbackMap.remove(executionId)
            AppLogger.d(TAG, "已移除回调，ID: $executionId, 当前回调数: ${callbackMap.size}")
        }
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        
        // 获取执行ID
        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, -1)
        // AppLogger.d(TAG, "收到命令结果，执行ID: $executionId")
        
        if (executionId == -1) {
            AppLogger.e(TAG, "无效的执行ID")
            return
        }
        
        // 获取结果Bundle
        val resultBundle = intent.getBundleExtra("result")
        if (resultBundle == null) {
            AppLogger.e(TAG, "结果Bundle为空")
            return
        }
        
        // 解析结果
        val stdout = resultBundle.getString("stdout", "")
        val stderr = resultBundle.getString("stderr", "")
        val exitCode = resultBundle.getInt("exitCode", -1)
        val errmsg = resultBundle.getString("errmsg", "")
        
        // AppLogger.d(TAG, "命令执行结果: stdout长度=${stdout.length}, stderr长度=${stderr.length}, exitCode=$exitCode")
        
        // 构建结果对象
        val result = CommandResult(
            success = exitCode == 0,
            stdout = stdout,
            stderr = stderr,
            exitCode = exitCode
        )
        
        // 调用回调
        val callback = callbackMap[executionId]
        if (callback != null) {
            serviceScope.launch {
                callback(result)
                // 执行完成后移除回调
                removeCallback(executionId)
            }
        } else {
            // AppLogger.w(TAG, "未找到ID为 $executionId 的回调")
        }
    }
} 