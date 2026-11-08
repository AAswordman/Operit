package com.ai.assistance.operit.ui.features.management.databackup

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.databackup.components.DataBackupCard
import com.ai.assistance.operit.ui.features.management.databackup.components.DataBackupRow
import com.ai.assistance.operit.ui.features.management.settings.screens.ChatBackupSettingsScreen
import com.ai.assistance.operit.ui.features.management.settings.screens.ChatHistorySettingsScreen
import com.ai.assistance.operit.ui.features.management.databackup.logcat.LogcatScreen
import kotlinx.coroutines.launch

internal data class DataBackupOverview(
    val chats: Int = 0,
    val characters: Int = 0,
    val memories: Int = 0,
    val links: Int = 0,
    val profileName: String = "",
)

/** 统计详情仍使用原路由，三个数据页签只调整组织和交互。 */
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
fun DataAndBackupScreen(onNavigateToTokenStatistics: () -> Unit, initialTab: Int = 0) {
    val pager = rememberPagerState(initialPage = initialTab, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var overview by remember { mutableStateOf(DataBackupOverview()) }
    val tabs =
        listOf(
            stringResource(R.string.data_backup_tab_restore),
            stringResource(R.string.data_backup_tab_chats),
            stringResource(R.string.data_backup_tab_logs),
        )
    val header: @Composable () -> Unit = {
        DataOverviewCard(overview, onNavigateToTokenStatistics)
        TabRow(
            selectedTabIndex = pager.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.scrollToPage(index) } },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
    }
    HorizontalPager(
        state = pager,
        beyondViewportPageCount = 2,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) { page ->
        // 三个页签保持组合，切换时保留选择、滚动位置和正在执行的任务。
        when (page) {
            0 -> ChatBackupSettingsScreen(header = header, onOverviewChanged = { overview = it })
            1 -> ChatHistorySettingsScreen(header = header)
            2 -> LogcatScreen(header = header)
        }
    }
}

@Composable
private fun DataOverviewCard(overview: DataBackupOverview, onNavigateToTokens: () -> Unit) {
    DataBackupCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.data_backup_overview),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.data_backup_current_profile, overview.profileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val stats =
                    listOf(
                        overview.chats to R.string.data_backup_count_chats,
                        overview.characters to R.string.data_backup_count_characters,
                        overview.memories to R.string.data_backup_count_memories,
                        overview.links to R.string.data_backup_count_links,
                    )
                stats.forEachIndexed { index, (count, label) ->
                    if (index > 0)
                        VerticalDivider(
                            Modifier.height(44.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        DataBackupRow(
            title = stringResource(R.string.settings_token_usage_stats),
            subtitle = stringResource(R.string.data_backup_token_entry_desc),
            icon = Icons.Default.Analytics,
            onClick = onNavigateToTokens,
        )
    }
}
