package com.ai.assistance.operit.ui.features.conversation.memory.workbench

import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.MemorySpaceProfileDocumentRepository
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun MemorySpaceOverview(onOpenSpace: (String) -> Unit, onOpenRecent: (String, String?) -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
    val cardManager = remember(context) { CharacterCardManager.getInstance(context) }
    val spaceIds by preferences.memorySpaceListFlow.collectAsState(initial = emptyList())
    val activeId by preferences.activeMemorySpaceIdFlow.collectAsState(initial = "default")
    val cardIds by cardManager.characterCardListFlow.collectAsState(initial = emptyList())
    val currentScreen = LocalIsCurrentScreen.current
    val scope = rememberCoroutineScope()
    var spaces by remember { mutableStateOf<List<MemorySpace>>(emptyList()) }
    var cards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    var summaries by remember { mutableStateOf<Map<String, MemorySpaceSummary>>(emptyMap()) }
    var viewedId by rememberSaveable { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var chooseGlobal by remember { mutableStateOf(false) }
    var bindingCard by remember { mutableStateOf<CharacterCard?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<MemorySpace?>(null) }
    var deleting by remember { mutableStateOf<MemorySpace?>(null) }
    var draft by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }

    LaunchedEffect(spaceIds, cardIds, currentScreen, revision) {
        if (!currentScreen) return@LaunchedEffect
        try {
            MemorySpaceProfileDocumentRepository.getInstance(context).initialize()
            preferences.ensureDefaultMemorySpace(context.getString(R.string.default_profile))
            val loaded = preferences.memorySpaceListFlow.first().map { preferences.getMemorySpaceFlow(it).first() }
            spaces = loaded
            cards = cardManager.getAllCharacterCards().sortedBy { it.name }
            if (loaded.none { it.id == viewedId }) viewedId = preferences.activeMemorySpaceIdFlow.first()
            ready = true
            error = null
            summaries = withContext(Dispatchers.IO) { loaded.associate { it.id to MemoryRepository(context, it.id).getSpaceSummary() } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.toString()
        }
    }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                action()
                revision++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                OKToast.error(context, failure.toString(), OKToast.LENGTH_SHORT).show()
            } finally { busy = false }
        }
    }
    val names = spaces.associate { it.id to it.name }
    val selected = spaces.firstOrNull { it.id == viewedId }
    fun rename(space: MemorySpace) { renaming = space; draft = space.name; nameError = false }
    fun fixed(card: CharacterCard) = CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode) == CharacterCardMemoryProfileBindingMode.FIXED_PROFILE

    CustomScaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Layers, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.memory_management_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.memory_workbench_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { help = true }) { Icon(Icons.Default.HelpOutline, stringResource(R.string.memory_workbench_help)) }
                }
            }
            if (error != null) item {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { revision++ }) { Text(stringResource(R.string.action_retry)) }
            }
            if (!ready) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            if (ready && selected != null) {
                item {
                    OverviewSection(stringResource(R.string.memory_overview_spaces) + "  ${spaces.size}") {
                        TextButton(enabled = !busy, onClick = { draft = ""; creating = true; nameError = false }) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text(stringResource(R.string.memory_overview_create)) }
                    }
                    val usedBy = cards.count { if (fixed(it)) it.memoryProfileId == selected.id else selected.id == activeId }
                    Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f))) {
                        Column(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f), MaterialTheme.colorScheme.surfaceContainer)))) {
                            Row(Modifier.padding(start = 16.dp, top = 14.dp, end = 6.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Layers, null, Modifier.padding(top = 7.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(selected.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(if (selected.id == activeId) R.string.memory_workbench_global else R.string.memory_workbench_viewing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.memory_workbench_used_count, usedBy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                SpaceActions(selected, activeId, busy, { rename(selected) }, { deleting = selected }, { perform { preferences.setActiveMemorySpace(selected.id) } })
                            }
                            val summary = summaries[selected.id]
                            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                SpaceStat(summary?.memoryCount, stringResource(R.string.memory_workbench_stat_memories))
                                SpaceStat(summary?.linkCount, stringResource(R.string.memory_workbench_stat_links))
                                SpaceStat(summary?.folderCount, stringResource(R.string.memory_workbench_stat_folders))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                            Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { onOpenSpace(selected.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.memory_workbench_open), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (spaces.size > 1) item { Text(stringResource(R.string.memory_workbench_other_spaces), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(spaces.filter { it.id != selected.id }, key = { "space:${it.id}" }) { space ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clickable(enabled = !busy) { viewedId = space.id }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Layers, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(space.name, style = MaterialTheme.typography.bodyLarge)
                                val summary = summaries[space.id]
                                if (summary != null) Text(stringResource(R.string.memory_workbench_count_memories, summary.memoryCount) + " · " + stringResource(R.string.memory_workbench_count_links, summary.linkCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (space.id == activeId) Text(stringResource(R.string.memory_workbench_global), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
                        }
                        SpaceActions(space, activeId, busy, { rename(space) }, { deleting = space }, { perform { preferences.setActiveMemorySpace(space.id) } })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.memory_workbench_follow_hint), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(enabled = !busy, onClick = { chooseGlobal = true }) { Text(stringResource(R.string.memory_overview_change)) }
                    }
                    OverviewSection(stringResource(R.string.memory_overview_roles) + "  ${cards.size}") { Text(stringResource(R.string.memory_workbench_independent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    HorizontalDivider()
                    if (cards.isEmpty()) Text(stringResource(R.string.memory_workbench_no_characters), Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(cards, key = { "card:${it.id}" }) { card ->
                    val isFixed = fixed(card)
                    val name = if (isFixed) names[card.memoryProfileId] else names[activeId]
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { bindingCard = card }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MemoryCharacterAvatar(card, preferences)
                        Column(Modifier.weight(1f)) {
                            Text(card.name, style = MaterialTheme.typography.bodyLarge)
                            Text(name ?: stringResource(R.string.memory_overview_missing_space), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(if (isFixed) R.string.memory_workbench_fixed else R.string.memory_workbench_follow_global), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    OverviewSection(stringResource(R.string.memory_workbench_recent) + " · " + selected.name) {
                        TextButton(onClick = { onOpenRecent(selected.id, null) }) { Text(stringResource(R.string.memory_workbench_all)); Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp)) }
                    }
                    HorizontalDivider()
                }
                val recent = summaries[selected.id]?.recentMemories
                if (recent != null && recent.isEmpty()) item { Text(stringResource(R.string.memory_workbench_empty_recent), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(recent.orEmpty(), key = { "recent:${it.uuid}" }) { memory ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenRecent(selected.id, memory.uuid) }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (memory.isDocumentNode) Icons.Default.Description else Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(memory.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text((memory.folderPath ?: stringResource(R.string.memory_uncategorized)) + " · " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(memory.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                }
                item { Text(stringResource(R.string.memory_workbench_overview_footer), Modifier.fillMaxWidth().padding(vertical = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (chooseGlobal) MemoryChoiceSheet(
        title = stringResource(R.string.memory_overview_choose_global), choices = spaces.map { MemoryChoice(it.id, it.name) }, selected = activeId,
        onDismiss = { chooseGlobal = false }, onSelect = { id -> chooseGlobal = false; perform { preferences.setActiveMemorySpace(id) } }, description = stringResource(R.string.memory_workbench_follow_hint),
    )
    bindingCard?.let { card ->
        val selectedBinding = if (fixed(card)) card.memoryProfileId.orEmpty() else "follow"
        MemoryChoiceSheet(
            title = stringResource(R.string.memory_overview_binding_title, card.name),
            choices = listOf(MemoryChoice("follow", stringResource(R.string.memory_workbench_follow_global), names[activeId].orEmpty())) + spaces.map { MemoryChoice(it.id, it.name, stringResource(R.string.memory_workbench_fixed_hint)) },
            selected = selectedBinding, onDismiss = { bindingCard = null }, description = stringResource(R.string.memory_workbench_binding_hint),
            onSelect = { id ->
                bindingCard = null
                perform {
                    val latest = cardManager.getCharacterCard(card.id)
                    cardManager.updateCharacterCard(latest.copy(memoryProfileBindingMode = if (id == "follow") CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL else CharacterCardMemoryProfileBindingMode.FIXED_PROFILE, memoryProfileId = id.takeUnless { it == "follow" }))
                }
            },
        )
    }
    if (creating || renaming != null) {
        val target = renaming
        AlertDialog(onDismissRequest = { creating = false; renaming = null }, title = { Text(stringResource(if (target == null) R.string.memory_overview_create else R.string.memory_overview_rename)) }, text = {
            OutlinedTextField(value = draft, onValueChange = { draft = it; nameError = false }, label = { Text(stringResource(R.string.memory_overview_name)) }, singleLine = true, isError = nameError, supportingText = { if (nameError) Text(stringResource(R.string.memory_workbench_duplicate_name)) })
        }, confirmButton = {
            TextButton(enabled = !busy && draft.isNotBlank(), onClick = {
                val name = draft.trim()
                if (spaces.any { it.id != target?.id && it.name == name }) nameError = true else perform {
                    if (target == null) viewedId = preferences.createMemorySpace(name) else preferences.updateMemorySpace(target.copy(name = name))
                    creating = false; renaming = null
                }
            }) { Text(stringResource(R.string.save)) }
        }, dismissButton = { TextButton(onClick = { creating = false; renaming = null }) { Text(stringResource(R.string.cancel)) } })
    }
    deleting?.let { space ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.memory_overview_delete_title)) }, text = { Text(stringResource(R.string.memory_overview_delete_warning, space.name)) }, confirmButton = {
            TextButton(enabled = !busy, onClick = { perform { preferences.deleteMemorySpace(space.id); deleting = null } }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(R.string.memory_workbench_help)) }, text = { Text(stringResource(R.string.memory_workbench_help_body)) }, confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.memory_close)) } })
}

@Composable
private fun OverviewSection(title: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        action()
    }
}

@Composable
private fun SpaceStat(value: Int?, label: String) {
    Column(Modifier.widthIn(min = 64.dp)) {
        Text(value?.toString() ?: "—", style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpaceActions(space: MemorySpace, activeId: String, busy: Boolean, onRename: () -> Unit, onDelete: () -> Unit, onGlobal: () -> Unit) {
    var expanded by remember(space.id) { mutableStateOf(false) }
    Box {
        IconButton(enabled = !busy, onClick = { expanded = true }) { Icon(Icons.Default.MoreHoriz, stringResource(R.string.memory_overview_space_actions)) }
        MemoryMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { expanded = false; onRename() })
            DropdownMenuItem(text = { Text(stringResource(R.string.memory_overview_choose_global)) }, leadingIcon = { Icon(Icons.Default.Check, null) }, enabled = space.id != activeId, onClick = { expanded = false; onGlobal() })
            HorizontalDivider(Modifier.padding(horizontal = 12.dp))
            // 颜色交给菜单状态处理，固定红色会覆盖默认空间的禁用外观。
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                enabled = !busy && space.id != "default",
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@Composable
private fun MemoryCharacterAvatar(card: CharacterCard, preferences: UserPreferencesManager) {
    val avatar by remember(card.id, preferences) { preferences.getAiAvatarForCharacterCardFlow(card.id) }.collectAsState(initial = null)
    Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (avatar != null) Image(painter = rememberAsyncImagePainter(avatar), contentDescription = card.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.Person, card.name, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}