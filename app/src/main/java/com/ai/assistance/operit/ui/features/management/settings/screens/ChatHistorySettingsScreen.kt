package com.ai.assistance.operit.ui.features.management.settings.screens

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptOutlinedButton as OutlinedButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material3.*
import com.ai.assistance.operit.ui.features.management.databackup.components.DataBackupDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.management.databackup.components.*
import com.ai.assistance.operit.ui.features.conversation.memory.settings.ChatMemoryRebuildDateRangeDialog
import com.ai.assistance.operit.core.memory.extraction.ChatMemoryRebuildManager
import com.ai.assistance.operit.core.memory.extraction.ChatMemoryRebuildTimeScope
import com.ai.assistance.operit.core.memory.extraction.ChatMemoryWindowPlanner
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardChatStats
import com.ai.assistance.operit.data.model.CharacterGroupCard
import com.ai.assistance.operit.data.model.CharacterGroupChatStats
import com.ai.assistance.operit.data.model.ImportStrategy
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.management.settings.components.CharacterCardAssignDialog
import com.ai.assistance.operit.ui.features.management.settings.components.CharacterGroupAssignDialog
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.ai.assistance.operit.ui.features.management.databackup.components.DataBackupCard
import com.ai.assistance.operit.ui.features.management.databackup.components.DataBackupExpandableSection
import com.ai.assistance.operit.ui.features.management.databackup.components.DataBackupRow

/**
 * 无绑定工作区信息
 */
data class UnboundWorkspaceInfo(
    val name: String,
    val fullPath: String,
    val location: String // "内部存储" 或 "外部存储"
)

@Composable
fun ChatHistorySettingsScreen(header: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val memoryRebuildManager = remember { ChatMemoryRebuildManager.getInstance(context) }
    val memoryRebuildProgress by memoryRebuildManager.progress.collectAsState()

    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val activeProfileId by userPreferencesManager.activeMemorySpaceIdFlow.collectAsState(initial = "default")

    val characterCardStatsState by chatHistoryManager.characterCardStatsFlow
        .collectAsState(initial = null as List<CharacterCardChatStats>?)
    val characterCardStats = characterCardStatsState ?: emptyList()
    val isCharacterCardStatsLoading = characterCardStatsState == null

    val characterGroupStatsState by chatHistoryManager.characterGroupStatsFlow
        .collectAsState(initial = null as List<CharacterGroupChatStats>?)
    val characterGroupStats = characterGroupStatsState ?: emptyList()
    val isCharacterGroupStatsLoading = characterGroupStatsState == null

    var availableCharacterCards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    var characterCardsLoading by remember { mutableStateOf(true) }
    var availableCharacterGroups by remember { mutableStateOf<List<CharacterGroupCard>>(emptyList()) }
    var characterGroupsLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        characterCardManager.characterCardListFlow.collectLatest { ids ->
            val cards = ids.mapNotNull { id ->
                runCatching { characterCardManager.getCharacterCard(id) }.getOrNull()
            }
            availableCharacterCards = cards
            if (characterCardsLoading) {
                characterCardsLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        characterGroupCardManager.allCharacterGroupCardsFlow.collectLatest { groups ->
            availableCharacterGroups = groups
            if (characterGroupsLoading) {
                characterGroupsLoading = false
            }
        }
    }

    var chatHistories by remember { mutableStateOf<List<ChatHistory>>(emptyList()) }
    var totalChatCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        chatHistoryManager.chatHistoriesFlow.collect { histories ->
            chatHistories = histories
            totalChatCount = histories.size
        }
    }

    // 获取无绑定的工作区文件夹
    var unboundWorkspaces by remember { mutableStateOf<List<UnboundWorkspaceInfo>>(emptyList()) }
    LaunchedEffect(chatHistories) {
        try {
            val internalStorageLabel = context.getString(R.string.chathistory_internal_storage)
            val externalStorageLabel = context.getString(R.string.chathistory_external_storage)
            val result = withContext(Dispatchers.IO) {
                val workspaces = mutableListOf<UnboundWorkspaceInfo>()

                // 1. 检查内部存储工作区 (/data/data/files/workspace)
                val internalWorkspaceDir = File(context.filesDir, "workspace")
                val internalWorkspaceRoot = internalWorkspaceDir.canonicalFile
                val boundInternalWorkspaceNames = chatHistories
                    .mapNotNull { history ->
                        val workspace = history.workspace ?: return@mapNotNull null
                        try {
                            val workspaceDir = File(workspace).canonicalFile
                            if (workspaceDir.parentFile?.canonicalFile == internalWorkspaceRoot) {
                                workspaceDir.name
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            AppLogger.e(
                                "ChatHistorySettings",
                                "统计内部工作区绑定失败: chatId=${history.id}, workspace=$workspace",
                                e
                            )
                            null
                        }
                    }
                    .toSet()
                val boundExternalWorkspacePaths = chatHistories
                    .mapNotNull { history ->
                        val workspace = history.workspace ?: return@mapNotNull null
                        try {
                            val workspaceDir = File(workspace).canonicalFile
                            if (workspaceDir.parentFile?.canonicalFile != internalWorkspaceRoot) {
                                workspace
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            AppLogger.e(
                                "ChatHistorySettings",
                                "统计外部工作区绑定失败: chatId=${history.id}, workspace=$workspace",
                                e
                            )
                            null
                        }
                    }
                    .toSet()

                if (internalWorkspaceDir.exists() && internalWorkspaceDir.isDirectory) {
                    internalWorkspaceDir.listFiles { file -> file.isDirectory }?.forEach { dir ->
                        val workspaceName = dir.canonicalFile.name
                        if (workspaceName !in boundInternalWorkspaceNames) {
                            workspaces.add(
                                UnboundWorkspaceInfo(
                                    name = dir.name,
                                    fullPath = dir.absolutePath,
                                    location = internalStorageLabel
                                )
                            )
                        }
                    }
                }

                // 2. 检查外部存储工作区（旧位置）
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val externalWorkspaceDir = File(downloadDir, "Operit/workspace")
                if (externalWorkspaceDir.exists() && externalWorkspaceDir.isDirectory) {
                    externalWorkspaceDir.listFiles { file -> file.isDirectory }?.forEach { dir ->
                        val fullPath = dir.absolutePath
                        if (fullPath !in boundExternalWorkspacePaths) {
                            workspaces.add(
                                UnboundWorkspaceInfo(
                                    name = dir.name,
                                    fullPath = fullPath,
                                    location = externalStorageLabel
                                )
                            )
                        }
                    }
                }

                workspaces
            }

            unboundWorkspaces = result
        } catch (e: Exception) {
            AppLogger.e("ChatHistorySettings", "获取无绑定工作区失败", e)
            unboundWorkspaces = emptyList()
        }
    }

    val profileIds by userPreferencesManager.memorySpaceListFlow.collectAsState(initial = listOf("default"))
    var allProfiles by remember { mutableStateOf<List<MemorySpace>>(emptyList()) }

    LaunchedEffect(profileIds) {
        val profiles = profileIds.mapNotNull { profileId ->
            try {
                userPreferencesManager.getMemorySpaceFlow(profileId).first()
            } catch (_: Exception) {
                null
            }
        }
        allProfiles = profiles
    }

    val activeProfileName =
        allProfiles.find { it.id == activeProfileId }?.name ?: context.getString(R.string.default_profile_name)

    var showAssignCharacterDialog by remember { mutableStateOf(false) }
    var pendingAssignStat by remember { mutableStateOf<CharacterCardChatStats?>(null) }
    var selectedCharacterCardId by remember { mutableStateOf<String?>(null) }
    var assignInProgress by remember { mutableStateOf(false) }
    var pendingMissingStat by remember { mutableStateOf<CharacterCardChatStats?>(null) }
    var showMissingActionDialog by remember { mutableStateOf(false) }
    var showDeleteMissingDialog by remember { mutableStateOf(false) }
    var deleteMissingInProgress by remember { mutableStateOf(false) }

    var showAssignGroupDialog by remember { mutableStateOf(false) }
    var pendingAssignGroupStat by remember { mutableStateOf<CharacterGroupChatStats?>(null) }
    var selectedCharacterGroupId by remember { mutableStateOf<String?>(null) }
    var assignGroupInProgress by remember { mutableStateOf(false) }
    var pendingMissingGroupStat by remember { mutableStateOf<CharacterGroupChatStats?>(null) }
    var showMissingGroupActionDialog by remember { mutableStateOf(false) }

    val isScreenLoading =
        isCharacterCardStatsLoading ||
            characterCardsLoading ||
            isCharacterGroupStatsLoading ||
            characterGroupsLoading

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { header() } }
            item {
                DataBackupCard {
                    CharacterCardStatsCard(
                    stats = characterCardStats,
                    characterCards = availableCharacterCards,
                    isLoading = isCharacterCardStatsLoading || characterCardsLoading,
                    onAssignMissing = { stat ->
                        pendingMissingStat = stat
                        showMissingActionDialog = true
                    }
                )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    CharacterGroupStatsCard(
                    stats = characterGroupStats,
                    characterGroups = availableCharacterGroups,
                    isLoading = isCharacterGroupStatsLoading || characterGroupsLoading,
                    onAssignMissing = { stat ->
                        pendingMissingGroupStat = stat
                        showMissingGroupActionDialog = true
                    }
                )
                }
            }
            item(key = "chat_selection") { ChatHistoryBatchSelectorCard(
                    operationScope = scope,
                    chatHistories = chatHistories,
                    characterCards = availableCharacterCards,
                    characterGroups = availableCharacterGroups,
                    memoryRebuildManager = memoryRebuildManager,
                    memoryRebuildProgress = memoryRebuildProgress,
                    onApply = { selectedIds, targetCharacterName, targetCharacterGroupId, targetGroupName, shouldUnbindCharacterCard, shouldUnbindCharacterGroup ->
                        if (selectedIds.isEmpty()) {
                            OKToast.warning(context, context.getString(R.string.please_select_chats_first), OKToast.LENGTH_SHORT).show()
                            return@ChatHistoryBatchSelectorCard false
                        }
                        try {
                            var messageParts = mutableListOf<String>()

                            // 更新角色卡绑定（仅在明确指定时更新）
                            // shouldUnbindCharacterCard 为 true 表示移除绑定
                            // targetCharacterName 不为 null 表示设置新的角色卡
                            // 如果两者都为 false/null，且提供了分组，则只更新分组，不更新角色卡
                            val shouldUpdateCharacterGroup = shouldUnbindCharacterGroup || targetCharacterGroupId != null
                            val shouldUpdateCharacterCard =
                                !shouldUpdateCharacterGroup && (shouldUnbindCharacterCard || targetCharacterName != null)

                            if (shouldUpdateCharacterCard) {
                                chatHistoryManager.assignCharacterCardToChats(
                                    chatIds = selectedIds,
                                    targetCharacterCardName = targetCharacterName
                                )
                                messageParts.add(
                                    if (targetCharacterName.isNullOrBlank()) {
                                        context.getString(R.string.removed_character_card_binding, selectedIds.size)
                                    } else {
                                        context.getString(R.string.assigned_chats_to_character_card, selectedIds.size, targetCharacterName)
                                    }
                                )
                            }

                            if (shouldUpdateCharacterGroup) {
                                if (shouldUnbindCharacterGroup) {
                                    chatHistoryManager.clearCharacterGroupBindingForChats(selectedIds)
                                    messageParts.add(context.getString(R.string.removed_character_group_binding, selectedIds.size))
                                } else if (!targetCharacterGroupId.isNullOrBlank()) {
                                    chatHistoryManager.assignCharacterGroupToChats(
                                        chatIds = selectedIds,
                                        targetCharacterGroupId = targetCharacterGroupId
                                    )
                                    val targetCharacterGroupName =
                                        availableCharacterGroups.firstOrNull { it.id == targetCharacterGroupId }?.name
                                    messageParts.add(
                                        context.getString(
                                            R.string.assigned_chats_to_character_group,
                                            selectedIds.size,
                                            targetCharacterGroupName ?: targetCharacterGroupId
                                        )
                                    )
                                }
                            }

                            // 更新分组
                            if (targetGroupName != null) {
                                chatHistoryManager.assignGroupToChats(
                                    chatIds = selectedIds,
                                    groupName = targetGroupName
                                )
                                messageParts.add(context.getString(R.string.assigned_chats_to_group, selectedIds.size, targetGroupName))
                            }

                            val message = messageParts.joinToString("；")
                            OKToast.success(context, message, OKToast.LENGTH_SHORT).show()
                            true
                        } catch (e: Exception) {
                            OKToast.error(
                                context,
                                context.getString(R.string.batch_update_failed, e.localizedMessage ?: e.toString()),
                                OKToast.LENGTH_LONG
                            ).show()
                            false
                        }
                    }
                ) }
            item(key = "workspaces") { UnboundWorkspaceCard(
                    operationScope = scope,
                    unboundWorkspaces = unboundWorkspaces,
                    onDelete = { selectedWorkspacePaths ->
                        try {
                            val deletedCount = withContext(Dispatchers.IO) {
                                var count = 0
                                selectedWorkspacePaths.forEach { workspacePath ->
                                    val workspaceDir = File(workspacePath)
                                    if (workspaceDir.exists() && workspaceDir.deleteRecursively()) {
                                        count++
                                    }
                                }
                                count
                            }

                            OKToast.success(context, context.getString(R.string.deleted_unbound_workspaces, deletedCount), OKToast.LENGTH_SHORT).show()

                            // 刷新列表
                            unboundWorkspaces = unboundWorkspaces.filter { it.fullPath !in selectedWorkspacePaths }
                        } catch (e: Exception) {
                            AppLogger.e("ChatHistorySettings", "删除工作区失败", e)
                            OKToast.error(context, context.getString(R.string.delete_failed, e.localizedMessage ?: ""), OKToast.LENGTH_LONG).show()
                        }
                    }
                ) }
            item { ClearAllChatsAction(chatHistories, scope) }
        }

        AnimatedVisibility(
            visible = isScreenLoading,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
        modifier = Modifier
            .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = context.getString(R.string.loading_data_please_wait),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showMissingActionDialog && pendingMissingStat != null) {
        val stat = pendingMissingStat!!
        val displayName = stat.characterCardName ?: context.getString(R.string.unbound_character_card)
        DataBackupDialog(
            onDismissRequest = {
                showMissingActionDialog = false
                pendingMissingStat = null
            },
            title = {
                Text(text = context.getString(R.string.missing_card_entry_action_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = context.getString(
                            R.string.missing_card_entry_action_message,
                            displayName,
                            stat.chatCount
                        )
                    )
                    if (availableCharacterCards.isEmpty()) {
                        Text(
                            text = context.getString(R.string.no_available_character_cards_toast),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAssignStat = stat
                        selectedCharacterCardId = availableCharacterCards.firstOrNull()?.id
                        showMissingActionDialog = false
                        pendingMissingStat = null
                        showAssignCharacterDialog = true
                    },
                    enabled = availableCharacterCards.isNotEmpty()
                ) {
                    Text(context.getString(R.string.action_assign_to_card))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMissingActionDialog = false
                        showDeleteMissingDialog = true
                    }
                ) {
                    Text(
                        text = context.getString(R.string.action_delete_residual_chats),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }

    if (showMissingGroupActionDialog && pendingMissingGroupStat != null) {
        val stat = pendingMissingGroupStat!!
        val missingGroupId = stat.characterGroupId
        val displayName = missingGroupId?.let { groupId ->
            availableCharacterGroups.firstOrNull { it.id == groupId }?.name
        } ?: if (missingGroupId.isNullOrBlank()) {
            context.getString(R.string.unbound_character_group)
        } else {
            context.getString(R.string.missing_character_group_id, missingGroupId)
        }
        DataBackupDialog(
            onDismissRequest = {
                showMissingGroupActionDialog = false
                pendingMissingGroupStat = null
            },
            title = {
                Text(text = context.getString(R.string.missing_group_entry_action_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = context.getString(
                            R.string.missing_group_entry_action_message,
                            displayName,
                            stat.chatCount
                        )
                    )
                    if (availableCharacterGroups.isEmpty()) {
                        Text(
                            text = context.getString(R.string.no_available_character_groups_toast),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAssignGroupStat = stat
                        selectedCharacterGroupId = availableCharacterGroups.firstOrNull()?.id
                        showMissingGroupActionDialog = false
                        pendingMissingGroupStat = null
                        showAssignGroupDialog = true
                    },
                    enabled = availableCharacterGroups.isNotEmpty() && !missingGroupId.isNullOrBlank()
                ) {
                    Text(context.getString(R.string.action_assign_to_group))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMissingGroupActionDialog = false
                        pendingMissingGroupStat = null
                        if (!missingGroupId.isNullOrBlank()) {
                            scope.launch {
                                try {
                                    chatHistoryManager.clearCharacterGroupBinding(missingGroupId)
                                    OKToast.success(
                                        context,
                                        context.getString(R.string.removed_character_group_binding, stat.chatCount),
                                        OKToast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    OKToast.error(
                                        context,
                                        context.getString(R.string.delete_failed, e.localizedMessage ?: e.toString()),
                                        OKToast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = context.getString(R.string.remove_character_group_binding),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }

    if (showDeleteMissingDialog && pendingMissingStat != null) {
        val stat = pendingMissingStat!!
        val displayName = stat.characterCardName ?: context.getString(R.string.unbound_character_card)
        DataBackupDialog(
            dismissEnabled = !deleteMissingInProgress,
            onDismissRequest = {
                if (!deleteMissingInProgress) {
                    showDeleteMissingDialog = false
                    pendingMissingStat = null
                }
            },
            title = {
                Text(text = context.getString(R.string.confirm_delete))
            },
            text = {
                Text(
                    text = context.getString(
                        R.string.delete_residual_chats_confirmation,
                        displayName,
                        stat.chatCount
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleteMissingInProgress) {
                            return@TextButton
                        }
                        deleteMissingInProgress = true
                        scope.launch {
                            try {
                                val deletedCount = chatHistoryManager.deleteChatsByCharacterCardBinding(stat.characterCardName)
                                val skippedCount = (stat.chatCount - deletedCount).coerceAtLeast(0)
                                val toastText = if (skippedCount > 0) {
                                    context.getString(
                                        R.string.deleted_residual_chats_with_skipped_locked,
                                        deletedCount,
                                        skippedCount
                                    )
                                } else {
                                    context.getString(R.string.deleted_residual_chats_count, deletedCount)
                                }
                                OKToast.success(context, toastText, OKToast.LENGTH_SHORT).show()
                                showDeleteMissingDialog = false
                                pendingMissingStat = null
                            } catch (e: Exception) {
                                OKToast.error(
                                    context,
                                    context.getString(R.string.delete_failed, e.localizedMessage ?: e.toString()),
                                    OKToast.LENGTH_LONG
                                ).show()
                            } finally {
                                deleteMissingInProgress = false
                            }
                        }
                    },
                    enabled = !deleteMissingInProgress
                ) {
                    if (deleteMissingInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(context.getString(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!deleteMissingInProgress) {
                            showDeleteMissingDialog = false
                            pendingMissingStat = null
                        }
                    },
                    enabled = !deleteMissingInProgress
                ) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }

    if (showAssignCharacterDialog && pendingAssignStat != null) {
        CharacterCardAssignDialog(
            missingChatCount = pendingAssignStat?.chatCount ?: 0,
            characterCards = availableCharacterCards,
            selectedCardId = selectedCharacterCardId,
            onCardSelected = { selectedCharacterCardId = it },
            onDismiss = {
                if (!assignInProgress) {
                    showAssignCharacterDialog = false
                    pendingAssignStat = null
                    selectedCharacterCardId = null
                }
            },
            onConfirm = {
                val stat = pendingAssignStat
                val targetCard = availableCharacterCards.firstOrNull { it.id == selectedCharacterCardId }

                if (assignInProgress) {
                    return@CharacterCardAssignDialog
                }

                if (stat == null) {
                    OKToast.info(context, context.getString(R.string.no_chat_stats_to_assign), OKToast.LENGTH_SHORT).show()
                    showAssignCharacterDialog = false
                    return@CharacterCardAssignDialog
                }

                if (targetCard == null) {
                    OKToast.warning(context, context.getString(R.string.please_select_a_character_card), OKToast.LENGTH_SHORT).show()
                    return@CharacterCardAssignDialog
                }

                assignInProgress = true
                scope.launch {
                    try {
                        chatHistoryManager.reassignChatsToCharacterCard(
                            sourceCharacterCardName = stat.characterCardName,
                            targetCharacterCardName = targetCard.name
                        )
                        OKToast.success(
                            context,
                            context.getString(R.string.assigned_to_character_card, targetCard.name),
                            OKToast.LENGTH_SHORT
                        ).show()
                        showAssignCharacterDialog = false
                        pendingAssignStat = null
                        selectedCharacterCardId = null
                    } catch (e: Exception) {
                        OKToast.error(
                            context,
                            context.getString(R.string.assign_failed_error, e.localizedMessage ?: e.toString()),
                            OKToast.LENGTH_LONG
                        ).show()
                    } finally {
                        assignInProgress = false
                    }
                }
            },
            inProgress = assignInProgress
        )
    }
    if (showAssignGroupDialog && pendingAssignGroupStat != null) {
        CharacterGroupAssignDialog(
            missingChatCount = pendingAssignGroupStat?.chatCount ?: 0,
            characterGroups = availableCharacterGroups,
            selectedGroupId = selectedCharacterGroupId,
            onGroupSelected = { selectedCharacterGroupId = it },
            onDismiss = { if (!assignGroupInProgress) { showAssignGroupDialog = false; pendingAssignGroupStat = null } },
            onConfirm = {
                val stat = pendingAssignGroupStat
                val target = availableCharacterGroups.firstOrNull { it.id == selectedCharacterGroupId }
                if (stat != null && target != null && !assignGroupInProgress) {
                    assignGroupInProgress = true
                    scope.launch {
                        try {
                            val count = chatHistoryManager.reassignChatsToCharacterGroup(stat.characterGroupId, target.id)
                            OKToast.success(context, context.getString(R.string.assigned_chats_to_character_group, count, target.name), OKToast.LENGTH_SHORT).show()
                            showAssignGroupDialog = false
                            pendingAssignGroupStat = null
                        } catch (e: Exception) {
                            AppLogger.e("DataAndBackup", "修复角色群关联失败", e)
                            OKToast.error(context, context.getString(R.string.assign_failed_error, e.localizedMessage ?: e.toString()), OKToast.LENGTH_LONG).show()
                        } finally { assignGroupInProgress = false }
                    }
                }
            },
            inProgress = assignGroupInProgress,
        )
    }

}

@Composable
private fun CharacterCardStatsCard(
    stats: List<CharacterCardChatStats>,
    characterCards: List<CharacterCard>,
    isLoading: Boolean,
    onAssignMissing: (CharacterCardChatStats) -> Unit
) {
    val context = LocalContext.current
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }

        DataBackupExpandableSection(
        title = stringResource(R.string.character_card_statistics),
        subtitle = stringResource(R.string.character_card_statistics_subtitle),
        icon = Icons.Default.AssignmentInd,
    ) {


            if (isLoading) {
                Column(
        modifier = Modifier
            .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = context.getString(R.string.counting_chat_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (stats.isEmpty()) {
            Text(
                    text = context.getString(R.string.no_chat_data_available),
                style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val sortedStats = remember(stats) {
                    stats.sortedWith(
                        compareByDescending<CharacterCardChatStats> { it.characterCardName.isNullOrBlank() }
                            .thenBy { it.characterCardName ?: "" }
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sortedStats.forEach { stat ->
                        key(stat.characterCardName ?: "missing-${stat.hashCode()}") {
                            val matchedCard = characterCards.firstOrNull { card ->
                                card.name == stat.characterCardName
                            }
                            CharacterCardStatRow(
                                stat = stat,
                                characterCard = matchedCard,
                                userPreferencesManager = userPreferencesManager,
                                onAssignMissing = if (matchedCard == null) {
                                    { onAssignMissing(stat) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
         }}

@Composable
private fun CharacterGroupStatsCard(
    stats: List<CharacterGroupChatStats>,
    characterGroups: List<CharacterGroupCard>,
    isLoading: Boolean,
    onAssignMissing: (CharacterGroupChatStats) -> Unit
) {
    val context = LocalContext.current
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val groupById = remember(characterGroups) {
        characterGroups.associateBy { it.id }
    }

        DataBackupExpandableSection(
        title = stringResource(R.string.character_group_statistics),
        subtitle = stringResource(R.string.character_group_statistics_subtitle),
        icon = Icons.Default.Groups,
    ) {


            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = context.getString(R.string.counting_chat_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (stats.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_chat_data_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val sortedStats = remember(stats, groupById) {
                    stats.sortedWith(
                        compareByDescending<CharacterGroupChatStats> { stat ->
                            val groupId = stat.characterGroupId
                            !groupId.isNullOrBlank() && groupById[groupId] == null
                        }.thenBy { stat ->
                            stat.characterGroupId.isNullOrBlank()
                        }.thenBy { stat ->
                            val groupId = stat.characterGroupId
                            groupById[groupId]?.name ?: ""
                        }
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sortedStats.forEach { stat ->
                        key(stat.characterGroupId ?: "unbound-${stat.hashCode()}") {
                            val group = stat.characterGroupId?.let { groupById[it] }
                            val isMissingGroup = !stat.characterGroupId.isNullOrBlank() && group == null
                            CharacterGroupStatRow(
                                stat = stat,
                                characterGroup = group,
                                userPreferencesManager = userPreferencesManager,
                                onAssignMissing = if (isMissingGroup) {
                                    { onAssignMissing(stat) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
         }}

@Composable
private fun CharacterGroupStatRow(
    stat: CharacterGroupChatStats,
    characterGroup: CharacterGroupCard?,
    userPreferencesManager: UserPreferencesManager,
    onAssignMissing: (() -> Unit)?
) {
    val context = LocalContext.current
    val isMissingGroup = !stat.characterGroupId.isNullOrBlank() && characterGroup == null
    val iconBackground = if (isMissingGroup) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val iconTint = if (isMissingGroup) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .let {
            if (isMissingGroup && onAssignMissing != null) {
                it.clickable { onAssignMissing() }
            } else {
                it
            }
        }
        .padding(12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (characterGroup != null) {
            val avatarUri by userPreferencesManager
                .getAiAvatarForCharacterGroupFlow(characterGroup.id)
                .collectAsState(initial = null)

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (!avatarUri.isNullOrBlank()) Color.Transparent
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUri.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                        contentDescription = context.getString(R.string.character_group_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = characterGroup.name.firstOrNull()?.toString()
                            ?: context.getString(R.string.character_group_single_char),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconBackground.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = Icons.Default.PriorityHigh,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val displayName = when {
                characterGroup != null -> characterGroup.name
                stat.characterGroupId.isNullOrBlank() ->
                    context.getString(R.string.unbound_character_group)
                else ->
                    context.getString(R.string.missing_character_group_id, stat.characterGroupId)
            }
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = context.getString(R.string.chats_and_messages_count, stat.chatCount, stat.messageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isMissingGroup && onAssignMissing != null) {
                Text(
                    text = context.getString(R.string.click_to_manage_missing_group),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (isMissingGroup && onAssignMissing != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CharacterCardStatRow(
    stat: CharacterCardChatStats,
    characterCard: CharacterCard?,
    userPreferencesManager: UserPreferencesManager,
    onAssignMissing: (() -> Unit)?
) {
    val context = LocalContext.current
    val isMissing = stat.characterCardName.isNullOrBlank()
    val needsAttention = characterCard == null
    val iconBackground = if (needsAttention) {
        MaterialTheme.colorScheme.errorContainer
                        } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val iconTint = if (needsAttention) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .let {
            if (needsAttention && onAssignMissing != null) {
                it.clickable { onAssignMissing() }
                } else {
                it
            }
        }
        .padding(12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (characterCard != null) {
            val avatarUri by userPreferencesManager
                .getAiAvatarForCharacterCardFlow(characterCard.id)
                .collectAsState(initial = null)

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (!avatarUri.isNullOrBlank()) Color.Transparent
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUri.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                        contentDescription = context.getString(R.string.character_card_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
            Text(
                        text = characterCard.name.firstOrNull()?.toString() ?: context.getString(R.string.character_fallback),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconBackground.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = Icons.Default.PriorityHigh,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
                    Text(
                text = stat.characterCardName ?: context.getString(R.string.unbound_character_card),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
                    )
                    Text(
                text = context.getString(R.string.chats_and_messages_count, stat.chatCount, stat.messageCount),
                style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (needsAttention && onAssignMissing != null) {
                Text(
                    text = context.getString(R.string.click_to_manage_missing_card),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (needsAttention && onAssignMissing != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHistoryBatchSelectorCard(
    operationScope: CoroutineScope,
    chatHistories: List<ChatHistory>,
    characterCards: List<CharacterCard>,
    characterGroups: List<CharacterGroupCard>,
    memoryRebuildManager: ChatMemoryRebuildManager,
    memoryRebuildProgress: ChatMemoryRebuildManager.Progress,
    onApply: suspend (
        selectedChatIds: List<String>,
        targetCharacterCardName: String?,
        targetCharacterGroupId: String?,
        targetGroupName: String?,
        shouldUnbindCharacterCard: Boolean,
        shouldUnbindCharacterGroup: Boolean
    ) -> Boolean
) {
    val context = LocalContext.current
    val scope = operationScope
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var messageCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(chatHistories) {
        try {
            messageCounts = ChatHistoryManager.getInstance(context).getMessageCountsByChatId()
        } catch (e: Exception) {
            AppLogger.e("DataAndBackup", "读取聊天消息数量失败", e)
        }
    }
    var selectedChatIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedTargetName by remember { mutableStateOf<String?>(null) }
    var targetIsUnbind by remember { mutableStateOf(false) }
    var groupDropdownExpanded by remember { mutableStateOf(false) }
    var selectedTargetGroupId by remember { mutableStateOf<String?>(null) }
    var targetGroupIsUnbind by remember { mutableStateOf(false) }
    var targetGroupName by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var deleteInProgress by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBatchManagementSheet by remember { mutableStateOf(false) }
    var memoryWindowInput by rememberSaveable { mutableStateOf(ChatMemoryWindowPlanner.DEFAULT_WINDOW_MESSAGE_COUNT.toString()) }
    val selectedMemoryWindowSize = memoryWindowInput.toIntOrNull()
    val validMemoryWindow = selectedMemoryWindowSize != null && selectedMemoryWindowSize in ChatMemoryWindowPlanner.MIN_WINDOW_MESSAGE_COUNT..ChatMemoryWindowPlanner.MAX_WINDOW_MESSAGE_COUNT
    var showMemoryRebuildConfirmDialog by remember { mutableStateOf(false) }
    var memoryTimeScopeMode by remember { mutableStateOf(MemoryRebuildTimeScopeMode.ENTIRE) }
    var memoryRangeStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var memoryRangeEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var showMemoryDateRangePicker by remember { mutableStateOf(false) }
    val memoryDateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }

    val normalizedQuery = searchQuery.trim()
    val characterGroupNameById = remember(characterGroups) {
        characterGroups.associate { it.id to it.name }
    }
    val filteredHistories = remember(chatHistories, normalizedQuery, characterGroupNameById) {
        val base = if (normalizedQuery.isBlank()) {
            chatHistories
        } else {
            chatHistories.filter { history ->
                val groupName = history.characterGroupId?.let { characterGroupNameById[it] }
                history.title.contains(normalizedQuery, ignoreCase = true) ||
                        (history.group?.contains(normalizedQuery, ignoreCase = true) == true) ||
                        (history.characterCardName?.contains(normalizedQuery, ignoreCase = true) == true) ||
                        (groupName?.contains(normalizedQuery, ignoreCase = true) == true)
            }
        }
        // 先按是否有角色卡、分组分区，再按角色卡名称和分组名称排序，方便成批管理
        base.sortedWith(
            compareBy<ChatHistory> {
                // 无角色群组的排在后面
                it.characterGroupId.isNullOrBlank()
            }.thenBy {
                // 先按角色群组名称排序
                characterGroupNameById[it.characterGroupId] ?: ""
            }.thenBy {
                // 再按角色卡名称排序
                it.characterCardName.isNullOrBlank()
            }.thenBy {
                it.characterCardName ?: ""
            }.thenBy {
                // 然后按分组名称排序（空分组排在后面）
                it.group.isNullOrBlank()
            }.thenBy {
                it.group ?: ""
            }.thenByDescending {
                // 同一角色卡+分组内按最近更新时间倒序
                it.updatedAt
            }
        )
    }

    LaunchedEffect(chatHistories) {
        val availableIds = chatHistories.map { it.id }.toSet()
        selectedChatIds = selectedChatIds.filter { it in availableIds }.toSet()
    }

    LaunchedEffect(characterCards) {
        if (selectedTargetName != null && characterCards.none { it.name == selectedTargetName }) {
            selectedTargetName = null
        }
    }
    LaunchedEffect(characterGroups) {
        if (selectedTargetGroupId != null && characterGroups.none { it.id == selectedTargetGroupId }) {
            selectedTargetGroupId = null
        }
    }

    val hasSelection = selectedChatIds.isNotEmpty()
    val isMemoryRebuildRunning =
        memoryRebuildProgress.status == ChatMemoryRebuildManager.Status.PREPARING ||
            memoryRebuildProgress.status == ChatMemoryRebuildManager.Status.RUNNING
    val hasTargetSelection = targetIsUnbind || !selectedTargetName.isNullOrBlank()
    val hasTargetGroupSelection = targetGroupIsUnbind || !selectedTargetGroupId.isNullOrBlank()
    val hasTargetGroup = targetGroupName.isNotBlank()
    val canSubmit =
        hasSelection &&
            (hasTargetSelection || hasTargetGroupSelection || hasTargetGroup) &&
            !submitting &&
            !deleteInProgress &&
            !isMemoryRebuildRunning

    DataBackupCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = stringResource(R.string.data_backup_chat_records),
                subtitle = stringResource(R.string.data_backup_chat_records_desc),
                icon = Icons.AutoMirrored.Filled.PlaylistAddCheck
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(context.getString(R.string.search_by_title_group_card)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (chatHistories.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_chat_records_for_batch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (filteredHistories.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_matching_chats_adjust_filter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.selected_chats_count, selectedChatIds.size, filteredHistories.size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        TextButton(
                            onClick = {
                                val ids = filteredHistories.map { it.id }
                                selectedChatIds = selectedChatIds.toMutableSet().apply { addAll(ids) }
                            },
                            enabled =
                                filteredHistories.isNotEmpty() &&
                                    !submitting &&
                                    !deleteInProgress &&
                                    !isMemoryRebuildRunning
                        ) {
                            Text(context.getString(R.string.select_all_current_list))
                        }
                        TextButton(
                            onClick = { selectedChatIds = emptySet() },
                            enabled =
                                selectedChatIds.isNotEmpty() &&
                                    !submitting &&
                                    !deleteInProgress &&
                                    !isMemoryRebuildRunning
                        ) {
                            Text(context.getString(R.string.clear_selection))
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                ) {
                    itemsIndexed(filteredHistories, key = { _, history -> history.id }) { index, history ->
                        val groupName = history.characterGroupId?.let { characterGroupNameById[it] }
                        ChatHistorySelectableRow(
                            history = history,
                            messageCount = messageCounts[history.id],
                            characterGroupName = groupName,
                            selected = selectedChatIds.contains(history.id),
                            enabled = !submitting && !deleteInProgress && !isMemoryRebuildRunning,
                            onSelectionChange = { selected ->
                                if (submitting || deleteInProgress || isMemoryRebuildRunning) {
                                    return@ChatHistorySelectableRow
                                }
                                selectedChatIds = if (selected) {
                                    selectedChatIds + history.id
                                } else {
                                    selectedChatIds - history.id
                                }
                            }
                        )
                        if (index < filteredHistories.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)
                        }
                    }
                }
            }

            if (memoryRebuildProgress.status != ChatMemoryRebuildManager.Status.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (memoryRebuildProgress.status) {
                        ChatMemoryRebuildManager.Status.PREPARING -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = stringResource(R.string.chat_memory_rebuild_preparing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ChatMemoryRebuildManager.Status.RUNNING -> {
                            LinearProgressIndicator(
                                progress = { memoryRebuildProgress.fraction.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = context.getString(
                                    R.string.chat_memory_rebuild_progress,
                                    memoryRebuildProgress.currentChatTitle,
                                    memoryRebuildProgress.completedWindows + 1,
                                    memoryRebuildProgress.totalWindows,
                                    memoryRebuildProgress.processedSourceMessages,
                                    memoryRebuildProgress.totalSourceMessages,
                                    memoryRebuildProgress.failedWindows
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ChatMemoryRebuildManager.Status.COMPLETED -> {
                            Text(
                                text = context.getString(
                                    R.string.chat_memory_rebuild_completed,
                                    memoryRebuildProgress.completedChats,
                                    memoryRebuildProgress.completedWindows,
                                    memoryRebuildProgress.failedWindows
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        ChatMemoryRebuildManager.Status.CANCELLED -> {
                            Text(
                                text = context.getString(
                                    R.string.chat_memory_rebuild_cancelled,
                                    memoryRebuildProgress.completedWindows,
                                    memoryRebuildProgress.totalWindows
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ChatMemoryRebuildManager.Status.FAILED -> {
                            Text(
                                text = context.getString(
                                    R.string.chat_memory_rebuild_failed,
                                    memoryRebuildProgress.errorMessage
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        ChatMemoryRebuildManager.Status.IDLE -> Unit
                    }
                    if (isMemoryRebuildRunning) {
                        OutlinedButton(
                            onClick = memoryRebuildManager::cancel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.chat_memory_rebuild_cancel))
                        }
                    }
                }
            }

            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val actionsEnabled = hasSelection && !submitting && !deleteInProgress && !isMemoryRebuildRunning
                    TextButton(onClick = { showBatchManagementSheet = true }, enabled = actionsEnabled, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.data_backup_manage_links), maxLines = 1)
                    }
                    TextButton(onClick = { showMemoryRebuildConfirmDialog = true }, enabled = actionsEnabled, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Psychology, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.data_backup_build_memory), maxLines = 1)
                    }
                    TextButton(onClick = { showDeleteConfirmDialog = true }, enabled = actionsEnabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.delete), maxLines = 1)
                    }
                }
            }
        }
    }

    if (showBatchManagementSheet) {
        DataBackupDialog(
            dismissEnabled = !submitting,
            onDismissRequest = { if (!submitting) showBatchManagementSheet = false },
            title = { Text(stringResource(R.string.data_backup_manage_links)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Text(
                    text = context.getString(
                        R.string.selected_chats_count,
                        selectedChatIds.size,
                        filteredHistories.size
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    val targetLabel = when {
                        targetIsUnbind -> context.getString(R.string.remove_character_card_binding)
                        !selectedTargetName.isNullOrBlank() -> selectedTargetName!!
                        else -> ""
                    }
                    OutlinedTextField(
                        value = targetLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(context.getString(R.string.target_character_card_optional)) },
                        placeholder = { Text(context.getString(R.string.select_card_or_unbind)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.textFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.remove_character_card_binding)) },
                            onClick = {
                                targetIsUnbind = true
                                selectedTargetName = null
                                selectedTargetGroupId = null
                                targetGroupIsUnbind = false
                                dropdownExpanded = false
                            }
                        )
                        if (characterCards.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.no_available_character_cards_dropdown), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                enabled = false,
                                onClick = {}
                            )
                        } else {
                            characterCards.forEach { card ->
                                DropdownMenuItem(
                                    text = { Text(card.name) },
                                    onClick = {
                                        selectedTargetName = card.name
                                        targetIsUnbind = false
                                        selectedTargetGroupId = null
                                        targetGroupIsUnbind = false
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = groupDropdownExpanded,
                    onExpandedChange = { groupDropdownExpanded = it }
                ) {
                    val targetGroupLabel = when {
                        targetGroupIsUnbind -> context.getString(R.string.remove_character_group_binding)
                        !selectedTargetGroupId.isNullOrBlank() ->
                            characterGroupNameById[selectedTargetGroupId] ?: selectedTargetGroupId!!
                        else -> ""
                    }
                    OutlinedTextField(
                        value = targetGroupLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(context.getString(R.string.target_character_group_optional)) },
                        placeholder = { Text(context.getString(R.string.select_group_or_unbind)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.textFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = groupDropdownExpanded,
                        onDismissRequest = { groupDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.remove_character_group_binding)) },
                            onClick = {
                                targetGroupIsUnbind = true
                                selectedTargetGroupId = null
                                targetIsUnbind = false
                                selectedTargetName = null
                                groupDropdownExpanded = false
                            }
                        )
                        if (characterGroups.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.no_available_character_groups_dropdown), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                enabled = false,
                                onClick = {}
                            )
                        } else {
                            characterGroups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.name) },
                                    onClick = {
                                        selectedTargetGroupId = group.id
                                        targetGroupIsUnbind = false
                                        targetIsUnbind = false
                                        selectedTargetName = null
                                        groupDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = targetGroupName,
                    onValueChange = { targetGroupName = it },
                    singleLine = true,
                    maxLines = 1,
                    label = { Text(stringResource(R.string.target_group_optional)) },
                    placeholder = { Text(stringResource(R.string.enter_group_name_hint)) },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.batch_assign_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                }
                HorizontalDivider()
                TextButton(
                    onClick = {
                        showBatchManagementSheet = false
                        showDeleteConfirmDialog = true
                    },
                    enabled = selectedChatIds.isNotEmpty() && !submitting && !deleteInProgress,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.delete_selected_chats, selectedChatIds.size))
                }
             } },
            dismissButton = { TextButton(onClick = { showBatchManagementSheet = false }, enabled = !submitting) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {                 Button(
                        onClick = {
                            if (!canSubmit) return@Button
                            scope.launch {
                                submitting = true
                                val groupName = targetGroupName.takeIf { it.isNotBlank() }
                                val success = onApply(
                                    selectedChatIds.toList(),
                                    if (targetIsUnbind) null else selectedTargetName,
                                    if (targetGroupIsUnbind) null else selectedTargetGroupId,
                                    groupName,
                                    targetIsUnbind,
                                    targetGroupIsUnbind
                                )
                                if (success) {
                                    selectedChatIds = emptySet()
                                    targetGroupName = ""
                                    selectedTargetName = null
                                    targetIsUnbind = false
                                    selectedTargetGroupId = null
                                    targetGroupIsUnbind = false
                                    showBatchManagementSheet = false
                                }
                                submitting = false
                            }
                        },
                        enabled = canSubmit,
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        val buttonText = when {
                            targetGroupIsUnbind && targetGroupName.isNotBlank() -> context.getString(R.string.apply_changes)
                            targetGroupIsUnbind -> context.getString(R.string.remove_character_group_binding)
                            targetIsUnbind && targetGroupName.isNotBlank() -> context.getString(R.string.apply_changes)
                            targetIsUnbind -> context.getString(R.string.remove_character_card_binding)
                            !selectedTargetGroupId.isNullOrBlank() -> context.getString(R.string.apply_character_group)
                            targetGroupName.isNotBlank() && selectedTargetName.isNullOrBlank() -> context.getString(R.string.apply_group)
                            else -> context.getString(R.string.apply_character_card)
                        }
                        Text(buttonText)
                    } },
        )
    }



    if (showDeleteConfirmDialog) {
        DataBackupDialog(
            dismissEnabled = !deleteInProgress,
            onDismissRequest = {
                if (!deleteInProgress) {
                    showDeleteConfirmDialog = false
                }
            },
            title = { Text(context.getString(R.string.confirm_delete)) },
            text = {
                Text(
                    context.getString(
                        R.string.delete_selected_chats_confirmation,
                        selectedChatIds.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targets = selectedChatIds.toList()
                        scope.launch {
                            deleteInProgress = true
                            try {
                                val result = deleteSelectedChatHistories(context, targets)
                                val message =
                                    if (result.skippedLockedCount > 0) {
                                        context.getString(
                                            R.string.deleted_selected_chats_with_skipped_locked,
                                            result.deletedCount,
                                            result.skippedLockedCount
                                        )
                                    } else {
                                        context.getString(
                                            R.string.deleted_selected_chats_count,
                                            result.deletedCount
                                        )
                                    }
                                OKToast.success(context, message, OKToast.LENGTH_SHORT).show()
                                selectedChatIds = emptySet()
                                showDeleteConfirmDialog = false
                            } catch (e: Exception) {
                                OKToast.error(
                                    context,
                                    context.getString(
                                        R.string.delete_failed,
                                        e.localizedMessage ?: e.toString()
                                    ),
                                    OKToast.LENGTH_LONG
                                ).show()
                            } finally {
                                deleteInProgress = false
                            }
                        }
                    },
                    enabled = !deleteInProgress,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (deleteInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(context.getString(R.string.delete))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !deleteInProgress
                ) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }

    if (showMemoryRebuildConfirmDialog && !showMemoryDateRangePicker) {
        val rangeStart = memoryRangeStartDate
        val rangeEnd = memoryRangeEndDate
        val hasCompleteRange = rangeStart != null && rangeEnd != null
        val canConfirmMemoryRebuild =
            when (memoryTimeScopeMode) {
                MemoryRebuildTimeScopeMode.ENTIRE -> true
                MemoryRebuildTimeScopeMode.RANGE -> hasCompleteRange
            }
        DataBackupDialog(
            onDismissRequest = { showMemoryRebuildConfirmDialog = false },
            title = { Text(stringResource(R.string.chat_memory_rebuild_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.data_backup_memory_build_desc, selectedChatIds.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = memoryWindowInput,
                        onValueChange = { memoryWindowInput = it },
                        label = { Text(stringResource(R.string.chat_memory_rebuild_window_size)) },
                        supportingText = { Text(stringResource(R.string.data_backup_memory_window_hint)) },
                        isError = !validMemoryWindow,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.chat_memory_rebuild_time_scope), style = MaterialTheme.typography.labelLarge)
                    DataBackupCard {
                        Row(Modifier.fillMaxWidth().clickable { memoryTimeScopeMode = MemoryRebuildTimeScopeMode.ENTIRE }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = memoryTimeScopeMode == MemoryRebuildTimeScopeMode.ENTIRE, onClick = { memoryTimeScopeMode = MemoryRebuildTimeScopeMode.ENTIRE })
                            Text(stringResource(R.string.chat_memory_rebuild_time_scope_all), style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().clickable { memoryTimeScopeMode = MemoryRebuildTimeScopeMode.RANGE; showMemoryDateRangePicker = true }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = memoryTimeScopeMode == MemoryRebuildTimeScopeMode.RANGE, onClick = { memoryTimeScopeMode = MemoryRebuildTimeScopeMode.RANGE; showMemoryDateRangePicker = true })
                            Text(stringResource(R.string.chat_memory_rebuild_time_scope_range), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (memoryTimeScopeMode == MemoryRebuildTimeScopeMode.RANGE) {
                        val rangeLabel = if (rangeStart != null && rangeEnd != null) context.getString(R.string.chat_memory_rebuild_time_scope_value, rangeStart.format(memoryDateFormatter), rangeEnd.format(memoryDateFormatter)) else context.getString(R.string.chat_memory_rebuild_time_scope_pick)
                        OutlinedButton(onClick = { showMemoryDateRangePicker = true }, modifier = Modifier.fillMaxWidth()) { Text(rangeLabel) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = validMemoryWindow && canConfirmMemoryRebuild && !isMemoryRebuildRunning && selectedChatIds.isNotEmpty(),
                    onClick = {
                        val timeScope =
                            when (memoryTimeScopeMode) {
                                MemoryRebuildTimeScopeMode.ENTIRE ->
                                    ChatMemoryRebuildTimeScope.EntireChat
                                MemoryRebuildTimeScopeMode.RANGE -> {
                                    val start = memoryRangeStartDate
                                    val end = memoryRangeEndDate
                                    if (start == null || end == null) {
                                        return@TextButton
                                    }
                                    ChatMemoryRebuildTimeScope.inclusiveDates(
                                        start,
                                        end,
                                        ZoneId.systemDefault()
                                    )
                                }
                            }
                        memoryRebuildManager.start(
                            chatIds = selectedChatIds.toList(),
                            windowMessageCount = selectedMemoryWindowSize ?: return@TextButton,
                            timeScope = timeScope
                        )
                        showMemoryRebuildConfirmDialog = false
                    }
                ) {
                    Text(stringResource(R.string.chat_memory_rebuild_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMemoryRebuildConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showMemoryDateRangePicker) {
        ChatMemoryRebuildDateRangeDialog(
            initialStart = memoryRangeStartDate,
            initialEnd = memoryRangeEndDate,
            onConfirm = { start, end ->
                memoryRangeStartDate = start
                memoryRangeEndDate = end
                memoryTimeScopeMode = MemoryRebuildTimeScopeMode.RANGE
                showMemoryDateRangePicker = false
            },
            onDismiss = { showMemoryDateRangePicker = false }
        )
    }
}

private enum class MemoryRebuildTimeScopeMode {
    ENTIRE,
    RANGE
}


@Composable
private fun ChatHistorySelectableRow(
    history: ChatHistory,
    messageCount: Int?,
    characterGroupName: String?,
    selected: Boolean,
    enabled: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onSelectionChange(!selected) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = onSelectionChange, enabled = enabled)
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val roleName = if (!history.characterGroupId.isNullOrBlank()) characterGroupName ?: context.getString(R.string.missing_character_group_id, history.characterGroupId) else history.characterCardName ?: context.getString(R.string.unbound_character_card)
            val countText = if (messageCount != null) context.getString(R.string.data_backup_message_count_suffix, messageCount) else ""
            val updatedText = history.updatedAt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(history.title.ifBlank { context.getString(R.string.unnamed_conversation) }, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (history.locked) Icon(Icons.Default.Lock, stringResource(R.string.data_backup_locked_chat), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(roleName + countText, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text((history.group ?: context.getString(R.string.data_backup_ungrouped)) + " · " + updatedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector
    ) {
        Row(
                modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Icon(
            imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                style = MaterialTheme.typography.titleMedium
                )
                Text(
                text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

/**
 * 无绑定工作区管理卡片
 */
@Composable
private fun UnboundWorkspaceCard(
    operationScope: CoroutineScope,
    unboundWorkspaces: List<UnboundWorkspaceInfo>,
    onDelete: suspend (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = operationScope
    var showSelection by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(unboundWorkspaces) {
        selected = selected.intersect(unboundWorkspaces.map { it.fullPath }.toSet())
    }
    DataBackupCard {
        DataBackupRow(stringResource(R.string.unbound_workspaces_title), stringResource(R.string.unbound_workspaces_subtitle), Icons.Default.FolderOff, onClick = { showSelection = true }, trailing = stringResource(R.string.data_backup_workspace_count, unboundWorkspaces.size))
    }
    if (showSelection && !showDeleteConfirm) {
        DataBackupDialog(
            onDismissRequest = { if (!deleting) showSelection = false },
            title = { Text(stringResource(R.string.unbound_workspaces_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.data_backup_workspace_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.selected_workspaces_count, selected.size, unboundWorkspaces.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { selected = unboundWorkspaces.map { it.fullPath }.toSet() }) { Text(stringResource(R.string.select_all_current_list)) }
                        TextButton(onClick = { selected = emptySet() }) { Text(stringResource(R.string.clear_selection)) }
                    }
                    if (unboundWorkspaces.isEmpty()) Text(stringResource(R.string.no_unbound_workspaces))
                    else DataBackupCard {
                        unboundWorkspaces.forEachIndexed { index, workspace ->
                            if (index > 0) HorizontalDivider()
                            UnboundWorkspaceRow(workspace, workspace.fullPath in selected) { checked -> selected = if (checked) selected + workspace.fullPath else selected - workspace.fullPath }
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showSelection = false }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                Button(onClick = { showDeleteConfirm = true }, enabled = selected.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete_selected_workspaces, selected.size)) }
            },
        )
    }
    if (showDeleteConfirm) {
        DataBackupDialog(
            dismissEnabled = !deleting,
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(stringResource(R.string.delete_workspaces_confirmation, selected.size)) },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }, enabled = !deleting) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                Button(onClick = {
                    val targets = selected
                    scope.launch {
                        deleting = true
                        try { onDelete(targets); selected = emptySet(); showDeleteConfirm = false; showSelection = false }
                        finally { deleting = false }
                    }
                }, enabled = !deleting, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete)) }
            },
        )
    }
}

/**
 * 无绑定工作区行项目
 */
@Composable
private fun UnboundWorkspaceRow(
    workspaceInfo: UnboundWorkspaceInfo,
    selected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChange(!selected) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onSelectionChange(it) }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workspaceInfo.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = workspaceInfo.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = context.getString(R.string.not_used_by_any_chat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = if (workspaceInfo.location == context.getString(R.string.chathistory_internal_storage)) Icons.Default.Folder else Icons.Default.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

private data class DeleteSelectedChatsResult(
    val deletedCount: Int,
    val skippedLockedCount: Int
)

private suspend fun deleteSelectedChatHistories(
    context: Context,
    selectedChatIds: List<String>
): DeleteSelectedChatsResult =
    withContext(Dispatchers.IO) {
        val chatHistoryManager = ChatHistoryManager.getInstance(context)
        var deletedCount = 0
        var skippedLockedCount = 0

        selectedChatIds.forEach { chatId ->
            val deleted = chatHistoryManager.deleteChatHistory(chatId)
            if (deleted) {
                deletedCount++
            } else {
                skippedLockedCount++
            }
        }

        DeleteSelectedChatsResult(
            deletedCount = deletedCount,
            skippedLockedCount = skippedLockedCount
        )
    }


@Composable
private fun ClearAllChatsAction(chatHistories: List<ChatHistory>, operationScope: CoroutineScope) {
    val context = LocalContext.current
    val scope = operationScope
    var confirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.data_backup_locked_notice), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { confirm = true }, enabled = chatHistories.isNotEmpty() && !deleting, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.data_backup_clear_chats)) }
    }
    if (confirm) {
        DataBackupDialog(
            dismissEnabled = !deleting,
            onDismissRequest = { if (!deleting) confirm = false },
            title = { Text(stringResource(R.string.data_backup_clear_chats)) },
            text = { Text(stringResource(R.string.data_backup_clear_chats_confirm)) },
            dismissButton = { TextButton(onClick = { confirm = false }, enabled = !deleting) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                Button(onClick = {
                    val ids = chatHistories.map { it.id }
                    scope.launch {
                        deleting = true
                        try {
                            val result = deleteSelectedChatHistories(context, ids)
                            val message = context.getString(R.string.deleted_selected_chats_with_skipped_locked, result.deletedCount, result.skippedLockedCount)
                            OKToast.success(context, message, OKToast.LENGTH_SHORT).show()
                            confirm = false
                        } catch (e: Exception) {
                            AppLogger.e("DataAndBackup", "清空聊天记录失败", e)
                            OKToast.error(context, context.getString(R.string.delete_failed, e.localizedMessage ?: e.toString()), OKToast.LENGTH_LONG).show()
                        } finally { deleting = false }
                    }
                }, enabled = !deleting, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete)) }
            },
        )
    }
}
