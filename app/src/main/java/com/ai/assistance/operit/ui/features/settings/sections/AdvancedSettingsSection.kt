package com.ai.assistance.operit.ui.features.settings.sections

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.ApiKeyPoolAvailabilityTester
import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiKeyInfo
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.settings.ModelConfigSaveCoordinator
import com.ai.assistance.operit.ui.features.settings.RegisterModelConfigSaveAction
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

@Composable
fun AdvancedSettingsSection(
    config: ModelConfigData,
    configManager: ModelConfigManager,
    saveCoordinator: ModelConfigSaveCoordinator,
    keyAvailabilityTester: ApiKeyPoolAvailabilityTester,
    showNotification: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var useApiKeyPool by remember(config.id) { mutableStateOf(config.useMultipleApiKeys) }
    var apiKeyPool by remember(config.id) { mutableStateOf(config.apiKeyPool) }
    var showRequestQueueControls by remember(config.id) { mutableStateOf(false) }
    var requestLimitPerMinuteInput by
        remember(config.id) {
            mutableStateOf(
                config.requestLimitPerMinute
                    .takeIf { it > 0 }
                    ?.toString()
                    .orEmpty()
            )
        }
    var maxConcurrentRequestsInput by
        remember(config.id) {
            mutableStateOf(
                config.maxConcurrentRequests
                    .takeIf { it > 0 }
                    ?.toString()
                    .orEmpty()
            )
        }

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<ApiKeyInfo?>(null) }
    var showClearPoolConfirmDialog by remember { mutableStateOf(false) }

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

    fun saveChanges() {
        val state = buildApiKeyPoolSaveState()
        scope.launch {
            try {
                keyAvailabilityTester.pauseAndJoin()
                persistApiKeyPool(state)
                showNotification(context.getString(R.string.advanced_settings_saved))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("AdvancedSettingsSection", "保存 API Key 池失败", e)
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
            showNotification(context.getString(R.string.advanced_settings_saved))
        }
    }

    data class RequestQueueControlState(
        val requestLimitPerMinute: Int,
        val maxConcurrentRequests: Int
    )

    suspend fun persistRequestQueueControlState(state: RequestQueueControlState) {
        configManager.updateRequestQueueSettings(
            configId = config.id,
            requestLimitPerMinute = state.requestLimitPerMinute,
            maxConcurrentRequests = state.maxConcurrentRequests
        )
        EnhancedAIService.refreshAllServices(configManager.appContext)
    }

    LaunchedEffect(config.id) {
        snapshotFlow {
            RequestQueueControlState(
                requestLimitPerMinute = requestLimitPerMinuteInput.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                maxConcurrentRequests = maxConcurrentRequestsInput.toIntOrNull()?.coerceAtLeast(0) ?: 0
            )
        }
            .drop(1)
            .debounce(700)
            .distinctUntilChanged()
            .collectLatest { state ->
                runCatching {
                    persistRequestQueueControlState(state)
                }.onFailure { e ->
                    showNotification(context.getString(R.string.save_failed) + ": " + (e.message ?: ""))
                }
            }
    }

    fun exportKeyPoolToUri(uri: Uri) {
        scope.launch {
            try {
                val content = apiKeyPool.joinToString("\n") { it.key }
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                } ?: throw IllegalStateException("openOutputStream returned null")

                showNotification(context.getString(R.string.exported_keys_count, apiKeyPool.size))
            } catch (e: Exception) {
                showNotification(context.getString(R.string.export_keys_failed) + ": ${e.message}")
            }
        }
    }

    // 文件选择器
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

                    val newKeys = keys.mapIndexed { _, key ->
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
            exportKeyPoolToUri(uri)
        }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.advanced_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRequestQueueControls = !showRequestQueueControls },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.request_queue_controls),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.request_queue_controls_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector =
                                if (showRequestQueueControls) Icons.Default.KeyboardArrowUp
                                else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(
                        visible = showRequestQueueControls,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsTextField(
                                title = stringResource(R.string.request_limit_per_minute),
                                subtitle = stringResource(R.string.request_limit_per_minute_desc),
                                value = requestLimitPerMinuteInput,
                                onValueChange = {
                                    requestLimitPerMinuteInput = it.filter { ch -> ch.isDigit() }
                                },
                                placeholder = "0",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                )
                            )

                            SettingsTextField(
                                title = stringResource(R.string.max_concurrent_requests),
                                subtitle = stringResource(R.string.max_concurrent_requests_desc),
                                value = maxConcurrentRequestsInput,
                                onValueChange = {
                                    maxConcurrentRequestsInput = it.filter { ch -> ch.isDigit() }
                                },
                                placeholder = "0",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                )
                            )
                        }
                    }
                }
            }

            // API Key Pool Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable {
                        useApiKeyPool = !useApiKeyPool
                        saveChanges()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.use_api_key_pool),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.api_key_pool_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useApiKeyPool,
                    onCheckedChange = {
                        useApiKeyPool = it
                        saveChanges()
                    }
                )
            }

            // API Key Pool Management UI
            AnimatedVisibility(
                visible = useApiKeyPool,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    val maxEditableKeys = 20
                    val isLargeKeyPool = apiKeyPool.size > maxEditableKeys

                    if (apiKeyPool.isEmpty()) {
                        Text(
                            stringResource(R.string.api_key_pool_empty),
                            modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isLargeKeyPool) {
                        Text(
                            text = stringResource(R.string.api_key_pool_keys_count, apiKeyPool.size),
                            modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.api_key_pool_large_hint, maxEditableKeys),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column {
                            apiKeyPool.forEach { keyInfo ->
                                ApiKeyItem(
                                    keyInfo = keyInfo,
                                    onEdit = { editingKey = it },
                                    onDelete = { keyToDelete ->
                                        apiKeyPool = apiKeyPool.filter { it.id != keyToDelete.id }
                                        saveChanges()
                                    }
                                )
                            }
                        }
                    }

                    if (apiKeyPool.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(
                                R.string.api_key_pool_test_progress,
                                apiKeyPool.count { it.availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE },
                                apiKeyPool.count { it.availabilityStatus == ApiKeyAvailabilityStatus.UNAVAILABLE },
                                apiKeyPool.count { it.availabilityStatus == ApiKeyAvailabilityStatus.UNTESTED }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (keyTestState.totalToTest > 0) {
                            Text(
                                text = stringResource(
                                    R.string.api_key_pool_test_run_progress,
                                    keyTestState.tested,
                                    keyTestState.totalToTest
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!keyTestState.lastError.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.api_key_pool_test_failed, keyTestState.lastError ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val concurrency = 5
                                        keyAvailabilityTester.startOrResume(
                                            context = context,
                                            baseConfig = config,
                                            useMultipleApiKeys = useApiKeyPool,
                                            apiKeyPool = apiKeyPool,
                                            concurrency = concurrency,
                                            onPoolUpdated = {
                                                apiKeyPool = it
                                            }
                                        )
                                    }
                                },
                                enabled = !keyAvailabilityTester.isRunning(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (keyTestState.paused) {
                                        stringResource(R.string.api_key_pool_test_continue)
                                    } else {
                                        stringResource(R.string.api_key_pool_test_start)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = { keyAvailabilityTester.pause() },
                                enabled = keyAvailabilityTester.isRunning(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.api_key_pool_test_pause))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                apiKeyPool = apiKeyPool.map {
                                    it.copy(availabilityStatus = ApiKeyAvailabilityStatus.UNTESTED)
                                }
                                saveChanges()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.api_key_pool_clear_marks))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isLargeKeyPool) {
                        Button(
                            onClick = { editingKey = null; showAddKeyDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.add_api_key))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "text/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            filePickerLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.batch_import_keys))
                    }

                    OutlinedButton(
                        onClick = {
                            exportFileLauncher.launch("api_keys.txt")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_keys))
                    }

                    if (apiKeyPool.isNotEmpty() && isLargeKeyPool) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showClearPoolConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.clear_api_key_pool))
                        }
                    }
                    
                    Text(
                        text = stringResource(R.string.import_format_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                    )
                }
            }
        }
    }

    if (showClearPoolConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearPoolConfirmDialog = false },
            title = { Text(stringResource(R.string.clear_api_key_pool)) },
            text = { Text(stringResource(R.string.clear_api_key_pool_confirm_message, apiKeyPool.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        apiKeyPool = emptyList()
                        saveChanges()
                        showClearPoolConfirmDialog = false
                        showNotification(context.getString(R.string.api_key_pool_cleared))
                    }
                ) {
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPoolConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    if (showAddKeyDialog || editingKey != null) {
        ApiKeyEditDialog(
            keyInfo = editingKey,
            onDismiss = { showAddKeyDialog = false; editingKey = null },
            onSave = { keyInfo ->
                if (editingKey == null) { // Add new key
                    apiKeyPool = apiKeyPool + keyInfo
                } else { // Update existing key
                    apiKeyPool = apiKeyPool.map { if (it.id == keyInfo.id) keyInfo else it }
                }
                saveChanges()
                showAddKeyDialog = false
                editingKey = null
            }
        )
    }
}

@Composable
private fun ApiKeyItem(
    keyInfo: ApiKeyInfo,
    onEdit: (ApiKeyInfo) -> Unit,
    onDelete: (ApiKeyInfo) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${keyInfo.name} (...${keyInfo.key.takeLast(4)})",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector =
                when (keyInfo.availabilityStatus) {
                    ApiKeyAvailabilityStatus.AVAILABLE -> Icons.Default.CheckCircle
                    ApiKeyAvailabilityStatus.UNAVAILABLE -> Icons.Default.Cancel
                    ApiKeyAvailabilityStatus.UNTESTED -> Icons.Default.HelpOutline
                },
            contentDescription = null,
            tint =
                when (keyInfo.availabilityStatus) {
                    ApiKeyAvailabilityStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
                    ApiKeyAvailabilityStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
                    ApiKeyAvailabilityStatus.UNTESTED -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.size(16.dp)
        )
        IconButton(
            onClick = { onEdit(keyInfo) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Edit, 
                contentDescription = stringResource(R.string.edit_api_key), 
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(
            onClick = { onDelete(keyInfo) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Delete, 
                contentDescription = stringResource(R.string.delete_api_key), 
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
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
        title = { Text(if (isNew) stringResource(R.string.add_api_key) else stringResource(R.string.edit_api_key)) },
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
                        ?: ApiKeyInfo(id = UUID.randomUUID().toString(), name = finalName, key = key, isEnabled = true)
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
