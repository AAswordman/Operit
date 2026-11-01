package com.ai.assistance.operit.ui.common.dialogs

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import kotlinx.coroutines.launch

/** 以当前页的实际内容决定高度，窗口只提供上限，标题与操作区保持可见。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentSizedTabbedDialog(
    title: String,
    tabTitles: List<String>,
    pagerState: PagerState,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
    showCloseButton: Boolean = true,
    content: @Composable ColumnScope.(Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pageHeights = remember(pagerState, tabTitles.size) {
        mutableStateListOf<Int>().apply { repeat(tabTitles.size) { add(0) } }
    }
    val scrollStates = key(pagerState) { List(tabTitles.size) { rememberScrollState() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // 用完整窗口提供稳定上限，避免异步加载后的页面被旧窗口高度截断。
        Box(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Layout(
                    modifier = Modifier.fillMaxWidth(),
                    content = {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (showCloseButton) {
                                    IconButton(onClick = onDismiss) {
                                        Icon(Icons.Default.Close, stringResource(R.string.close))
                                    }
                                }
                            }
                            TabRow(
                                selectedTabIndex = pagerState.currentPage,
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.primary,
                                indicator = { positions ->
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(positions[pagerState.currentPage])
                                            .wrapContentSize(Alignment.BottomCenter)
                                            .width(24.dp)
                                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                                        height = 3.dp,
                                    )
                                },
                                divider = {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                },
                            ) {
                                tabTitles.forEachIndexed { index, label ->
                                    Tab(
                                        selected = pagerState.currentPage == index,
                                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                        selectedContentColor = MaterialTheme.colorScheme.primary,
                                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        text = { Text(label) },
                                    )
                                }
                            }
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth(),
                            beyondViewportPageCount = tabTitles.lastIndex,
                            verticalAlignment = Alignment.Top,
                            key = { it },
                        ) { page ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .verticalScroll(scrollStates[page])
                                    // 滚动容器以无限高度测量内部内容，记录的是完整页高。
                                    .onSizeChanged { pageHeights[page] = it.height }
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) { content(page) }
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) { actions() }
                    },
                ) { measurables, constraints ->
                    val loose = constraints.copy(minWidth = 0, minHeight = 0)
                    val header = measurables[0].measure(loose)
                    val footer = measurables[2].measure(
                        loose.copy(maxHeight = (constraints.maxHeight - header.height).coerceAtLeast(0))
                    )
                    val bodyLimit = (constraints.maxHeight - header.height - footer.height).coerceAtLeast(0)
                    val naturalHeight = pageHeights[pagerState.currentPage]
                    // 初次布局先测完整页；取得内容高度后，不再把短页撑到窗口上限。
                    val bodyHeight = if (naturalHeight == 0) bodyLimit else naturalHeight.coerceAtMost(bodyLimit)
                    val body = measurables[1].measure(loose.copy(minHeight = bodyHeight, maxHeight = bodyHeight))
                    val height = header.height + body.height + footer.height
                    layout(constraints.maxWidth, height) {
                        header.placeRelative(0, 0)
                        body.placeRelative(0, header.height)
                        footer.placeRelative(0, header.height + body.height)
                    }
                }
            }
        }
    }
}
