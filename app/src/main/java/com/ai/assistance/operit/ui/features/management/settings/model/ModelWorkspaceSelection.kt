package com.ai.assistance.operit.ui.features.management.settings.model

/** 保持正在编辑的有效选择；进入分组时优先定位实际绑定，再恢复该组编辑记忆。 */
internal fun resolveWorkspaceConfig(
    candidateIds: List<String>,
    currentConfigId: String?,
    activeConfigId: String?,
    rememberedConfigId: String?,
): String = listOf(currentConfigId, activeConfigId, rememberedConfigId)
    .firstOrNull { it != null && it in candidateIds } ?: candidateIds.firstOrNull().orEmpty()
