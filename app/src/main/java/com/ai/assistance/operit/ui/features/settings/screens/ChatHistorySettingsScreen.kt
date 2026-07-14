package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.*
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
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
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
import com.ai.assistance.operit.ui.features.settings.components.CharacterCardAssignDialog
import com.ai.assistance.operit.ui.features.settings.components.CharacterGroupAssignDialog
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 无绑定工作区信息
 */
data class UnboundWorkspaceInfo(
    val name: String,
    val fullPath: String,
    val location: String // "内部存储" 或 "外部存储"
)

@Composable
fun ChatHistorySettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ChatManagementOverviewCard(
                    totalChatCount = totalChatCount,
                    activeProfileName = activeProfileName
                )
            }
            item {
                CharacterCardStatsCard(
                    stats = characterCardStats,
                    characterCards = availableCharacterCards,
                    isLoading = isCharacterCardStatsLoading || characterCardsLoading,
                    onAssignMissing = { stat ->
                        pendingMissingStat = stat
                        showMissingActionDialog = true
                    }
                )
            }
            item {
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
            item {
                ChatHistoryBatchSelectorCard(
                    chatHistories = chatHistories,
                    characterCards = availableCharacterCards,
                    characterGroups = availableCharacterGroups,
                    onApply = { selectedIds, targetCharacterName, targetCharacterGroupId, targetGroupName, shouldUnbindCharacterCard, shouldUnbindCharacterGroup ->
                        if (selectedIds.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.please_select_chats_first), Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            true
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.batch_update_failed, e.localizedMessage ?: e.toString()),
                                Toast.LENGTH_LONG
                            ).show()
                            false
                        }
                    }
                )
            }
            
            // 无绑定工作区管理卡片
            item {
                UnboundWorkspaceCard(
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

                            Toast.makeText(context, context.getString(R.string.deleted_unbound_workspaces, deletedCount), Toast.LENGTH_SHORT).show()

                            // 刷新列表
                            unboundWorkspaces = unboundWorkspaces.filter { it.fullPath !in selectedWorkspacePaths }
                        } catch (e: Exception) {
                            AppLogger.e("ChatHistorySettings", "删除工作区失败", e)
                            Toast.makeText(context, context.getString(R.string.delete_failed, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
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
        AlertDialog(
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
        AlertDialog(
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
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.removed_character_group_binding, stat.chatCount),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.delete_failed, e.localizedMessage ?: e.toString()),
                                        Toast.LENGTH_LONG
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
        AlertDialog(
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
                                Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                                showDeleteMissingDialog = false
                                pendingMissingStat = null
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.delete_failed, e.localizedMessage ?: e.toString()),
                                    Toast.LENGTH_LONG
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
                    Toast.makeText(context, context.getString(R.string.no_chat_stats_to_assign), Toast.LENGTH_SHORT).show()
                    showAssignCharacterDialog = false
                    return@CharacterCardAssignDialog
                }

                if (targetCard == null) {
                    Toast.makeText(context, context.getString(R.string.please_select_a_character_card), Toast.LENGTH_SHORT).show()
                    return@CharacterCardAssignDialog
                }

                assignInProgress = true
                scope.launch {
                    try {
                        chatHistoryManager.reassignChatsToCharacterCard(
                            sourceCharacterCardName = stat.characterCardName,
                            targetCharacterCardName = targetCard.name
                        )
                        Toast.makeText(
                            context,
                            context.getString(R.string.assigned_to_character_card, targetCard.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        showAssignCharacterDialog = false
                        pendingAssignStat = null
                        selectedCharacterCardId = null
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.assign_failed_error, e.localizedMessage ?: e.toString()),
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        assignInProgress = false
                    }
                }
            },
            inProgress = assignInProgress
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatManagementOverviewCard(
    totalChatCount: Int,
    activeProfileName: String
) {
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                    text = context.getString(R.string.chat_history_overview),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = context.getString(R.string.current_config_label, activeProfileName),
                style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatChip(
                icon = Icons.Default.History,
                title = "$totalChatCount",
                subtitle = context.getString(R.string.chat_records_label)
            )
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = context.getString(R.string.character_card_statistics),
                subtitle = context.getString(R.string.character_card_statistics_subtitle),
                icon = Icons.Default.AssignmentInd
            )

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
        }
    }
}

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

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = context.getString(R.string.character_group_statistics),
                subtitle = context.getString(R.string.character_group_statistics_subtitle),
                icon = Icons.Default.Groups
            )

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
        }
    }
}

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
    chatHistories: List<ChatHistory>,
    characterCards: List<CharacterCard>,
    characterGroups: List<CharacterGroupCard>,
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
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedChatIds by remember { mutableStateOf<Set<String>>(emptySet()) }
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
    val hasTargetSelection = targetIsUnbind || !selectedTargetName.isNullOrBlank()
    val hasTargetGroupSelection = targetGroupIsUnbind || !selectedTargetGroupId.isNullOrBlank()
    val hasTargetGroup = targetGroupName.isNotBlank()
    val canSubmit =
        hasSelection &&
            (hasTargetSelection || hasTargetGroupSelection || hasTargetGroup) &&
            !submitting &&
            !deleteInProgress

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = stringResource(R.string.batch_assign_title),
                subtitle = stringResource(R.string.batch_assign_subtitle),
                icon = Icons.AutoMirrored.Filled.PlaylistAddCheck
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(context.getString(R.string.search_by_title_group_card)) },
                label = { Text(context.getString(R.string.filter_chat_history)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (chatHistories.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_chat_records_for_batch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@ElevatedCard
            }

            if (filteredHistories.isEmpty()) {
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
                            enabled = filteredHistories.isNotEmpty() && !submitting && !deleteInProgress
                        ) {
                            Text(context.getString(R.string.select_all_current_list))
                        }
                        TextButton(
                            onClick = { selectedChatIds = emptySet() },
                            enabled = selectedChatIds.isNotEmpty() && !submitting && !deleteInProgress
                        ) {
                            Text(context.getString(R.string.clear_selection))
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    itemsIndexed(filteredHistories, key = { _, history -> history.id }) { index, history ->
                        val groupName = history.characterGroupId?.let { characterGroupNameById[it] }
                        ChatHistorySelectableRow(
                            history = history,
                            characterGroupName = groupName,
                            selected = selectedChatIds.contains(history.id),
                            onSelectionChange = { selected ->
                                if (submitting || deleteInProgress) {
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

            Button(
                onClick = { showDeleteConfirmDialog = true },
                enabled = hasSelection && !submitting && !deleteInProgress,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                if (deleteInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                } else {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(context.getString(R.string.delete_selected_chats, selectedChatIds.size))
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Button(
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
                                }
                                submitting = false
                            }
                        },
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f)
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
                    }
                    TextButton(
                        onClick = {
                            selectedChatIds = emptySet()
                        },
                        enabled = selectedChatIds.isNotEmpty() && !deleteInProgress,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text(context.getString(R.string.cancel_selection))
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
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
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                selectedChatIds = emptySet()
                                showDeleteConfirmDialog = false
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.delete_failed,
                                        e.localizedMessage ?: e.toString()
                                    ),
                                    Toast.LENGTH_LONG
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
}

@Composable
private fun ChatHistorySelectableRow(
    history: ChatHistory,
    characterGroupName: String?,
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
                text = history.title.ifBlank { context.getString(R.string.unnamed_conversation) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                history.group?.let { group ->
                    append(context.getString(R.string.group_label, group))
                    append(" · ")
                }
                val groupId = history.characterGroupId?.trim()
                if (!groupId.isNullOrBlank()) {
                    val resolvedGroupName =
                        characterGroupName
                            ?: context.getString(R.string.missing_character_group_id, groupId)
                    append(context.getString(R.string.character_group_label, resolvedGroupName))
                    return@buildString
                }

                val cardInfo =
                    if (history.characterCardName.isNullOrBlank()) {
                        context.getString(R.string.unbound_character_card)
                    } else {
                        context.getString(R.string.character_card_label, history.characterCardName)
                    }
                append(cardInfo)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    unboundWorkspaces: List<UnboundWorkspaceInfo>,
    onDelete: suspend (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedWorkspaces by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteInProgress by remember { mutableStateOf(false) }
    
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = context.getString(R.string.unbound_workspaces_title),
                subtitle = context.getString(R.string.unbound_workspaces_subtitle),
                icon = Icons.Default.FolderOff
            )
            
            if (unboundWorkspaces.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_unbound_workspaces),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 选择控制栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.selected_workspaces_count, selectedWorkspaces.size, unboundWorkspaces.size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { selectedWorkspaces = unboundWorkspaces.map { it.fullPath }.toSet() },
                            enabled = unboundWorkspaces.isNotEmpty() && !deleteInProgress
                        ) {
                            Text(context.getString(R.string.select_all_current_list))
                        }
                        TextButton(
                            onClick = { selectedWorkspaces = emptySet() },
                            enabled = selectedWorkspaces.isNotEmpty() && !deleteInProgress
                        ) {
                            Text(context.getString(R.string.clear_all))
                        }
                    }
                }
                
                // 工作区列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    itemsIndexed(unboundWorkspaces, key = { _, workspace -> workspace.fullPath }) { index, workspace ->
                        UnboundWorkspaceRow(
                            workspaceInfo = workspace,
                            selected = selectedWorkspaces.contains(workspace.fullPath),
                            onSelectionChange = { selected ->
                                if (!deleteInProgress) {
                                    selectedWorkspaces = if (selected) {
                                        selectedWorkspaces + workspace.fullPath
                                    } else {
                                        selectedWorkspaces - workspace.fullPath
                                    }
                                }
                            }
                        )
                        if (index < unboundWorkspaces.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)
                        }
                    }
                }
                
                // 删除按钮
                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    enabled = selectedWorkspaces.isNotEmpty() && !deleteInProgress,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    if (deleteInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.delete_selected_workspaces, selectedWorkspaces.size))
                }
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!deleteInProgress) {
                    showDeleteConfirmDialog = false
                }
            },
            title = { Text(context.getString(R.string.confirm_delete)) },
            text = { 
                Text(context.getString(R.string.delete_workspaces_confirmation, selectedWorkspaces.size)) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targets = selectedWorkspaces
                        scope.launch {
                            deleteInProgress = true
                            try {
                                onDelete(targets)
                                selectedWorkspaces = emptySet()
                                showDeleteConfirmDialog = false
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

