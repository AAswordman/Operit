package com.ai.assistance.operit.ui.features.conversation.memory.workbench

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** 控制区占用外层分配的固定比例；窄屏采用图标，大字体仍可滚动访问全部操作。 */
@Composable
internal fun MemoryLibraryControls(
    modifier: Modifier,
    spaceControls: @Composable (compact: Boolean) -> Unit,
    searchControls: @Composable () -> Unit,
    folderControls: @Composable (compact: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < 480.dp * LocalDensity.current.fontScale
        val minimumHeight = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minimumHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            spaceControls(compact)
            searchControls()
            folderControls(compact)
        }
    }
}