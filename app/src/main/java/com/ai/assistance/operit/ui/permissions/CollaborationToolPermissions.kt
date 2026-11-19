package com.ai.assistance.operit.ui.permissions

import com.ai.assistance.operit.core.agent.collaboration.CollaborationTools

internal fun effectiveToolPermission(name: String, master: PermissionLevel, override: PermissionLevel?): PermissionLevel =
    override ?: if (master == PermissionLevel.ASK && CollaborationTools.isCollaborationTool(name)) PermissionLevel.ALLOW else master
