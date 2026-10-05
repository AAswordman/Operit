package com.ai.assistance.operit.core.tools.permissions

import android.content.Context
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.shell.ShellExecutorFactory
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 在一次工具执行会话内缓存授权快照，下一轮对话开始时再重新查询。 */
object PermissionCapabilityResolver {
    @Volatile
    private var fallbackSession = PermissionCapabilitySession()
    private val sessionThreadLocal = ThreadLocal<PermissionCapabilitySession?>()

    /** 创建独立的消息工具会话，不覆盖未显式绑定入口使用的会话。 */
    internal fun beginSession(): PermissionCapabilitySession {
        return PermissionCapabilitySession()
    }

    /** 用户显式修改权限模式后，立即让下一次工具调用重新建立快照。 */
    internal fun invalidateSession() {
        fallbackSession = PermissionCapabilitySession()
    }

    /** 在工具执行协程中绑定当前对话的权限快照。 */
    internal suspend fun <T> withSession(
        session: PermissionCapabilitySession,
        block: suspend () -> T
    ): T = withContext(sessionThreadLocal.asContextElement(session)) {
        block()
    }

    private fun currentSession(): PermissionCapabilitySession =
        sessionThreadLocal.get() ?: fallbackSession

    fun shellSnapshot(
        context: Context,
        mode: AndroidPermissionLevel? = null
    ): PermissionCapabilities {
        val session = currentSession()
        session.cachedShell(mode)?.let { return it }
        return synchronized(session) {
            val resolvedMode = mode ?: androidPermissionPreferences.getPreferredPermissionLevel()
                ?: AndroidPermissionLevel.STANDARD
            session.cachedShell(resolvedMode)?.let { return@synchronized it }
            readShellSnapshot(context, resolvedMode).also(session::cacheShell)
        }
    }

    /** 读取界面状态时使用独立快照，避免提前占用工具执行会话的能力结果。 */
    fun statusShellSnapshot(
        context: Context,
        mode: AndroidPermissionLevel
    ): PermissionCapabilities = readShellSnapshot(context, mode)

    suspend fun uiSnapshot(
        context: Context,
        mode: AndroidPermissionLevel? = null
    ): PermissionCapabilities {
        val session = currentSession()
        session.cachedUi(mode)?.let { return it }
        return session.uiSnapshotMutex.withLock {
            session.cachedUi(mode)?.let { return@withLock it }
            val shell = shellSnapshot(context, mode)
            val snapshot = shell.copy(
                accessibilityAvailable = shell.mode != AndroidPermissionLevel.STANDARD &&
                    UIHierarchyManager.isAccessibilityServiceEnabled(context)
            )
            session.cacheUi(snapshot)
            snapshot
        }
    }

    /** 读取界面状态时查询无障碍，不写入工具执行会话。 */
    suspend fun statusUiSnapshot(
        context: Context,
        mode: AndroidPermissionLevel
    ): PermissionCapabilities = readShellSnapshot(context, mode).copy(
        accessibilityAvailable = mode != AndroidPermissionLevel.STANDARD &&
            UIHierarchyManager.isAccessibilityServiceEnabled(context)
    )

    private fun readShellSnapshot(
        context: Context,
        mode: AndroidPermissionLevel
    ): PermissionCapabilities {
        val shizukuRunning = ShizukuAuthorizer.isShizukuServiceRunning()
        val shizukuGranted = shizukuRunning && ShizukuAuthorizer.hasShizukuPermission()
        val rootAvailable = if (mode == AndroidPermissionLevel.ROOT) {
            val executor = ShellExecutorFactory.getBackendExecutor(context, PermissionBackend.ROOT)
            executor.hasPermission().granted
        } else false
        return PermissionCapabilities(
            mode = mode,
            shizukuRunning = shizukuRunning,
            shizukuGranted = shizukuGranted,
            rootAvailable = rootAvailable
        )
    }
}
