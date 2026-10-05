package com.ai.assistance.operit.core.tools.permissions

import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShellIdentity

/** 执行后端与用户模式分开，新增授权来源不需要新增用户模式。 */
enum class PermissionBackend { STANDARD, ACCESSIBILITY, SHIZUKU, ROOT }

data class PermissionCapabilities(
    val mode: AndroidPermissionLevel,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val accessibilityAvailable: Boolean = false,
    val rootAvailable: Boolean = false
) {
    val canUseShizuku: Boolean
        get() = mode != AndroidPermissionLevel.STANDARD && shizukuRunning && shizukuGranted
    val canUseAccessibility: Boolean
        get() = mode != AndroidPermissionLevel.STANDARD && accessibilityAvailable
    val canUseRoot: Boolean
        get() = mode == AndroidPermissionLevel.ROOT && rootAvailable
    val hasPrivilegedShell: Boolean
        get() = canUseRoot || canUseShizuku

    val shellBackend: PermissionBackend
        get() = when {
            canUseRoot -> PermissionBackend.ROOT
            canUseShizuku -> PermissionBackend.SHIZUKU
            else -> PermissionBackend.STANDARD
        }

    fun shellBackendFor(identity: ShellIdentity): PermissionBackend? = when (identity) {
        ShellIdentity.DEFAULT -> shellBackend
        ShellIdentity.APP -> PermissionBackend.STANDARD
        ShellIdentity.ROOT -> if (canUseRoot) PermissionBackend.ROOT else null
        ShellIdentity.SHELL -> if (hasPrivilegedShell) shellBackend else null
    }

    fun uiBackend(hasExplicitDisplay: Boolean = false): PermissionBackend = when {
        canUseRoot -> PermissionBackend.ROOT
        !hasExplicitDisplay && canUseAccessibility -> PermissionBackend.ACCESSIBILITY
        else -> shellBackend
    }
}
