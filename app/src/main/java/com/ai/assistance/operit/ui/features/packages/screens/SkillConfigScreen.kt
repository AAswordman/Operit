package com.ai.assistance.operit.ui.features.packages.screens

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.skill.SkillPackage
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.preferences.SkillVisibilityPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable
import com.ai.assistance.operit.ui.features.packages.market.MarketUpdateCheckButton
import com.ai.assistance.operit.ui.features.packages.market.readMarketInstallMarkerForSkill
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SkillConfigScreen(
    skillRepository: SkillRepository,
    snackbarHostState: SnackbarHostState,
    onNavigateToSkillMarket: () -> Unit = {},
    onOpenSkillMarketDetail: (MarketV2Entry) -> Unit = {},
    searchQuery: String = "",
    skillOrder: List<String> = emptyList(),
    onSaveSkillOrder: (List<String>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val skillVisibilityPreferences = remember { SkillVisibilityPreferences.getInstance(context) }

    var skills by remember { mutableStateOf<Map<String, SkillPackage>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedSkill by remember { mutableStateOf<SkillPackage?>(null) }
    var selectedSkillDetail by remember { mutableStateOf<SkillDetailDialogData?>(null) }
    var isSkillDetailLoading by remember { mutableStateOf(false) }
    var skillLoadErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showSkillLoadErrorsDialog by remember { mutableStateOf(false) }

    var showImportDialog by remember { mutableStateOf(false) }
    var importTabIndex by remember { mutableStateOf(0) }
    var repoUrlInput by remember { mutableStateOf("") }
    var zipUri by remember { mutableStateOf<Uri?>(null) }
    var zipFileName by remember { mutableStateOf("") }
    var manualSkillId by remember { mutableStateOf("") }
    var manualSkillDescription by remember { mutableStateOf("") }
    var manualSkillContent by remember { mutableStateOf("") }
    var manualAttachments by remember { mutableStateOf<List<ManualImportAttachment>>(emptyList()) }
    var isImporting by remember { mutableStateOf(false) }

    val refreshSkills: suspend () -> Unit = {
        isLoading = true
        try {
            val loaded =
                withContext(Dispatchers.IO) {
                    skillRepository.getAvailableSkillPackagesSnapshot()
                }
            skills = loaded.first
            skillLoadErrors = loaded.second
        } finally {
            isLoading = false
        }
    }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            zipUri = it
            zipFileName = resolveUriDisplayName(context, it, fallback = "skill.zip")
        }
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        val existingKeys = manualAttachments.map { it.uri.toString() }.toMutableSet()
        val updated = manualAttachments.toMutableList()
        uris.forEach { uri ->
            if (existingKeys.add(uri.toString())) {
                updated += ManualImportAttachment(
                    uri = uri,
                    displayName = resolveUriDisplayName(context, uri)
                )
            }
        }
        manualAttachments = updated
    }

    LaunchedEffect(Unit) {
        refreshSkills()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.skills),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            modifier = Modifier.size(24.dp),
                            onClick = {
                                scope.launch {
                                    refreshSkills()
                                    snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_refreshed))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = skillRepository.getSkillsDirectoryPath(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val displayedSkills =
                remember(skills, searchQuery, skillOrder) {
                    val searchText = searchQuery.trim()
                    val filtered = skills.values
                        .filter { skill ->
                            searchText.isEmpty() ||
                                skill.name.contains(searchText, ignoreCase = true) ||
                                skill.description.contains(searchText, ignoreCase = true) ||
                                skill.directory.absolutePath.contains(searchText, ignoreCase = true)
                        }
                    if (searchText.isEmpty() && skillOrder.isNotEmpty()) {
                        val orderIndex = skillOrder.withIndex().associate { (i, name) -> name to i }
                        filtered.sortedBy { orderIndex[it.name] ?: Int.MAX_VALUE }
                    } else {
                        filtered.sortedBy { it.name }
                    }
                }

            if (skills.isEmpty()) {
                Text(
                    text = stringResource(R.string.skillmgr_no_skills_found, skillRepository.getSkillsDirectoryPath()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                var orderedSkills by remember(displayedSkills) {
                    mutableStateOf(displayedSkills)
                }
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    orderedSkills = orderedSkills.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                    val newOrder = orderedSkills.map { it.name }
                    onSaveSkillOrder(newOrder)
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    if (orderedSkills.isEmpty()) {
                        item(key = "empty_skill_search_state") {
                            Text(
                                text = stringResource(R.string.no_matching_skills_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    itemsIndexed(
                        items = orderedSkills,
                        key = { _, skill -> skill.name }
                    ) { index, skill ->
                        ReorderableItem(
                            reorderableState,
                            key = skill.name,
                            animateItemModifier = Modifier.animateItem(
                            fadeInSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                            placementSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                            fadeOutSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                        )
                        ) { isDragging ->
                            val elevation = if (isDragging) 8.dp else 0.dp
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isDragging) {
                                            Modifier.shadow(elevation, RoundedCornerShape(14.dp))
                                        } else {
                                            Modifier
                                        }
                                    ),
                                color = if (isDragging) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                SkillListItem(
                                    skill = skill,
                                    skillVisibilityPreferences = skillVisibilityPreferences,
                                    onClick = {
                                        val targetPath = skill.directory.absolutePath
                                        selectedSkill = skill
                                        selectedSkillDetail = null
                                        isSkillDetailLoading = true
                                        scope.launch {
                                            val detail =
                                                withContext(Dispatchers.IO) {
                                                    buildSkillDetailDialogData(
                                                        skill = skill,
                                                        skillContent = skillRepository.readSkillContent(skill.name).orEmpty()
                                                    )
                                                }
                                            if (selectedSkill?.directory?.absolutePath == targetPath) {
                                                selectedSkillDetail = detail
                                                isSkillDetailLoading = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.longPressDraggableHandle()
                                )
                            }
                            if (index < orderedSkills.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (skillLoadErrors.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick = { showSkillLoadErrorsDialog = true },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = stringResource(R.string.error_occurred_simple)
                    )
                }
            }

            FloatingActionButton(
                onClick = onNavigateToSkillMarket,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Store,
                    contentDescription = stringResource(R.string.screen_title_skill_market)
                )
            }

            FloatingActionButton(
                onClick = { showImportDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.import_action)
                )
            }
        }

        if (skills.isEmpty() && (isLoading || isImporting)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) showImportDialog = false },
            title = { Text(stringResource(R.string.import_or_install_skill)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = importTabIndex,
                        edgePadding = 8.dp,
                        divider = {},
                        indicator = { tabPositions ->
                            if (importTabIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    with(TabRowDefaults) { Modifier.tabIndicatorOffset(tabPositions[importTabIndex]) }
                                )
                            }
                        }
                    ) {
                        Tab(
                            selected = importTabIndex == 0,
                            onClick = { importTabIndex = 0 },
                            text = { Text(stringResource(R.string.import_from_repo), maxLines = 1) }
                        )
                        Tab(
                            selected = importTabIndex == 1,
                            onClick = { importTabIndex = 1 },
                            text = { Text(stringResource(R.string.import_from_zip), maxLines = 1) }
                        )
                        Tab(
                            selected = importTabIndex == 2,
                            onClick = { importTabIndex = 2 },
                            text = { Text(stringResource(R.string.import_from_direct), maxLines = 1) }
                        )
                    }

                    when (importTabIndex) {
                        0 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.enter_repo_info),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    enabled = !isImporting,
                                    onClick = {
                                        showImportDialog = false
                                        onNavigateToSkillMarket()
                                    }
                                ) {
                                    Text(stringResource(R.string.get_skill))
                                }
                            }

                            OutlinedTextField(
                                value = repoUrlInput,
                                onValueChange = { repoUrlInput = it },
                                label = { Text(stringResource(R.string.repo_link)) },
                                placeholder = { Text("https://github.com/username/repo") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                enabled = !isImporting
                            )
                        }

                        1 -> {
                            Text(
                                text = stringResource(R.string.select_skill_plugin_zip),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = zipFileName,
                                    onValueChange = { },
                                    label = { Text(stringResource(R.string.skill_zip_package)) },
                                    placeholder = { Text(stringResource(R.string.select_zip_file)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    readOnly = true,
                                    enabled = !isImporting
                                )

                                IconButton(
                                    enabled = !isImporting,
                                    onClick = { zipPicker.launch("application/zip") }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = stringResource(R.string.select_file)
                                    )
                                }
                            }
                        }

                        2 -> {
                            OutlinedTextField(
                                value = manualSkillId,
                                onValueChange = { manualSkillId = it },
                                label = { Text(stringResource(R.string.skillmgr_direct_skill_id)) },
                                placeholder = { Text(stringResource(R.string.skillmgr_direct_skill_id_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isImporting
                            )

                            OutlinedTextField(
                                value = manualSkillDescription,
                                onValueChange = { manualSkillDescription = it },
                                label = { Text(stringResource(R.string.skillmgr_direct_description)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isImporting
                            )

                            OutlinedTextField(
                                value = manualSkillContent,
                                onValueChange = { manualSkillContent = it },
                                label = { Text(stringResource(R.string.skillmgr_direct_content)) },
                                placeholder = { Text(stringResource(R.string.skillmgr_direct_content_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 6,
                                maxLines = 10,
                                enabled = !isImporting
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.attachments_count, manualAttachments.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )

                                TextButton(
                                    enabled = !isImporting,
                                    onClick = { attachmentPicker.launch(arrayOf("*/*")) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = stringResource(R.string.add_attachment))
                                }
                            }

                            if (manualAttachments.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    manualAttachments.forEach { attachment ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = attachment.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            IconButton(
                                                enabled = !isImporting,
                                                onClick = {
                                                    manualAttachments = manualAttachments.filterNot { it.uri == attachment.uri }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = stringResource(R.string.remove_attachment),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = stringResource(R.string.skillmgr_direct_files_saved_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isImporting) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.processing))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = {
                        scope.launch {
                            when (importTabIndex) {
                                0 -> {
                                    val url = repoUrlInput.trim()
                                    if (url.isBlank()) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.enter_repo_info))
                                        return@launch
                                    }
                                    isImporting = true
                                    try {
                                        val result = skillRepository.importSkillFromGitHubRepo(url)
                                        refreshSkills()
                                        snackbarHostState.showSnackbar(result)
                                        showImportDialog = false
                                    } finally {
                                        isImporting = false
                                    }
                                }

                                1 -> {
                                    val uri = zipUri
                                    if (uri == null) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.select_zip_file))
                                        return@launch
                                    }
                                    isImporting = true
                                    try {
                                        val nameToUse = zipFileName.ifBlank { "skill.zip" }
                                        if (!nameToUse.endsWith(".zip", ignoreCase = true)) {
                                            snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_only_zip_files))
                                            return@launch
                                        }

                                        val result = withContext(Dispatchers.IO) {
                                            val tempFile = File(context.cacheDir, nameToUse)
                                            try {
                                                context.contentResolver.openInputStream(uri)?.use { input ->
                                                    tempFile.outputStream().use { output ->
                                                        input.copyTo(output)
                                                    }
                                                } ?: throw IllegalStateException(context.getString(R.string.skillmgr_cannot_read_file))

                                                skillRepository.importSkillFromZip(tempFile)
                                            } finally {
                                                try {
                                                    tempFile.delete()
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }

                                        refreshSkills()
                                        snackbarHostState.showSnackbar(result)
                                        showImportDialog = false
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_import_failed, e.message ?: ""))
                                    } finally {
                                        isImporting = false
                                    }
                                }

                                2 -> {
                                    val skillId = manualSkillId.trim()
                                    val skillContent = manualSkillContent.trim()
                                    if (skillId.isBlank()) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_direct_skill_id_required))
                                        return@launch
                                    }
                                    if (!isValidSkillId(skillId)) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_direct_skill_id_invalid))
                                        return@launch
                                    }
                                    if (skillContent.isBlank()) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_direct_content_required))
                                        return@launch
                                    }

                                    isImporting = true
                                    try {
                                        val result = skillRepository.importSkillFromDirectInput(
                                            skillId = skillId,
                                            description = manualSkillDescription.trim(),
                                            content = skillContent,
                                            attachmentUris = manualAttachments.map { it.uri }
                                        )
                                        refreshSkills()
                                        snackbarHostState.showSnackbar(result)
                                        showImportDialog = false
                                        manualSkillId = ""
                                        manualSkillDescription = ""
                                        manualSkillContent = ""
                                        manualAttachments = emptyList()
                                    } finally {
                                        isImporting = false
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.import_action))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = { showImportDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSkillLoadErrorsDialog) {
        SkillLoadErrorsDialog(
            errors = skillLoadErrors,
            onDismiss = { showSkillLoadErrorsDialog = false }
        )
    }

    selectedSkill?.let { skill ->
        SkillDetailDialog(
            skill = skill,
            detail = selectedSkillDetail,
            isLoading = isSkillDetailLoading,
            onOpenMarketDetail = { entry ->
                selectedSkill = null
                selectedSkillDetail = null
                isSkillDetailLoading = false
                onOpenSkillMarketDetail(entry)
            },
            onDismiss = {
                selectedSkill = null
                selectedSkillDetail = null
                isSkillDetailLoading = false
            },
            onDelete = {
                val skillName = skill.name
                scope.launch {
                    val ok = skillRepository.deleteSkill(skillName)
                    if (ok) {
                        refreshSkills()
                        snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_deleted, skillName))
                    } else {
                        snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_delete_failed, skillName))
                    }
                }
                selectedSkill = null
                selectedSkillDetail = null
                isSkillDetailLoading = false
            }
        )
    }
}

private data class ManualImportAttachment(
    val uri: Uri,
    val displayName: String
)

private data class SkillDetailDialogData(
    val skillContent: String,
    val directoryPath: String,
    val skillFilePath: String,
    val fileCount: Int,
    val folderCount: Int,
    val directoryPreview: String,
    val hiddenEntryCount: Int
)

private val SKILL_ID_PATTERN = Regex("^[A-Za-z0-9._-]+$")

private fun isValidSkillId(skillId: String): Boolean {
    return SKILL_ID_PATTERN.matches(skillId) && skillId != "." && skillId != ".."
}

private fun resolveUriDisplayName(context: Context, uri: Uri, fallback: String = "file"): String {
    val resolver = context.contentResolver
    val displayName = runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }.getOrNull()

    if (!displayName.isNullOrBlank()) {
        return displayName
    }

    val uriFallback = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    return uriFallback.ifBlank { fallback }
}

private fun buildSkillDetailDialogData(
    skill: SkillPackage,
    skillContent: String
): SkillDetailDialogData {
    val previewLines = mutableListOf<String>()
    var fileCount = 0
    var folderCount = 0
    val previewLimit = 18

    fun sortedChildren(parent: File): List<File> {
        return parent.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }

    fun addPreviewLine(depth: Int, label: String) {
        if (previewLines.size < previewLimit) {
            previewLines += "${"  ".repeat(depth)}$label"
        }
    }

    fun walk(node: File, depth: Int) {
        if (node.isDirectory) {
            folderCount += 1
            addPreviewLine(depth, "${node.name}/")
            sortedChildren(node).forEach { child ->
                walk(child, depth + 1)
            }
        } else {
            fileCount += 1
            addPreviewLine(depth, node.name)
        }
    }

    sortedChildren(skill.directory).forEach { child ->
        walk(child, depth = 0)
    }

    val totalEntries = fileCount + folderCount
    return SkillDetailDialogData(
        skillContent = skillContent,
        directoryPath = skill.directory.absolutePath,
        skillFilePath = skill.skillFile.absolutePath,
        fileCount = fileCount,
        folderCount = folderCount,
        directoryPreview = previewLines.joinToString("\n"),
        hiddenEntryCount = (totalEntries - previewLines.size).coerceAtLeast(0)
    )
}

@Composable
private fun SkillDetailDialog(
    skill: SkillPackage,
    detail: SkillDetailDialogData?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onOpenMarketDetail: (MarketV2Entry) -> Unit
) {
    var showSkillMarkdown by remember(skill.directory.absolutePath) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = skill.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading || detail == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.skillmgr_detail_loading),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    if (skill.description.isNotBlank()) {
                        SkillDetailSection(title = stringResource(R.string.skillmgr_direct_description)) {
                            Text(
                                text = skill.description,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    SkillDetailSection(title = stringResource(R.string.skillmgr_detail_directory)) {
                        Text(
                            text = detail.directoryPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    SkillDetailSection(title = stringResource(R.string.skillmgr_detail_entry_file)) {
                        Text(
                            text = detail.skillFilePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    SkillDetailSection(title = stringResource(R.string.skillmgr_detail_imported_contents)) {
                        Text(
                            text = stringResource(
                                R.string.skillmgr_detail_counts,
                                detail.fileCount,
                                detail.folderCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = detail.directoryPreview,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        if (detail.hiddenEntryCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.skillmgr_detail_more_items,
                                    detail.hiddenEntryCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(
                        onClick = { showSkillMarkdown = !showSkillMarkdown }
                    ) {
                        Text(
                            text = if (showSkillMarkdown) {
                                stringResource(R.string.skillmgr_detail_hide_skill_md)
                            } else {
                                stringResource(R.string.skillmgr_detail_show_skill_md)
                            }
                        )
                    }

                    if (showSkillMarkdown) {
                        SkillDetailSection(title = stringResource(R.string.skillmgr_detail_skill_md)) {
                            MarkdownTextComposable(
                                text = detail.skillContent,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                MarketUpdateCheckButton(
                    markerProvider = { readMarketInstallMarkerForSkill(skill.directory) },
                    onOpenMarketDetail = onOpenMarketDetail
                )
                TextButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.skillmgr_delete))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.skillmgr_close))
            }
        }
    )
}

@Composable
private fun SkillDetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        content()
    }
}

@Composable
private fun SkillLoadErrorsDialog(
    errors: Map<String, String>,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.error_occurred_simple)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            ) {
                errors.toSortedMap().forEach { (skillFolderName, errorText) ->
                    Text(
                        text = skillFolderName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        }
    )
}

@Composable
private fun SkillListItem(
    skill: SkillPackage,
    skillVisibilityPreferences: SkillVisibilityPreferences,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    var visibleToAi by remember(skill.name) {
        mutableStateOf(skillVisibilityPreferences.isSkillVisibleToAi(skill.name))
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .width(3.dp)
                    .height(22.dp),
                color = accentColor,
                shape = RoundedCornerShape(2.dp)
            ) {}
            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = accentColor
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (skill.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                modifier = Modifier.scale(0.8f),
                checked = visibleToAi,
                onCheckedChange = { checked ->
                    visibleToAi = checked
                    skillVisibilityPreferences.setSkillVisibleToAi(skill.name, checked)
                }
            )
        }
    }
}
