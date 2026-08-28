package com.ai.assistance.operit.ui.features.settings.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.preferences.MemorySpaceProfileDocumentRepository
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.settings.components.rememberMarkdownSyntaxOutputTransformation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * User-facing profile manager. Profile ids stay identical to memory-space ids because existing
 * ObjectBox databases and character-card bindings already use those stable ids.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPreferencesSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMemory: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { MemorySpaceProfileDocumentRepository.getInstance(context) }
    val preferencesManager = remember(context) { UserPreferencesManager.getInstance(context) }
    val displayPreferencesManager = remember(context) {
        DisplayPreferencesManager.getInstance(context)
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val profileIds by preferencesManager.memorySpaceListFlow.collectAsState(initial = emptyList())
    val activeProfileId by preferencesManager.activeMemorySpaceIdFlow.collectAsState(initial = "default")

    val draftEditorState = rememberTextFieldState()
    val editorScrollState = rememberScrollState()
    val markdownSyntaxOutputTransformation = rememberMarkdownSyntaxOutputTransformation()
    var profiles by remember { mutableStateOf<List<MemorySpace>>(emptyList()) }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var loadedProfile by remember { mutableStateOf<MemorySpace?>(null) }
    var savedMarkdown by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var initialized by remember { mutableStateOf(false) }
    var loadingProfile by remember { mutableStateOf(true) }
    var savingDocument by remember { mutableStateOf(false) }
    var savingPolicy by remember { mutableStateOf(false) }
    var selectorExpanded by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showPolicySheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBackDiscardDialog by remember { mutableStateOf(false) }
    var showSelectionDiscardDialog by remember { mutableStateOf(false) }
    var showMemoryDiscardDialog by remember { mutableStateOf(false) }
    var pendingProfileId by remember { mutableStateOf<String?>(null) }
    var editedName by remember { mutableStateOf("") }

    val draftMarkdown = draftEditorState.text.toString()
    val hasUnsavedChanges = draftMarkdown != savedMarkdown
    val exceedsLimit = draftMarkdown.length > MemorySpaceProfileDocumentRepository.MAX_CONTENT_CHARS
    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId } ?: loadedProfile
    val selectedProfileName = selectedProfile?.name ?: ""
    val selectedIsActive = selectedProfileId == activeProfileId

    LaunchedEffect(Unit) {
        try {
            // Released data must migrate before a new default entry can be created.
            repository.initialize()
            preferencesManager.ensureDefaultMemorySpace(context.getString(R.string.default_profile))
        } catch (error: Exception) {
            snackbarHostState.showSnackbar(error.message ?: error.javaClass.simpleName)
        } finally {
            initialized = true
        }
    }

    LaunchedEffect(profileIds, initialized) {
        if (!initialized) return@LaunchedEffect
        try {
            profiles = profileIds.map { id -> preferencesManager.getMemorySpaceFlow(id).first() }
            if (selectedProfileId !in profileIds) {
                selectedProfileId = activeProfileId.takeIf { it in profileIds } ?: profileIds.firstOrNull()
            }
        } catch (error: Exception) {
            snackbarHostState.showSnackbar(error.message ?: error.javaClass.simpleName)
        }
    }

    LaunchedEffect(selectedProfileId, initialized, profileIds) {
        val profileId = selectedProfileId ?: return@LaunchedEffect
        if (!initialized || profileId !in profileIds) return@LaunchedEffect
        loadingProfile = true
        loadedProfile = null
        try {
            val profile = preferencesManager.getMemorySpaceFlow(profileId).first()
            val markdown = repository.load(profileId)
            loadedProfile = profile
            savedMarkdown = markdown
            draftEditorState.edit {
                replace(0, length, markdown)
                selection = TextRange(0)
            }
            editorScrollState.scrollTo(0)
        } catch (error: Exception) {
            snackbarHostState.showSnackbar(error.message ?: error.javaClass.simpleName)
        } finally {
            loadingProfile = false
        }
    }

    fun selectProfile(profileId: String) {
        selectorExpanded = false
        if (profileId == selectedProfileId) return
        if (hasUnsavedChanges) {
            pendingProfileId = profileId
            showSelectionDiscardDialog = true
        } else {
            selectedProfileId = profileId
        }
    }

    fun saveDocument() {
        val profileId = selectedProfileId ?: return
        val markdown = draftMarkdown
        scope.launch {
            savingDocument = true
            try {
                repository.save(profileId, markdown)
                savedMarkdown = markdown
                snackbarHostState.showSnackbar(context.getString(R.string.save_successful))
            } catch (error: Exception) {
                snackbarHostState.showSnackbar(error.message ?: error.javaClass.simpleName)
            } finally {
                savingDocument = false
            }
        }
    }

    fun persistPolicy(updatedProfile: MemorySpace) {
        val previousProfile = loadedProfile ?: return
        savingPolicy = true
        loadedProfile = updatedProfile
        profiles = profiles.map { profile ->
            if (profile.id == updatedProfile.id) updatedProfile else profile
        }
        scope.launch {
            try {
                preferencesManager.updateMemorySpace(updatedProfile)
            } catch (error: Exception) {
                loadedProfile = previousProfile
                profiles = profiles.map { profile ->
                    if (profile.id == previousProfile.id) previousProfile else profile
                }
                snackbarHostState.showSnackbar(error.message ?: error.javaClass.simpleName)
            } finally {
                savingPolicy = false
            }
        }
    }

    fun navigateBackSafely() {
        if (hasUnsavedChanges) showBackDiscardDialog = true else onNavigateBack()
    }

    fun openSelectedMemory() {
        val profileId = selectedProfileId ?: return
        scope.launch {
            try {
                preferencesManager.setActiveMemorySpace(profileId)
                onNavigateToMemory()
            } catch (error: Exception) {
                snackbarHostState.showSnackbar(error.message ?: error.javaClass.simpleName)
            }
        }
    }

    BackHandler(onBack = ::navigateBackSafely)

    CustomScaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .fillMaxHeight()
                        .widthIn(max = 840.dp)
                        .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GlobalIdentitySection(
                            context = context,
                            displayPreferencesManager = displayPreferencesManager,
                            scope = scope,
                        )
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { selectorExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text =
                                        if (selectedProfileName.isBlank()) {
                                            stringResource(R.string.user_configuration_select)
                                        } else {
                                            selectedProfileName
                                        },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selectedIsActive) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription =
                                            stringResource(R.string.user_configuration_active),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription =
                                        stringResource(R.string.user_configuration_select)
                                )
                            }
                            DropdownMenu(
                                expanded = selectorExpanded,
                                onDismissRequest = { selectorExpanded = false }
                            ) {
                                profiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = profile.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingIcon = {
                                            if (profile.id == activeProfileId) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = { selectProfile(profile.id) }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { showCreateDialog = true },
                            enabled = !hasUnsavedChanges,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription =
                                    stringResource(R.string.user_configuration_create),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (hasUnsavedChanges) {
                                    showMemoryDiscardDialog = true
                                } else {
                                    openSelectedMemory()
                                }
                            },
                            enabled = selectedProfile != null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription =
                                    stringResource(R.string.user_configuration_open_memory),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showProfileMenu = true },
                                enabled = selectedProfile != null,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showProfileMenu,
                                onDismissRequest = { showProfileMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.user_configuration_policy))
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Tune, contentDescription = null)
                                    },
                                    enabled = loadedProfile != null,
                                    onClick = {
                                        showProfileMenu = false
                                        showPolicySheet = true
                                    }
                                )
                                if (!selectedIsActive) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.set_active)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        },
                                        onClick = {
                                            showProfileMenu = false
                                            selectedProfileId?.let { profileId ->
                                                scope.launch {
                                                    try {
                                                        preferencesManager.setActiveMemorySpace(profileId)
                                                    } catch (error: Exception) {
                                                        snackbarHostState.showSnackbar(
                                                            error.message ?: error.javaClass.simpleName
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.user_configuration_rename))
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        showProfileMenu = false
                                        editedName = selectedProfileName
                                        showRenameDialog = true
                                    }
                                )
                                if (selectedProfileId != "default") {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text =
                                                    stringResource(
                                                        R.string.user_configuration_delete
                                                    ),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showProfileMenu = false
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            label = { Text(stringResource(R.string.user_md_edit_tab)) }
                        )
                        FilterChip(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            label = { Text(stringResource(R.string.user_md_preview_tab)) }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        FilledTonalButton(
                            onClick = ::saveDocument,
                            enabled =
                                loadedProfile != null &&
                                    hasUnsavedChanges &&
                                    !exceedsLimit &&
                                    !savingDocument
                        ) {
                            if (savingDocument) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.save_action))
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    if (loadingProfile || loadedProfile == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                if (selectedTab == 0) {
                                    BasicTextField(
                                        state = draftEditorState,
                                        scrollState = editorScrollState,
                                        modifier = Modifier.fillMaxSize().padding(16.dp),
                                        textStyle =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                        outputTransformation = markdownSyntaxOutputTransformation,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        decorator = { innerTextField ->
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                if (draftMarkdown.isEmpty()) {
                                                    Text(
                                                        text =
                                                            stringResource(
                                                                R.string.user_md_editor_placeholder
                                                            ),
                                                        style =
                                                            MaterialTheme.typography.bodyMedium.copy(
                                                                fontFamily = FontFamily.Monospace
                                                            ),
                                                        color =
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                } else if (draftMarkdown.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.user_md_preview_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                                    )
                                } else {
                                    Box(
                                        modifier =
                                            Modifier.fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(16.dp)
                                    ) {
                                        MarkdownTextComposable(
                                            text = draftMarkdown,
                                            textColor = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text =
                                        "${draftMarkdown.length} / " +
                                            MemorySpaceProfileDocumentRepository.MAX_CONTENT_CHARS,
                                    style = MaterialTheme.typography.labelMedium,
                                    color =
                                        if (exceedsLimit) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val policyProfile = loadedProfile
    if (showPolicySheet && policyProfile != null) {
        ModalBottomSheet(onDismissRequest = { showPolicySheet = false }) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(
                    modifier =
                        Modifier.align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .widthIn(max = 640.dp)
                            .padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.user_configuration_policy),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).padding(start = 10.dp)
                        )
                        if (savingPolicy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Text(
                            text = stringResource(R.string.user_configuration_auto_update),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                        )
                        Switch(
                            checked = policyProfile.profileAutoUpdateEnabled,
                            onCheckedChange = { enabled ->
                                persistPolicy(
                                    policyProfile.copy(profileAutoUpdateEnabled = enabled)
                                )
                            },
                            enabled = !savingPolicy
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Text(
                            text = stringResource(R.string.user_configuration_lock),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                        )
                        Switch(
                            checked = policyProfile.profileAutoUpdateLocked,
                            onCheckedChange = { locked ->
                                persistPolicy(
                                    policyProfile.copy(profileAutoUpdateLocked = locked)
                                )
                            },
                            enabled = !savingPolicy
                        )
                    }

                    Text(
                        text = stringResource(R.string.user_configuration_auto_update_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showCreateDialog || showRenameDialog) {
        val creating = showCreateDialog
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                showRenameDialog = false
                editedName = ""
            },
            title = {
                Text(
                    stringResource(
                        if (creating) R.string.user_configuration_create
                        else R.string.user_configuration_rename
                    )
                )
            },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(R.string.user_configuration_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = editedName.trim()
                        scope.launch {
                            try {
                                if (creating) {
                                    val profileId = preferencesManager.createMemorySpace(name)
                                    preferencesManager.setActiveMemorySpace(profileId)
                                    selectedProfileId = profileId
                                } else {
                                    selectedProfileId?.let { profileId ->
                                        val profile =
                                            preferencesManager.getMemorySpaceFlow(profileId).first()
                                        val updatedProfile = profile.copy(name = name)
                                        preferencesManager.updateMemorySpace(updatedProfile)
                                        loadedProfile = updatedProfile
                                        profiles = profiles.map { item ->
                                            if (item.id == profileId) updatedProfile else item
                                        }
                                    }
                                }
                                showCreateDialog = false
                                showRenameDialog = false
                                editedName = ""
                            } catch (error: Exception) {
                                snackbarHostState.showSnackbar(
                                    error.message ?: error.javaClass.simpleName
                                )
                            }
                        }
                    },
                    enabled = editedName.isNotBlank()
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        showRenameDialog = false
                        editedName = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.user_configuration_delete)) },
            text = {
                Text(
                    stringResource(
                        R.string.user_configuration_delete_warning,
                        selectedProfileName
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedProfileId?.let { profileId ->
                            scope.launch {
                                try {
                                    preferencesManager.deleteMemorySpace(profileId)
                                    showDeleteDialog = false
                                } catch (error: Exception) {
                                    snackbarHostState.showSnackbar(
                                        error.message ?: error.javaClass.simpleName
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.confirm_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    if (showSelectionDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showSelectionDiscardDialog = false
                pendingProfileId = null
            },
            title = { Text(stringResource(R.string.user_md_unsaved_title)) },
            text = { Text(stringResource(R.string.user_md_unsaved_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedProfileId = pendingProfileId
                        pendingProfileId = null
                        showSelectionDiscardDialog = false
                    }
                ) {
                    Text(stringResource(R.string.user_md_discard))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSelectionDiscardDialog = false
                        pendingProfileId = null
                    }
                ) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    if (showBackDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showBackDiscardDialog = false },
            title = { Text(stringResource(R.string.user_md_unsaved_title)) },
            text = { Text(stringResource(R.string.user_md_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = onNavigateBack) {
                    Text(stringResource(R.string.user_md_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackDiscardDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    if (showMemoryDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showMemoryDiscardDialog = false },
            title = { Text(stringResource(R.string.user_md_unsaved_title)) },
            text = { Text(stringResource(R.string.user_md_unsaved_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMemoryDiscardDialog = false
                        draftEditorState.edit {
                            replace(0, length, savedMarkdown)
                            selection = TextRange(0)
                        }
                        openSelectedMemory()
                    }
                ) {
                    Text(stringResource(R.string.user_md_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMemoryDiscardDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }
}
