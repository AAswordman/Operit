package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.tools.ToolPermissionModeResultData
import com.ai.assistance.operit.ui.permissions.PermissionLevel

internal object ToolPermissionModeSupport {
    fun parse(rawMode: String?): PermissionLevel? {
        return when (rawMode?.trim()) {
            PermissionLevel.ALLOW.name -> PermissionLevel.ALLOW
            PermissionLevel.ASK.name -> PermissionLevel.ASK
            PermissionLevel.FORBID.name -> PermissionLevel.FORBID
            else -> null
        }
    }

    fun result(
            current: PermissionLevel,
            previous: PermissionLevel? = null
    ): ToolPermissionModeResultData {
        return ToolPermissionModeResultData(
                permissionLevel = current.name,
                previousPermissionLevel = previous?.name,
                changed = previous != null && previous != current
        )
    }
}
