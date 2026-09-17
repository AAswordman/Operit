package com.ai.assistance.operit.ui.features.settings.keypool

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.ApiKeyPoolAvailabilityTester
import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiKeyInfo
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.util.AppLogger
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val MAX_EDITABLE_KEYS = 20

@Composable
fun ApiKeyPoolSettingsSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    saveCoordinator: ModelConfigSaveCoordinator,
    keyAvailabilityTester: ApiKeyPoolAvailabilityTester,
    showNotification: (String) -> Unit
) {
    val hidesApiKeyPool =
        ApiProviderType.fromProviderTypeId(config.apiProviderTypeId)
            ?.supportsApiKeyPool() == false

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var useApiKeyPool by remember(config.id) { mutableStateOf(config.useMultipleApiKeys) }
    var apiKeyPool by remember(config.id) { mutableStateOf(config.apiKeyPool) }
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<ApiKeyInfo?>(null) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf<ApiKeyPoolSelectionMode?>(null) }
    var pendingExportKeys by remember { mutableStateOf<List<ApiKeyInfo>>(emptyList()) }

    val keyTestState by keyAvailabilityTester.state.collectAsState()
    val keyPoolSaveMutex = remember(config.id) { Mutex() }

    data class ApiKeyPoolSaveState(
        val useMultipleApiKeys: Boolean,
        val apiKeyPool: List<ApiKeyInfo>
    )

    fun buildApiKeyPoolSaveState() =
        ApiKeyPoolSaveState(
            useMultipleApiKeys = useApiKeyPool,
            apiKeyPool = apiKeyPool
        )

    suspend fun persistApiKeyPool(state: ApiKeyPoolSaveState) {
        keyPoolSaveMutex.withLock {
            configManager.updateApiKeyPoolSettings(
                configId = config.id,
                useMultipleApiKeys = state.useMultipleApiKeys,
                apiKeyPool = state.apiKeyPool
            )
            EnhancedAIService.refreshAllServices(configManager.appContext)
        }
    }

    fun saveChanges(successMessage: String? = context.getString(R.string.api_key_pool_saved)) {
        val state = buildApiKeyPoolSaveState()
        scope.launch {
            try {
                keyAvailabilityTester.pauseAndJoin()
                persistApiKeyPool(state)
                if (!successMessage.isNullOrBlank()) {
                    showNotification(successMessage)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("ApiKeyPoolSettingsSection", "保存 API Key 池失败", e)
                showNotification(context.getString(R.string.save_failed))
            }
        }
    }

    RegisterModelConfigSaveAction(
        coordinator = saveCoordinator,
        key = "api-key-pool:${config.id}"
    ) { showSuccess ->
        keyAvailabilityTester.pauseAndJoin()
        persistApiKeyPool(buildApiKeyPoolSaveState())
        if (showSuccess) {
            showNotification(context.getString(R.string.api_key_pool_saved))
        }
    }

    fun exportKeyPoolToUri(uri: Uri, keys: List<ApiKeyInfo>) {
        scope.launch {
            try {
                if (keys.isEmpty()) {
                    showNotification(context.getString(R.string.no_valid_keys_found))
                    return@launch
                }
                val content = keys.joinToString("\n") { it.key }
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                } ?: throw IllegalStateException("openOutputStream returned null")

                showNotification(context.getString(R.string.exported_keys_count, keys.size))
            } catch (e: Exception) {
                showNotification(context.getString(R.string.export_keys_failed) + ": ${e.message}")
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

                    val keys = content.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    if (keys.isEmpty()) {
                        showNotification(context.getString(R.string.no_valid_keys_found))
                        return@launch
                    }

                    val newKeys = keys.map { key ->
                        ApiKeyInfo(
                            id = UUID.randomUUID().toString(),
                            name = context.getString(R.string.advanced_import_key, key.takeLast(4)),
                            key = key,
                            isEnabled = true
                        )
                    }

                    apiKeyPool = apiKeyPool + newKeys
                    saveChanges()
                    showNotification(context.getString(R.string.imported_keys_count, keys.size))
                } catch (e: Exception) {
                    showNotification(context.getString(R.string.batch_import_failed) + ": ${e.message}")
                }
            }
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            exportKeyPoolToUri(uri, pendingExportKeys)
        }
    }

    fun launchImport() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }

    fun openExportSheet() {
        if (apiKeyPool.isEmpty()) {
            showNotification(context.getString(R.string.no_valid_keys_found))
            return
        }
        selectionMode = ApiKeyPoolSelectionMode.EXPORT
    }

    fun openDeleteSheet() {
        if (apiKeyPool.isEmpty()) {
            showNotification(context.getString(R.string.no_valid_keys_found))
            return
        }
        selectionMode = ApiKeyPoolSelectionMode.DELETE
    }

    fun deleteSelectedKeys(keys: List<ApiKeyInfo>) {
        val ids = keys.map { it.id }.toSet()
        if (ids.isEmpty()) return
        apiKeyPool = apiKeyPool.filterNot { it.id in ids }
        saveChanges(context.getString(R.string.api_key_pool_deleted_count, ids.size))
    }

    fun openAddDialog() {
        editingKey = null
        showAddKeyDialog = true
    }

    fun toggleOrRunTest() {
        if (keyAvailabilityTester.isRunning()) {
            keyAvailabilityTester.pause()
            return
        }
        scope.launch {
            keyAvailabilityTester.startOrResume(
                context = context,
                baseConfig = config,
                useMultipleApiKeys = useApiKeyPool,
                apiKeyPool = apiKeyPool,
                concurrency = 5,
                onPoolUpdated = { apiKeyPool = it }
            )
        }
    }

    fun clearAvailabilityMarks() {
        apiKeyPool = apiKeyPool.map {
            it.copy(availabilityStatus = ApiKeyAvailabilityStatus.UNTESTED)
        }
        saveChanges()
    }

    val isLargeKeyPool = apiKeyPool.size > MAX_EDITABLE_KEYS

    if (hidesApiKeyPool) {
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.api_key_pool_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.api_key_pool_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (useApiKeyPool && apiKeyPool.isNotEmpty()) {
                    Box {
                        IconButton(
                            onClick = { showHeaderMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showHeaderMenu,
                            onDismissRequest = { showHeaderMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.api_key_pool_info_title)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    showInfoSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.api_key_pool_import_keys)) },
                                leadingIcon = {
                                    Icon(Icons.Default.FileUpload, contentDescription = null)
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    launchImport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_keys)) },
                                leadingIcon = {
                                    Icon(Icons.Default.FileDownload, contentDescription = null)
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    openExportSheet()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.api_key_pool_delete_keys)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    openDeleteSheet()
                                }
                            )
                        }
                    }
                }
                Switch(
                    checked = useApiKeyPool,
                    onCheckedChange = {
                        useApiKeyPool = it
                        saveChanges(
                            context.getString(
                                if (it) {
                                    R.string.api_key_pool_enabled
                                } else {
                                    R.string.api_key_pool_disabled
                                }
                            )
                        )
                    }
                )
            }

            AnimatedVisibility(
                visible = useApiKeyPool,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    when {
                        apiKeyPool.isEmpty() -> {
                            ApiKeyPoolEmptyState(
                                onAdd = ::openAddDialog,
                                onImport = ::launchImport,
                                onExport = ::openExportSheet
                            )
                        }
                        isLargeKeyPool -> {
                            ApiKeyPoolLargeState(
                                keyCount = apiKeyPool.size,
                                availableCount = apiKeyPool.count { it.isEnabled && it.availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE },
                                unavailableCount = apiKeyPool.count { it.isEnabled && it.availabilityStatus == ApiKeyAvailabilityStatus.UNAVAILABLE },
                                untestedCount = apiKeyPool.count { it.isEnabled && it.availabilityStatus == ApiKeyAvailabilityStatus.UNTESTED },
                                disabledCount = apiKeyPool.count { !it.isEnabled },
                                tested = keyTestState.tested,
                                totalToTest = keyTestState.totalToTest,
                                testCompleted = !keyAvailabilityTester.isRunning() &&
                                    !keyTestState.paused &&
                                    keyTestState.totalToTest > 0 &&
                                    keyTestState.tested >= keyTestState.totalToTest,
                                lastError = keyTestState.lastError,
                                testLabelRes =
                                    when {
                                        keyAvailabilityTester.isRunning() ->
                                            R.string.api_key_pool_test_pause
                                        keyTestState.paused -> R.string.api_key_pool_test_continue
                                        else -> R.string.api_key_pool_test_short
                                    },
                                onTest = ::toggleOrRunTest,
                                onClearMarks = ::clearAvailabilityMarks
                            )
                        }
                        else -> {
                            ApiKeyPoolListState(
                                keys = apiKeyPool,
                                availableCount = apiKeyPool.count { it.isEnabled && it.availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE },
                                unavailableCount = apiKeyPool.count { it.isEnabled && it.availabilityStatus == ApiKeyAvailabilityStatus.UNAVAILABLE },
                                untestedCount = apiKeyPool.count { it.isEnabled && it.availabilityStatus == ApiKeyAvailabilityStatus.UNTESTED },
                                disabledCount = apiKeyPool.count { !it.isEnabled },
                                tested = keyTestState.tested,
                                totalToTest = keyTestState.totalToTest,
                                testCompleted = !keyAvailabilityTester.isRunning() &&
                                    !keyTestState.paused &&
                                    keyTestState.totalToTest > 0 &&
                                    keyTestState.tested >= keyTestState.totalToTest,
                                lastError = keyTestState.lastError,
                                testLabelRes =
                                    when {
                                        keyAvailabilityTester.isRunning() ->
                                            R.string.api_key_pool_test_pause
                                        keyTestState.paused -> R.string.api_key_pool_test_continue
                                        else -> R.string.api_key_pool_test_short
                                    },
                                onEdit = { editingKey = it },
                                onDelete = { keyToDelete ->
                                    apiKeyPool = apiKeyPool.filter { it.id != keyToDelete.id }
                                    saveChanges()
                                },
                                onToggleEnabled = { keyToToggle ->
                                    apiKeyPool = apiKeyPool.map {
                                        if (it.id == keyToToggle.id) {
                                            it.copy(isEnabled = !it.isEnabled)
                                        } else {
                                            it
                                        }
                                    }
                                    saveChanges()
                                },
                                onReorder = { from, to ->
                                    if (from == to) return@ApiKeyPoolListState
                                    apiKeyPool = apiKeyPool.toMutableList().also { keys ->
                                        keys.add(to, keys.removeAt(from))
                                    }
                                    saveChanges(successMessage = null)
                                },
                                onTest = ::toggleOrRunTest,
                                onClearMarks = ::clearAvailabilityMarks,
                                onAdd = ::openAddDialog
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInfoSheet) {
        ApiKeyPoolInfoSheet(
            configId = config.id,
            keys = apiKeyPool,
            onDismiss = { showInfoSheet = false }
        )
    }

    selectionMode?.let { mode ->
        ApiKeyPoolSelectionSheet(
            mode = mode,
            keys = apiKeyPool,
            onDismiss = { selectionMode = null },
            onConfirm = { selected ->
                selectionMode = null
                when (mode) {
                    ApiKeyPoolSelectionMode.EXPORT -> {
                        pendingExportKeys = selected
                        exportFileLauncher.launch("api_keys.txt")
                    }
                    ApiKeyPoolSelectionMode.DELETE -> deleteSelectedKeys(selected)
                }
            }
        )
    }

    if (showAddKeyDialog || editingKey != null) {
        ApiKeyEditDialog(
            keyInfo = editingKey,
            onDismiss = {
                showAddKeyDialog = false
                editingKey = null
            },
            onSave = { keyInfo ->
                apiKeyPool =
                    if (editingKey == null) {
                        apiKeyPool + keyInfo
                    } else {
                        apiKeyPool.map { if (it.id == keyInfo.id) keyInfo else it }
                    }
                saveChanges()
                showAddKeyDialog = false
                editingKey = null
            }
        )
    }
}

@Composable
private fun ApiKeyPoolEmptyState(
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.api_key_pool_empty_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onAdd) {
                Text(stringResource(R.string.api_key_pool_add_short))
            }
            TextButton(onClick = onImport) {
                Text(stringResource(R.string.api_key_pool_import_short))
            }
            TextButton(onClick = onExport) {
                Text(stringResource(R.string.api_key_pool_export_short))
            }
        }
    }
}

@Composable
private fun ApiKeyPoolListState(
    keys: List<ApiKeyInfo>,
    availableCount: Int,
    unavailableCount: Int,
    untestedCount: Int,
    disabledCount: Int,
    tested: Int,
    totalToTest: Int,
    testCompleted: Boolean,
    lastError: String?,
    testLabelRes: Int,
    onEdit: (ApiKeyInfo) -> Unit,
    onDelete: (ApiKeyInfo) -> Unit,
    onToggleEnabled: (ApiKeyInfo) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onTest: () -> Unit,
    onClearMarks: () -> Unit,
    onAdd: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
    ) {
        itemsIndexed(
            items = keys,
            key = { _, keyInfo -> keyInfo.id }
        ) { _, keyInfo ->
            ReorderableItem(
                reorderableState,
                key = keyInfo.id,
                animateItemModifier = Modifier.animateItem(
                    fadeInSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                    placementSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                    fadeOutSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                )
            ) { isDragging ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isDragging) {
                                Modifier.shadow(8.dp, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        ),
                    color = if (isDragging) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    ApiKeyPoolRow(
                        keyInfo = keyInfo,
                        dragHandleModifier = Modifier.longPressDraggableHandle(),
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onToggleEnabled = onToggleEnabled
                    )
                }
            }
        }
    }

    ApiKeyPoolStatusAndActions(
        availableCount = availableCount,
        unavailableCount = unavailableCount,
        untestedCount = untestedCount,
        disabledCount = disabledCount,
        tested = tested,
        totalToTest = totalToTest,
        testCompleted = testCompleted,
        lastError = lastError,
        testLabelRes = testLabelRes,
        onTest = onTest,
        onClearMarks = onClearMarks,
        onAdd = onAdd
    )
}

@Composable
private fun ApiKeyPoolLargeState(
    keyCount: Int,
    availableCount: Int,
    unavailableCount: Int,
    untestedCount: Int,
    disabledCount: Int,
    tested: Int,
    totalToTest: Int,
    testCompleted: Boolean,
    lastError: String?,
    testLabelRes: Int,
    onTest: () -> Unit,
    onClearMarks: () -> Unit
) {
    Text(
        text = stringResource(R.string.api_key_pool_keys_count, keyCount),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = stringResource(R.string.api_key_pool_large_hint, MAX_EDITABLE_KEYS),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    ApiKeyPoolStatusAndActions(
        availableCount = availableCount,
        unavailableCount = unavailableCount,
        untestedCount = untestedCount,
        disabledCount = disabledCount,
        tested = tested,
        totalToTest = totalToTest,
        testCompleted = testCompleted,
        lastError = lastError,
        testLabelRes = testLabelRes,
        onTest = onTest,
        onClearMarks = onClearMarks,
        onAdd = null
    )
}

@Composable
private fun ApiKeyPoolStatusAndActions(
    availableCount: Int,
    unavailableCount: Int,
    untestedCount: Int,
    disabledCount: Int,
    tested: Int,
    totalToTest: Int,
    testCompleted: Boolean,
    lastError: String?,
    testLabelRes: Int,
    onTest: () -> Unit,
    onClearMarks: () -> Unit,
    onAdd: (() -> Unit)?
) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(
            R.string.api_key_pool_test_progress,
            availableCount,
            unavailableCount,
            untestedCount,
            disabledCount
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (totalToTest > 0) {
        Text(
            text = stringResource(
                if (testCompleted) {
                    R.string.api_key_pool_test_run_completed
                } else {
                    R.string.api_key_pool_test_run_progress
                },
                tested,
                totalToTest
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (!lastError.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.api_key_pool_test_failed, lastError),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onTest) {
            Text(stringResource(testLabelRes))
        }
        TextButton(onClick = onClearMarks) {
            Text(stringResource(R.string.api_key_pool_clear_marks))
        }
        if (onAdd != null) {
            TextButton(onClick = onAdd) {
                Text(stringResource(R.string.api_key_pool_add_short))
            }
        }
    }
}

@Composable
private fun ApiKeyPoolRow(
    keyInfo: ApiKeyInfo,
    dragHandleModifier: Modifier = Modifier,
    onEdit: (ApiKeyInfo) -> Unit,
    onDelete: (ApiKeyInfo) -> Unit,
    onToggleEnabled: (ApiKeyInfo) -> Unit
) {
    var showRowMenu by remember { mutableStateOf(false) }
    val statusColor = keyInfo.statusColor()
    val statusLabel = keyInfo.statusLabel()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (keyInfo.isEnabled) 1f else 0.55f)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = stringResource(R.string.api_key_pool_reorder),
            modifier = dragHandleModifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(statusColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = keyInfo.name.ifBlank { maskApiKey(keyInfo.key) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = maskApiKey(keyInfo.key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(
                onClick = { showRowMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = statusLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = showRowMenu,
                onDismissRequest = { showRowMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_api_key)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        showRowMenu = false
                        onEdit(keyInfo)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (keyInfo.isEnabled) {
                                    R.string.api_key_pool_disable
                                } else {
                                    R.string.api_key_pool_enable
                                }
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (keyInfo.isEnabled) Icons.Default.Block
                                else Icons.Default.CheckCircle,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showRowMenu = false
                        onToggleEnabled(keyInfo)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_api_key)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showRowMenu = false
                        onDelete(keyInfo)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyEditDialog(
    keyInfo: ApiKeyInfo?,
    onDismiss: () -> Unit,
    onSave: (ApiKeyInfo) -> Unit
) {
    var key by remember { mutableStateOf(keyInfo?.key ?: "") }
    val isNew = keyInfo == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNew) stringResource(R.string.add_api_key)
                else stringResource(R.string.edit_api_key)
            )
        },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.api_key)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = "API Key ${key.takeLast(4)}"
                    val newKeyInfo = keyInfo?.copy(name = finalName, key = key)
                        ?: ApiKeyInfo(
                            id = UUID.randomUUID().toString(),
                            name = finalName,
                            key = key,
                            isEnabled = true
                        )
                    onSave(newKeyInfo)
                },
                enabled = key.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
}

internal fun maskApiKey(key: String): String {
    val last4 = key.takeLast(4)
    val prefix =
        when {
            key.startsWith("sk-") -> "sk-"
            key.startsWith("AIza") -> "AIza"
            else -> ""
        }
    return "${prefix}••••$last4"
}
