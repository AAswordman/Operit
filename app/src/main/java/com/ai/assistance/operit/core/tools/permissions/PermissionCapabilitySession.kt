package com.ai.assistance.operit.core.tools.permissions

import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import kotlinx.coroutines.sync.Mutex

/** 保存一次工具执行会话内的授权能力快照，避免每个工具重复探测系统授权。 */
internal class PermissionCapabilitySession {
    internal val uiSnapshotMutex = Mutex()
    private var shell: PermissionCapabilities? = null
    private var ui: PermissionCapabilities? = null

    @Synchronized
    fun begin() {
        shell = null
        ui = null
    }

    @Synchronized
    fun cachedShell(mode: AndroidPermissionLevel?): PermissionCapabilities? {
        val snapshot = shell ?: return null
        return if (mode == null || snapshot.mode == mode) snapshot else null
    }

    @Synchronized
    fun cacheShell(snapshot: PermissionCapabilities) {
        shell = snapshot
        ui = null
    }

    @Synchronized
    fun cachedUi(mode: AndroidPermissionLevel?): PermissionCapabilities? {
        val snapshot = ui ?: return null
        return if (mode == null || snapshot.mode == mode) snapshot else null
    }

    @Synchronized
    fun cacheUi(snapshot: PermissionCapabilities) {
        shell = snapshot
        ui = snapshot
    }
}
