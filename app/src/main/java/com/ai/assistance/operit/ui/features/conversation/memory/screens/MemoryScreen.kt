package com.ai.assistance.operit.ui.features.conversation.memory.screens

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.ai.assistance.operit.ui.features.conversation.memory.workbench.MemoryLibraryPage

@Composable
fun MemoryScreen(spaceId: String, onConfigureEmbedding: () -> Unit, initialList: Boolean = false, initialMemoryUuid: String? = null) {
    var viewedSpace by rememberSaveable(spaceId) { mutableStateOf(spaceId) }
    // 切换查看空间时隔离草稿、文件选择器和协程，不改变全局默认。
    key(viewedSpace) {
        MemoryLibraryPage(viewedSpace, { viewedSpace = it }, onConfigureEmbedding, initialList && viewedSpace == spaceId, initialMemoryUuid.takeIf { viewedSpace == spaceId })
    }
}
