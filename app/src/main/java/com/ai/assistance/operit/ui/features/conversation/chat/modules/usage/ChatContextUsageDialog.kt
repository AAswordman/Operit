package com.ai.assistance.operit.ui.features.conversation.chat.modules.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.context.ContextWindowBreakdown
import com.ai.assistance.operit.core.chat.context.TokenUsageBreakdown
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException

private enum class ContextUsagePage { WINDOW, CUMULATIVE }

private sealed interface ContextWindowLoadState {
    data object Loading : ContextWindowLoadState
    data object NoConversation : ContextWindowLoadState
    data object Failed : ContextWindowLoadState
    data class Loaded(val breakdown: ContextWindowBreakdown) : ContextWindowLoadState
}

/** 两个页签分别展示请求窗口估算与当前会话的历史累计用量。 */
@Composable
fun ChatContextUsageDialog(actualViewModel: ChatViewModel, onDismiss: () -> Unit) {
    val chatId by actualViewModel.currentChatId.collectAsState()
    val perRequestTokens by actualViewModel.perRequestTokenCount.collectAsState()
    val maxWindowSizeInK by actualViewModel.maxWindowSizeInK.collectAsState()
    val summaryThreshold by actualViewModel.summaryTokenThreshold.collectAsState()
    val enableSummary by actualViewModel.enableSummary.collectAsState()
    val isLoading by actualViewModel.currentChatIsLoading.collectAsState()
    val isSummarizing by actualViewModel.isSummarizing.collectAsState()
    val isSendTriggeredSummarizing by actualViewModel.isSendTriggeredSummarizing.collectAsState()
    val history by actualViewModel.chatHistory.collectAsState()
    val chats by actualViewModel.chatHistories.collectAsState()
    val usageChat = chats.firstOrNull { it.id == chatId }
    val cumulativeUsage = remember(usageChat) {
        usageChat?.let { chat -> runCatching {
            TokenUsageBreakdown.fromStored(chat.usageBreakdownJson, chat.inputTokens, chat.outputTokens)
        } }
    }
    val summarizing = isSummarizing || isSendTriggeredSummarizing
    val stableHistory = if (isLoading || summarizing) null else history
    var page by remember(chatId) { mutableStateOf(ContextUsagePage.WINDOW) }
    var loadState by remember(chatId) { mutableStateOf<ContextWindowLoadState>(ContextWindowLoadState.Loading) }
    var refreshVersion by remember(chatId) { mutableIntStateOf(0) }

    // 切换会话或结束生成、总结后重读；离开弹窗会取消估算，不保留其他会话的结果。
    LaunchedEffect(actualViewModel, chatId, maxWindowSizeInK, summaryThreshold, enableSummary,
        isLoading, summarizing, stableHistory, refreshVersion) {
        val targetChatId = chatId
        if (targetChatId == null) {
            loadState = ContextWindowLoadState.NoConversation
            return@LaunchedEffect
        }
        loadState = ContextWindowLoadState.Loading
        try {
            loadState = ContextWindowLoadState.Loaded(
                actualViewModel.loadContextWindowBreakdown(targetChatId)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("ChatContextUsageDialog", "加载上下文统计失败", e)
            loadState = ContextWindowLoadState.Failed
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(0.92f).heightIn(max = 640.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.context_usage_dialog_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .selectableGroup().padding(4.dp)
                ) {
                    ContextUsagePage.entries.forEach { item ->
                        val selected = item == page
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .selectable(selected = selected, role = Role.Tab, onClick = { page = item })
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(if (item == ContextUsagePage.WINDOW)
                                    R.string.context_usage_window_tab else R.string.context_usage_cumulative_tab),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                key(page) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()).padding(top = 16.dp)
                    ) {
                        when (page) {
                            ContextUsagePage.CUMULATIVE -> when {
                                chatId == null -> Text(stringResource(R.string.context_panel_no_conversation))
                                cumulativeUsage == null -> CircularProgressIndicator()
                                cumulativeUsage.isFailure -> Text(stringResource(R.string.context_panel_unavailable))
                                else -> CumulativeUsageContent(cumulativeUsage.getOrThrow())
                            }
                            ContextUsagePage.WINDOW -> when (val state = loadState) {
                                ContextWindowLoadState.Loading -> Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text(stringResource(R.string.context_panel_loading), style = MaterialTheme.typography.bodySmall)
                                }
                                ContextWindowLoadState.NoConversation -> Text(
                                    stringResource(R.string.context_panel_no_conversation),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                ContextWindowLoadState.Failed -> Column {
                                    Text(stringResource(R.string.context_panel_unavailable), style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { refreshVersion++ }) {
                                        Text(stringResource(R.string.context_panel_retry))
                                    }
                                }
                                is ContextWindowLoadState.Loaded -> ContextWindowUsageContent(
                                    breakdown = state.breakdown,
                                    lastRequestInputTokens = perRequestTokens?.first,
                                    canSummarize = !isLoading && !summarizing && history.isNotEmpty(),
                                    isSummarizing = summarizing,
                                    onSummarizeNow = actualViewModel::manuallySummarizeConversation
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
