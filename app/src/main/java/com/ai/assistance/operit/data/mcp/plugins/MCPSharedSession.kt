package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.Terminal
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MCP 共享终端会话管理器
 * 
 * 用于在 MCPStarter 和 MCPDeployer 之间共享同一个终端会话
 * 避免重复创建会话，提高资源利用效率
 */
object MCPSharedSession {
    
    private const val TAG = "MCPSharedSession"
    private const val SESSION_NAME = "mcp-shared"
    
    @Volatile
    private var sharedSessionId: String? = null
    private val mutex = Mutex()
    
    /**
     * 获取或创建共享的终端会话
     * 
     * @param context Android上下文
     * @return 会话ID，如果创建失败返回null
     */
    suspend fun getOrCreateSharedSession(context: Context): String? {
        val terminal = Terminal.getInstance(context)

        // 快速检查，避免不必要的锁定。这里必须同时校验会话是否仍然存活：终端的 PTY 进程
        // 异常退出后会话 ID 仍会被保留，如果直接复用这个已失效的 ID，所有基于它的命令都
        // 不会收到完成事件，MCP 桥接器的部署/启动流程就会永久挂起。
        sharedSessionId?.let { existing ->
            if (isSessionAlive(terminal, existing)) return existing
            AppLogger.w(TAG, "缓存的共享会话 $existing 已不存在，将重新创建")
            sharedSessionId = null
        }
        
        // 使用互斥锁来确保线程安全，避免在synchronized块中调用suspend函数
        return mutex.withLock {
            // 再次检查，防止在等待锁的过程中其他线程已经创建了会话
            sharedSessionId?.let { existing ->
                if (isSessionAlive(terminal, existing)) return@withLock existing
                AppLogger.w(TAG, "缓存的共享会话 $existing 已不存在，将重新创建")
                sharedSessionId = null
            }
            
            if (!terminal.isConnected()) {
                if (!terminal.initialize()) {
                    AppLogger.e(TAG, "Failed to initialize Terminal")
                    return@withLock null
                }
            }
            
            // 创建共享会话
            val sessionId = terminal.createSession(SESSION_NAME)
            if (sessionId == null) {
                AppLogger.e(TAG, "Failed to create shared session or session initialization timeout")
            } else {
                AppLogger.d(TAG, "Created shared MCP session: $sessionId")
                sharedSessionId = sessionId
            }
            
            sessionId
        }
    }

    /** 会话 ID 是否仍存在于终端会话表中。 */
    private fun isSessionAlive(terminal: Terminal, sessionId: String): Boolean {
        return try {
            terminal.terminalState.value.sessions.any { it.id == sessionId }
        } catch (e: Exception) {
            // 无法判断时按"存活"处理，避免因为一次读取失败而反复重建会话。
            AppLogger.w(TAG, "校验会话 $sessionId 存活状态失败: ${e.message}")
            true
        }
    }
    
    /**
     * 获取当前共享会话ID（如果存在）
     */
    fun getCurrentSessionId(): String? = sharedSessionId
    
    /**
     * 清除共享会话引用
     * 注意：这不会实际关闭会话，只是清除引用
     */
    suspend fun clearSession() {
        mutex.withLock {
            AppLogger.d(TAG, "Clearing shared session reference: $sharedSessionId")
            sharedSessionId = null
        }
    }
    
    /**
     * 检查共享会话是否存在
     */
    fun hasActiveSession(): Boolean = sharedSessionId != null
} 