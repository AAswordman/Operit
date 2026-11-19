package com.ai.assistance.operit.ui.features.chat.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.ChatKind
import com.ai.assistance.operit.data.repository.SubagentRunRepository
import com.ai.assistance.operit.core.agent.SubagentCoordinator
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun useFloatingWindowLauncher(
    actualViewModel: ChatViewModel,
    permissionLauncher: ActivityResultLauncher<String>
): () -> Unit {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    return {
        actualViewModel.onFloatingButtonClick(
            FloatingMode.WINDOW,
            permissionLauncher,
            colorScheme,
            typography,
            moveTaskToBackOnReady = true
        )
    }
}

@Composable
fun ChatScreenHeader(
        modifier: Modifier = Modifier,
        actualViewModel: ChatViewModel,
        showChatHistorySelector: Boolean,
        chatHeaderTransparent: Boolean,
        chatHeaderHistoryIconColor: Int?,
        chatHeaderPipIconColor: Int?,
        onCharacterSwitcherClick: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val currentChatId by actualViewModel.currentChatId.collectAsState()
    val chatHistories by actualViewModel.chatHistories.collectAsState()
    val currentChat = remember(currentChatId, chatHistories) {
        chatHistories.firstOrNull { it.id == currentChatId }
    }
    val repository = remember(context) { SubagentRunRepository.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val currentWindowSize by actualViewModel.currentWindowSize.collectAsState()
    val maxWindowSizeInK by actualViewModel.maxWindowSizeInK.collectAsState()
    val inputTokenCount by actualViewModel.inputTokenCount.collectAsState()
    val outputTokenCount by actualViewModel.outputTokenCount.collectAsState()
    val managementParentChatId =
        if (currentChat?.chatKind == ChatKind.SUBAGENT.name) {
            currentChat.parentChatId
        } else {
            currentChat?.id
        }
    val parentRunsFlow =
        remember(managementParentChatId) {
            managementParentChatId?.let(repository::observeByParentChatId)
                ?: flowOf(emptyList())
        }
    val parentRuns by parentRunsFlow.collectAsState(initial = emptyList())
    val visibleParentRuns = remember(parentRuns) {
        parentRuns.filter { it.archivedAt == null }
    }
    val hasActiveRun = remember(visibleParentRuns) {
        visibleParentRuns.any { it.isActiveSubagentRun() }
    }
    val managementParentTitle =
        remember(managementParentChatId, chatHistories) {
            chatHistories.firstOrNull { it.id == managementParentChatId }?.title
                ?: managementParentChatId.orEmpty()
        }
    var showSubagentManager by rememberSaveable { mutableStateOf(false) }

    if (showSubagentManager && managementParentChatId != null) {
        SubagentManagementDialog(
            parentTitle = managementParentTitle,
            runs = parentRuns,
            currentChildChatId =
                currentChat?.takeIf { it.chatKind == ChatKind.SUBAGENT.name }?.id,
            onSelect = { run ->
                showSubagentManager = false
                actualViewModel.switchChat(run.childChatId, scrollToBottom = false)
            },
            onStop = { run ->
                coroutineScope.launch {
                    if (com.ai.assistance.operit.core.agent.collaboration.CollaborationCoordinator.getInstance(context).isAgent(run.childChatId)) {
                        com.ai.assistance.operit.core.agent.collaboration.CollaborationCoordinator
                            .getInstance(context).interrupt(run.parentChatId, run.childChatId)
                    } else {
                        SubagentCoordinator.getInstance(context).cancelTask(run.id)
                    }
                }
            },
            onArchive = { run ->
                coroutineScope.launch {
                    if (repository.setArchived(run.id, archived = true) &&
                        currentChatId == run.childChatId
                    ) {
                        actualViewModel.switchChat(
                            managementParentChatId,
                            scrollToBottom = false,
                        )
                    }
                }
            },
            onRestore = { run ->
                coroutineScope.launch {
                    repository.setArchived(run.id, archived = false)
                }
            },
            onDismiss = { showSubagentManager = false },
        )
    }

    LaunchedEffect(actualViewModel, context) {
        actualViewModel.moveTaskToBackEvents.collect {
            (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    if (currentChat?.chatKind == ChatKind.SUBAGENT.name) {
        val runFlow =
            remember(currentChat.id) {
                repository.observeByChildChatId(currentChat.id)
            }
        val run by runFlow.collectAsState(initial = null)
        val parentTitle =
            remember(run?.parentChatId, chatHistories) {
                chatHistories.firstOrNull { it.id == run?.parentChatId }?.title
                    ?: run?.parentChatId
                    ?: currentChat.parentChatId.orEmpty()
            }
        SubagentChatHeader(
            modifier = modifier,
            title = run?.title ?: currentChat.title,
            parentTitle = parentTitle,
            agentName = resolveSubagentAgentName(run),
            siblingRuns = parentRuns,
            currentChildChatId = currentChat.id,
            transparent = chatHeaderTransparent,
            onBackToParent = {
                (run?.parentChatId ?: currentChat.parentChatId)
                    ?.let { parentChatId ->
                        actualViewModel.switchChat(parentChatId, scrollToBottom = false)
                    }
            },
            onSwitchSubagent = { childChatId ->
                actualViewModel.switchChat(childChatId, scrollToBottom = false)
            },
            onManageSubagents = { showSubagentManager = true },
            stats = {
                TokenUsageRing(currentWindowSize, inputTokenCount, outputTokenCount, maxWindowSizeInK,
                    currentChat.id, actualViewModel::estimateConversationCost)
            },
        )
        return
    }

    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val activePrompt by activePromptManager.activePromptFlow.collectAsState(
        initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
    )
    val activeCharacterCard by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCardFlow(prompt.id)
            is ActivePrompt.CharacterGroup -> flowOf(null)
        }
    }.collectAsState(initial = null)
    val activeCharacterGroup by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterGroup -> characterGroupCardManager.getCharacterGroupCardFlow(prompt.id)
            is ActivePrompt.CharacterCard -> flowOf(null)
        }
    }.collectAsState(initial = null)
    val activeCardAvatarUri by remember(activeCharacterCard?.id) {
        activeCharacterCard?.id?.let { userPreferencesManager.getAiAvatarForCharacterCardFlow(it) }
            ?: flowOf(null)
    }.collectAsState(initial = null)
    val activeGroupAvatarUri by remember(activeCharacterGroup?.id) {
        activeCharacterGroup?.id?.let { userPreferencesManager.getAiAvatarForCharacterGroupFlow(it) }
            ?: flowOf(null)
    }.collectAsState(initial = null)
    val activeGroupFallbackMemberCardId = remember(activeCharacterGroup?.members) {
        val sortedMembers = activeCharacterGroup?.members?.sortedBy { it.orderIndex }.orEmpty()
        sortedMembers.firstOrNull()?.characterCardId
    }
    val activeGroupFallbackMemberAvatarUri by remember(activeGroupFallbackMemberCardId) {
        activeGroupFallbackMemberCardId?.let { userPreferencesManager.getAiAvatarForCharacterCardFlow(it) }
            ?: flowOf(null)
    }.collectAsState(initial = null)
    val activeCharacterAvatarUri =
        when (activePrompt) {
            is ActivePrompt.CharacterGroup -> activeGroupAvatarUri ?: activeGroupFallbackMemberAvatarUri
            is ActivePrompt.CharacterCard -> activeCardAvatarUri
        }

    val activeStreamingChatIds by actualViewModel.activeStreamingChatIds.collectAsState()
    val isFloatingMode by actualViewModel.isFloatingMode.collectAsState()

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                actualViewModel.launchWindowFloatingModeAfterMicPermissionGranted(
                    colorScheme = colorScheme,
                    typography = typography,
                    moveTaskToBackOnReady = true
                )
            } else {
                actualViewModel.showToast(context.getString(R.string.microphone_permission_denied))
            }
        }


    val launchFloatingWindow = useFloatingWindowLauncher(actualViewModel, permissionLauncher)

    Row(
            modifier =
                    modifier
                            .fillMaxWidth()
                            .background(
                                    if (chatHeaderTransparent) Color.Transparent
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChatHeader(
                showChatHistorySelector = showChatHistorySelector,
                onToggleChatHistorySelector = { actualViewModel.toggleChatHistorySelector() },
                modifier = Modifier.weight(1f),
                isFloatingMode = isFloatingMode,
                onLaunchFloatingWindow = launchFloatingWindow,
                historyIconColor = chatHeaderHistoryIconColor,
                pipIconColor = chatHeaderPipIconColor,
                runningTaskCount = activeStreamingChatIds.size,
                activeCharacterName = activeCharacterGroup?.name ?: activeCharacterCard?.name ?: "",
                activeCharacterAvatarUri = activeCharacterAvatarUri,
                 onCharacterClick = onCharacterSwitcherClick
        )

        if (parentRuns.isNotEmpty()) {
            SubagentManageButton(
                hasActiveRun = hasActiveRun,
                onClick = { showSubagentManager = true },
            )
        }

        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TokenUsageRing(currentWindowSize, inputTokenCount, outputTokenCount, maxWindowSizeInK,
                currentChatId, actualViewModel::estimateConversationCost)
        }
    }
}

@Composable
private fun TokenUsageRing(
    currentWindowSize: Long,
    inputTokenCount: Long,
    outputTokenCount: Long,
    maxWindowSizeInK: Float,
    costChatId: String?,
    loadCost: suspend (String) -> com.ai.assistance.operit.data.stats.ChatCostEstimate,
    modifier: Modifier = Modifier,
) {
    // 统计信息
    val maxWindowSize = (maxWindowSizeInK * 1024).toLong().coerceAtLeast(0L)
    val totalTokenCount = inputTokenCount + outputTokenCount
    val contextUsagePercentage =
        if (maxWindowSize > 0) {
            ((currentWindowSize.toDouble() / maxWindowSize.toDouble()) * 100.0)
                .coerceAtMost(999.0)
                .toFloat()
        } else {
            0f
        }

    // 使用一个状态来跟踪是否显示详细信息
    val (showDetailedStats, setShowDetailedStats) = remember(costChatId) { mutableStateOf(false) }
    var cost by remember(costChatId) {
        mutableStateOf<com.ai.assistance.operit.data.stats.ChatCostEstimate?>(null)
    }
    var costFailed by remember(costChatId) { mutableStateOf(false) }
    LaunchedEffect(showDetailedStats, costChatId, inputTokenCount, outputTokenCount) {
        if (showDetailedStats && costChatId != null) {
            costFailed = false
            try {
                cost = loadCost(costChatId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                cost = null
                costFailed = true
            }
        }
    }

    Box(modifier) {
        // 主要显示（圆环进度）
        val progress = (contextUsagePercentage / 100f).coerceIn(0f, 1f)
        val animatedProgress by animateFloatAsState(targetValue = progress, label = "TokenProgressAnimation")
        val progressColor = when {
            contextUsagePercentage > 90 -> MaterialTheme.colorScheme.error
            contextUsagePercentage > 75 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }

        Box(
            modifier = Modifier
                .clickable { setShowDetailedStats(!showDetailedStats) }
                .size(32.dp)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier.fillMaxSize(),
                color = progressColor,
                strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            Text(
                text = "${contextUsagePercentage.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = progressColor
            )
        }

        // 简化的下拉框
        DropdownMenu(
            expanded = showDetailedStats,
            onDismissRequest = { setShowDetailedStats(false) },
            modifier =
                Modifier.width(IntrinsicSize.Min)
                    .background(MaterialTheme.colorScheme.surface)
        ) {
            DropdownMenuItem(
                text = {
                    Text(when {
                        cost != null -> stringResource(
                            if (cost!!.incomplete) R.string.chat_estimated_cost_partial else R.string.chat_estimated_cost,
                            String.format(java.util.Locale.ROOT, "%.4f", cost!!.cny),
                        )
                        costFailed -> stringResource(R.string.chat_estimated_cost_unavailable)
                        else -> stringResource(R.string.chat_estimated_cost_loading)
                    })
                },
                onClick = {},
                enabled = false,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.context_window, currentWindowSize)) },
                onClick = {},
                enabled = false
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.input_tokens, inputTokenCount)) },
                onClick = {},
                enabled = false
            )
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.output_tokens, outputTokenCount))
                },
                onClick = {},
                enabled = false
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.total_tokens, totalTokenCount),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight =
                                    androidx.compose.ui.text.font
                                        .FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {},
                enabled = false
            )
        }
    }
}

@Composable
private fun SubagentChatHeader(
    title: String,
    parentTitle: String,
    agentName: String,
    siblingRuns: List<com.ai.assistance.operit.data.model.SubagentRunEntity>,
    currentChildChatId: String,
    transparent: Boolean,
    onBackToParent: () -> Unit,
    onSwitchSubagent: (String) -> Unit,
    onManageSubagents: () -> Unit,
    stats: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSwitcher by rememberSaveable { mutableStateOf(false) }
    val visibleRuns = remember(siblingRuns) {
        siblingRuns.filter { it.archivedAt == null }
    }
    val hasActiveRun = remember(visibleRuns) {
        visibleRuns.any { it.isActiveSubagentRun() }
    }
    if (showSwitcher) {
        SubagentSwitcherSheet(
            runs = siblingRuns,
            currentChildChatId = currentChildChatId,
            onSelect = { run ->
                showSwitcher = false
                onSwitchSubagent(run.childChatId)
            },
            onDismiss = { showSwitcher = false },
        )
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    if (transparent) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    }
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackToParent) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.subagent_return_to_parent),
            )
        }
        SubagentHeaderTitle(
            parentTitle = parentTitle,
            agentName = agentName,
            taskTitle = title,
            onClick = { showSwitcher = true },
            modifier = Modifier.weight(1f),
        )
        SubagentManageButton(
            hasActiveRun = hasActiveRun,
            onClick = onManageSubagents,
        )
        stats()
    }
}

@Composable
fun StatItem(label: String, value: String, isHighlighted: Boolean = false) {
    Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
                text = value,
                style =
                        MaterialTheme.typography.labelMedium.copy(
                                fontWeight =
                                        if (isHighlighted)
                                                androidx.compose.ui.text.font.FontWeight.Bold
                                        else androidx.compose.ui.text.font.FontWeight.Normal
                        ),
                color =
                        if (isHighlighted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
        )
    }
}
