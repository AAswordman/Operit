package com.ai.assistance.operit.data.memory.profile

import com.ai.assistance.operit.data.model.Memory

/** 入口展示数据来自对应空间，查看与角色绑定不共享可变状态。 */
data class MemorySpaceSummary(
    val memoryCount: Int,
    val linkCount: Int,
    val folderCount: Int,
    val recentMemories: List<Memory>,
)
