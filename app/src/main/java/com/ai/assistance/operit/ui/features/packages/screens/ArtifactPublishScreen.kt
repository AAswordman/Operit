package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2ManifestCategory
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketScope
import com.ai.assistance.operit.ui.features.packages.market.ArtifactPublishClusterContext
import com.ai.assistance.operit.ui.features.packages.market.GitHubForgePublishService
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactType
import com.ai.assistance.operit.ui.features.packages.market.PublishProgressStage
import com.ai.assistance.operit.ui.features.packages.market.isOperit2VersionAllowed
import com.ai.assistance.operit.ui.features.packages.market.sameArtifactRuntimePackageId
import com.ai.assistance.operit.ui.features.packages.screens.artifact.viewmodel.ArtifactMarketViewModel
import kotlinx.coroutines.launch

private data class ArtifactPublishEditInfo(
    val type: PublishArtifactType?,
    val title: String,
    val description: String,
    val detail: String,
    val categoryId: String,
    val allowPublicUpdates: Boolean,
    val version: String,
    val minSupportedAppVersion: String?,
    val maxSupportedAppVersion: String?,
    val runtimePackageId: String,
    val normalizedId: String,
    val sourceFileName: String
)

private fun com.ai.assistance.operit.data.api.MarketV2Entry.toArtifactPublishEditInfo(): ArtifactPublishEditInfo {
    val versionValue = latestVersion
    val artifactValue = artifact
    val assetValue = assets.firstOrNull { it.versionId == versionValue?.id }
    return ArtifactPublishEditInfo(
        type = PublishArtifactType.fromWireValue(type),
        title = title,
        description = description,
        detail = detail,
        categoryId = categoryId,
        allowPublicUpdates = allowPublicUpdates,
        version = versionValue?.version.orEmpty(),
        minSupportedAppVersion = versionValue?.minAppVer,
        maxSupportedAppVersion = versionValue?.maxAppVer,
        runtimePackageId = versionValue?.runtimePackageId.orEmpty(),
        normalizedId = id,
        sourceFileName = assetValue?.assetName.orEmpty().ifBlank { assetValue?.name.orEmpty() }
    )
}

fun com.ai.assistance.operit.data.api.MarketV2Entry.toArtifactPublishClusterContext(): ArtifactPublishClusterContext {
    val versionValue = latestVersion
    return ArtifactPublishClusterContext(
        entryId = id,
        projectId = versionValue?.projectId?.ifBlank { artifact?.projectId.orEmpty() }.orEmpty(),
        runtimePackageId = versionValue?.runtimePackageId.orEmpty(),
        lockedDisplayName = title,
        projectDisplayName = title,
        projectDescription = detail.ifBlank { description },
        categoryId = categoryId
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactPublishScreen(
    onNavigateBack: () -> Unit,
    editingEntry: com.ai.assistance.operit.data.api.MarketV2Entry? = null,
    publishContext: ArtifactPublishClusterContext? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isEditMode = editingEntry != null
    val viewModel: ArtifactMarketViewModel =
        viewModel(
            key = "artifact-publish-all",
            factory = ArtifactMarketViewModel.Factory(context.applicationContext, ArtifactMarketScope.ALL)
        )

    val artifacts by viewModel.publishableArtifacts.collectAsState()
    val publishStage by viewModel.publishProgressStage.collectAsState()
    val publishMessage by viewModel.publishMessage.collectAsState()
    val publishError by viewModel.publishErrorMessage.collectAsState()
    val publishSuccess by viewModel.publishSuccessMessage.collectAsState()
    val requiresForgeInitialization by viewModel.requiresForgeInitialization.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    val initialInfo = remember(editingEntry) { editingEntry?.toArtifactPublishEditInfo() }
    var mutablePublishContext by remember(isEditMode, publishContext) {
        mutableStateOf(if (isEditMode) null else publishContext)
    }
    val activePublishContext = if (isEditMode) null else mutablePublishContext
    val isContinuationMode = activePublishContext != null
    val lockedRuntimePackageId = initialInfo?.runtimePackageId?.ifBlank { initialInfo.normalizedId }.orEmpty()
    val lockedDisplayName = activePublishContext?.lockedDisplayName?.trim().orEmpty()
    val isDisplayNameLocked = !isEditMode && lockedDisplayName.isNotBlank()
    val continuationDescription =
        stringResource(R.string.artifact_publish_continuation_description)

    val filteredArtifacts =
        remember(artifacts, activePublishContext, isEditMode, lockedRuntimePackageId) {
            val runtimePackageId =
                if (isEditMode) {
                    lockedRuntimePackageId
                } else {
                    activePublishContext?.runtimePackageId
                }
            if (runtimePackageId.isNullOrBlank()) {
                artifacts
            } else {
                artifacts.filter {
                    sameArtifactRuntimePackageId(it.packageName, runtimePackageId)
                }
            }
        }

    var selectedPackageName by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable(initialInfo?.title, lockedDisplayName) {
        mutableStateOf(
            initialInfo?.title.orEmpty().ifBlank { lockedDisplayName }
        )
    }
    var description by rememberSaveable(activePublishContext?.projectDescription) {
        mutableStateOf(initialInfo?.description.orEmpty().ifBlank { activePublishContext?.projectDescription.orEmpty() })
    }
    var detail by rememberSaveable(activePublishContext?.projectDescription) {
        mutableStateOf(initialInfo?.detail.orEmpty().ifBlank { activePublishContext?.projectDescription.orEmpty() })
    }
    var categoryId by rememberSaveable(activePublishContext?.categoryId) {
        mutableStateOf(initialInfo?.categoryId.orEmpty().ifBlank { activePublishContext?.categoryId.orEmpty() })
    }
    var allowPublicUpdates by rememberSaveable(initialInfo?.allowPublicUpdates) {
        mutableStateOf(initialInfo?.allowPublicUpdates ?: true)
    }
    var encryptArtifact by rememberSaveable { mutableStateOf(false) }
    var version by rememberSaveable { mutableStateOf(initialInfo?.version.orEmpty().ifBlank { "1.0.0" }) }
    var minSupportedAppVersion by rememberSaveable { mutableStateOf(initialInfo?.minSupportedAppVersion.orEmpty()) }
    var maxSupportedAppVersion by rememberSaveable { mutableStateOf(initialInfo?.maxSupportedAppVersion.orEmpty()) }

    var selectorExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<MarketV2ManifestCategory>>(emptyList()) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showOperit2WarningDialog by remember { mutableStateOf(false) }
    var showSecondForgeConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshPublishableArtifacts()
        MarketStatsApiService().getManifest().fold(
            onSuccess = { manifest -> categories = manifest.categories.filter { it.id.isNotBlank() } },
            onFailure = {}
        )
    }

    LaunchedEffect(filteredArtifacts, activePublishContext?.runtimePackageId, initialInfo?.normalizedId) {
        if (selectedPackageName.isBlank()) {
            val preferredRuntimePackageId =
                if (isEditMode) {
                    lockedRuntimePackageId.takeIf { it.isNotBlank() }
                } else {
                    activePublishContext?.runtimePackageId?.takeIf { it.isNotBlank() } ?: initialInfo?.normalizedId
                }
            val matched =
                filteredArtifacts.firstOrNull {
                    preferredRuntimePackageId != null &&
                        sameArtifactRuntimePackageId(it.packageName, preferredRuntimePackageId)
                } ?: filteredArtifacts.firstOrNull()
            if (matched != null) {
                selectedPackageName = matched.packageName
                if (!isEditMode && initialInfo == null) {
                    displayName = if (isDisplayNameLocked) lockedDisplayName else matched.displayName
                    if (!isContinuationMode) {
                        description = matched.description
                        detail = matched.description
                    }
                    version = matched.inferredVersion ?: "1.0.0"
                }
            } else if (isEditMode && preferredRuntimePackageId != null) {
                selectedPackageName = preferredRuntimePackageId
            }
        }
    }

    val selectedArtifact = filteredArtifacts.firstOrNull { it.packageName == selectedPackageName }
    val selectedType = selectedArtifact?.type ?: initialInfo?.type
    val isPublishing = publishStage !in listOf(PublishProgressStage.IDLE, PublishProgressStage.COMPLETED)
    val selectorDisplayName =
        if (isEditMode) {
            selectedArtifact?.displayName
                ?: initialInfo?.title.orEmpty().ifBlank {
                    initialInfo?.sourceFileName.orEmpty().ifBlank { lockedRuntimePackageId }
                }
        } else {
            selectedArtifact?.displayName.orEmpty()
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text =
                        when {
                            isEditMode -> stringResource(R.string.artifact_publish_edit_artifact_title)
                            isContinuationMode -> stringResource(R.string.artifact_publish_continue_on_version_title)
                            else -> stringResource(R.string.publish_description)
                        },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text =
                        when {
                            isEditMode -> stringResource(R.string.artifact_publish_edit_artifact_description)
                            isContinuationMode -> continuationDescription
                            else -> stringResource(R.string.artifact_publish_info_description)
                        },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (isEditMode && initialInfo != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val artifactTypeLabel =
                        when (initialInfo.type) {
                            PublishArtifactType.PACKAGE -> stringResource(R.string.artifact_type_package)
                            PublishArtifactType.SCRIPT -> stringResource(R.string.artifact_type_script)
                            null -> ""
                        }
                    val summaryText =
                        buildString {
                            if (artifactTypeLabel.isNotBlank()) {
                                append(artifactTypeLabel)
                            }
                            initialInfo.sourceFileName.takeIf { it.isNotBlank() }?.let {
                                if (isNotBlank()) append(" · ")
                                append(context.getString(R.string.artifact_publish_file_locked))
                            }
                        }.ifBlank { context.getString(R.string.artifact_publish_only_description_versions_editable) }
                    Text(
                        text = stringResource(R.string.artifact_publish_current_artifact),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(initialInfo.title.ifBlank { selectorDisplayName })
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (activePublishContext != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.artifact_publish_publish_update_version),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (lockedDisplayName.isNotBlank()) {
                        Text(
                            text = stringResource(
                                R.string.artifact_publish_locked_plugin_name_hint,
                                lockedDisplayName
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.artifact_publish_package_name_auto_inherited),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!isLoggedIn) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    stringResource(R.string.need_login_before_publish_artifact),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        if (!isEditMode && activePublishContext != null && filteredArtifacts.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    text = stringResource(R.string.artifact_publish_missing_local_continuation_artifact),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        if (isEditMode) {
            OutlinedTextField(
                value = selectorDisplayName,
                onValueChange = {},
                label = { Text(stringResource(R.string.local_artifact_entry)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                supportingText = {
                    selectedType?.let {
                        Text(
                            if (it == PublishArtifactType.PACKAGE) {
                                stringResource(R.string.publish_target_package_market)
                            } else {
                                stringResource(R.string.publish_target_script_market)
                            }
                        )
                    }
                }
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = selectorExpanded,
                onExpandedChange = {
                    if (filteredArtifacts.isNotEmpty()) {
                        selectorExpanded = !selectorExpanded
                    }
                }
            ) {
                OutlinedTextField(
                    value = selectorDisplayName,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.local_artifact_entry)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    enabled = filteredArtifacts.isNotEmpty(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = selectorExpanded)
                    },
                    supportingText = {
                        if (selectedType != null) {
                            Text(
                                text =
                                    if (selectedType == PublishArtifactType.PACKAGE) {
                                        stringResource(R.string.publish_target_package_market)
                                    } else {
                                        stringResource(R.string.publish_target_script_market)
                                    }
                            )
                        }
                    }
                )
                ExposedDropdownMenu(
                    expanded = selectorExpanded,
                    onDismissRequest = { selectorExpanded = false }
                ) {
                    filteredArtifacts.forEach { artifact ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(artifact.displayName)
                                    Text(
                                        text =
                                            if (artifact.type == PublishArtifactType.PACKAGE) {
                                                stringResource(R.string.artifact_type_package)
                                            } else {
                                                stringResource(R.string.artifact_type_script)
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                viewModel.clearPendingMarketRegistrationRetry()
                                selectedPackageName = artifact.packageName
                                selectorExpanded = false
                                if (initialInfo == null) {
                                    displayName = if (isDisplayNameLocked) lockedDisplayName else artifact.displayName
                                    description = artifact.description
                                    detail = artifact.description
                                    version = artifact.inferredVersion ?: "1.0.0"
                                }
                            }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = displayName,
            onValueChange = {
                if (!isEditMode && !isDisplayNameLocked) {
                    viewModel.clearPendingMarketRegistrationRetry()
                    displayName = it
                }
            },
            label = { Text(stringResource(R.string.display_name_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = isEditMode || isDisplayNameLocked,
            supportingText = {
                if (isEditMode) {
                    Text(stringResource(R.string.artifact_publish_published_name_readonly))
                } else if (isDisplayNameLocked) {
                    Text(stringResource(R.string.artifact_publish_locked_name_must_match_source))
                }
            }
        )
        OutlinedTextField(
            value = description,
            onValueChange = {
                viewModel.clearPendingMarketRegistrationRetry()
                description = it
            },
            label = { Text(stringResource(R.string.description_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        OutlinedTextField(
            value = detail,
            onValueChange = {
                viewModel.clearPendingMarketRegistrationRetry()
                detail = it
            },
            label = { Text(stringResource(R.string.market_detail_section_details)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 10
        )
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = {
                if (categories.isNotEmpty()) {
                    categoryExpanded = !categoryExpanded
                }
            }
        ) {
            val selectedCategoryLabel =
                categoryId
                    .takeIf { it.isNotBlank() }
                    ?.let { selected -> marketCategoryLabel(selected) }
                    .orEmpty()
            OutlinedTextField(
                value = selectedCategoryLabel,
                onValueChange = {},
                label = { Text(stringResource(R.string.market_detail_category_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                readOnly = true,
                enabled = categories.isNotEmpty(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                },
                isError = categoryId.isBlank()
            )
            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(marketCategoryLabel(category.id)) },
                        onClick = {
                            viewModel.clearPendingMarketRegistrationRetry()
                            categoryId = category.id
                            categoryExpanded = false
                        }
                    )
                }
            }
        }
        if (!isContinuationMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.market_allow_public_updates_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.market_allow_public_updates_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = allowPublicUpdates,
                        onCheckedChange = {
                            viewModel.clearPendingMarketRegistrationRetry()
                            allowPublicUpdates = it
                        }
                    )
                }
            }
        }
        if (!isEditMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.artifact_publish_encrypt_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.artifact_publish_encrypt_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = encryptArtifact,
                        onCheckedChange = {
                            viewModel.clearPendingMarketRegistrationRetry()
                            encryptArtifact = it
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = version,
            onValueChange = {
                if (!isEditMode) {
                    viewModel.clearPendingMarketRegistrationRetry()
                    version = it
                }
            },
            label = { Text(stringResource(R.string.version_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = isEditMode,
            supportingText = {
                if (isEditMode) {
                    Text(stringResource(R.string.artifact_publish_published_version_readonly))
                }
            }
        )
        OutlinedTextField(
            value = minSupportedAppVersion,
            onValueChange = {
                viewModel.clearPendingMarketRegistrationRetry()
                minSupportedAppVersion = it
            },
            label = { Text(stringResource(R.string.min_supported_app_version)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text(stringResource(R.string.min_supported_version_input_hint)) }
        )
        OutlinedTextField(
            value = maxSupportedAppVersion,
            onValueChange = {
                viewModel.clearPendingMarketRegistrationRetry()
                maxSupportedAppVersion = it
            },
            label = { Text(stringResource(R.string.max_supported_app_version)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text(stringResource(R.string.max_supported_version_input_hint)) }
        )

        publishError?.let { error ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.publish_failed_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Button(
            onClick = {
                if (isOperit2VersionAllowed(maxSupportedAppVersion)) {
                    showOperit2WarningDialog = true
                } else {
                    showConfirmationDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled =
                isLoggedIn &&
                    displayName.isNotBlank() &&
                    description.isNotBlank() &&
                    categoryId.isNotBlank() &&
                    !isPublishing &&
                    (
                        if (isEditMode) {
                            initialInfo?.type != null
                        } else {
                            selectedPackageName.isNotBlank() &&
                                (activePublishContext == null || filteredArtifacts.isNotEmpty())
                        }
                    )
        ) {
            if (isPublishing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                when {
                    isEditMode -> stringResource(R.string.artifact_publish_save_artifact_info)
                    isContinuationMode -> stringResource(R.string.artifact_publish_publish_update_version)
                    else -> stringResource(R.string.publish_to_market)
                }
            )
        }

        OutlinedButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    }

    if (publishMessage != null && isPublishing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.publishing_progress)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = publishMessage.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {}
        )
    }

    if (showConfirmationDialog && (selectedArtifact != null || isEditMode)) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = {
                Text(
                    when {
                        isEditMode -> stringResource(R.string.artifact_publish_confirm_save_artifact_info)
                        isContinuationMode -> stringResource(R.string.artifact_publish_confirm_publish_update_version)
                        else -> stringResource(R.string.confirm_publish)
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isEditMode) {
                        Text(stringResource(R.string.artifact_publish_edit_confirmation_message))
                        Text(stringResource(R.string.description_colon, description))
                        if (detail.isNotBlank()) {
                            Text(stringResource(R.string.detail_colon, detail))
                        }
                        Text(stringResource(R.string.market_detail_category_label) + ": " + categoryId)
                        Text(
                            stringResource(
                                R.string.supported_app_versions_colon,
                                minSupportedAppVersion.ifBlank { "-" },
                                maxSupportedAppVersion.ifBlank { GitHubForgePublishService.DEFAULT_MAX_SUPPORTED_APP_VERSION }
                            )
                        )
                    } else {
                        Text(
                            stringResource(R.string.please_check_submitted_info)
                        )
                        if (isContinuationMode) {
                            Text(continuationDescription)
                        }
                        Text(stringResource(R.string.name_colon, displayName))
                        Text(stringResource(R.string.description_colon, description))
                        if (detail.isNotBlank()) {
                            Text(stringResource(R.string.detail_colon, detail))
                        }
                        Text(stringResource(R.string.market_detail_category_label) + ": " + categoryId)
                        Text(stringResource(R.string.version_colon, version))
                        Text(
                            stringResource(
                                R.string.artifact_publish_encrypt_confirmation,
                                if (encryptArtifact) {
                                    stringResource(R.string.enabled)
                                } else {
                                    stringResource(R.string.disabled)
                                }
                            )
                        )
                        Text(
                            stringResource(
                                R.string.artifact_type_colon,
                                when (selectedType) {
                                    PublishArtifactType.PACKAGE -> stringResource(R.string.artifact_type_package)
                                    PublishArtifactType.SCRIPT -> stringResource(R.string.artifact_type_script)
                                    null -> "-"
                                }
                            )
                        )
                        Text(
                            stringResource(
                                R.string.supported_app_versions_colon,
                                minSupportedAppVersion.ifBlank { "-" },
                                maxSupportedAppVersion.ifBlank { GitHubForgePublishService.DEFAULT_MAX_SUPPORTED_APP_VERSION }
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmationDialog = false
                        if (isEditMode && editingEntry != null) {
                            viewModel.updatePublishedArtifact(
                                entry = editingEntry,
                                displayName = displayName,
                                description = description,
                                detail = detail,
                                categoryId = categoryId,
                                allowPublicUpdates = allowPublicUpdates,
                                minSupportedAppVersion = minSupportedAppVersion.ifBlank { null },
                                maxSupportedAppVersion = maxSupportedAppVersion.ifBlank { GitHubForgePublishService.DEFAULT_MAX_SUPPORTED_APP_VERSION }
                            )
                        } else {
                            viewModel.requestPublish(
                                packageName = selectedPackageName,
                                displayName = displayName,
                                description = description,
                                detail = detail,
                                categoryId = categoryId,
                                allowPublicUpdates = allowPublicUpdates,
                                version = version,
                                minSupportedAppVersion = minSupportedAppVersion.ifBlank { null },
                                maxSupportedAppVersion = maxSupportedAppVersion.ifBlank { GitHubForgePublishService.DEFAULT_MAX_SUPPORTED_APP_VERSION },
                                publishContext = activePublishContext,
                                encryptArtifact = encryptArtifact
                            )
                        }
                    }
                ) {
                    Text(
                        when {
                            isEditMode -> stringResource(R.string.artifact_publish_confirm_save_artifact_info)
                            isContinuationMode -> stringResource(R.string.artifact_publish_confirm_publish_update_version)
                            else -> stringResource(R.string.confirm_publish)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showOperit2WarningDialog) {
        AlertDialog(
            onDismissRequest = { showOperit2WarningDialog = false },
            title = { Text(stringResource(R.string.operit2_version_warning_title)) },
            text = { Text(stringResource(R.string.operit2_version_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOperit2WarningDialog = false
                        showConfirmationDialog = true
                    }
                ) {
                    Text(stringResource(R.string.continue_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOperit2WarningDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (requiresForgeInitialization && !showSecondForgeConfirm) {
        AlertDialog(
            onDismissRequest = {
                showSecondForgeConfirm = false
                viewModel.dismissForgeInitializationPrompt()
            },
            title = { Text(stringResource(R.string.create_operit_forge_title)) },
            text = { Text(stringResource(R.string.create_operit_forge_message)) },
            confirmButton = {
                TextButton(onClick = { showSecondForgeConfirm = true }) {
                    Text(stringResource(R.string.continue_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSecondForgeConfirm = false
                        viewModel.dismissForgeInitializationPrompt()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (requiresForgeInitialization && showSecondForgeConfirm) {
        AlertDialog(
            onDismissRequest = {
                showSecondForgeConfirm = false
                viewModel.dismissForgeInitializationPrompt()
            },
            title = { Text(stringResource(R.string.confirm_create_public_forge_title)) },
            text = { Text(stringResource(R.string.confirm_create_public_forge_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSecondForgeConfirm = false
                        viewModel.confirmForgeInitializationAndPublish()
                    }
                ) {
                    Text(stringResource(R.string.create_and_publish))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSecondForgeConfirm = false
                        viewModel.dismissForgeInitializationPrompt()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    publishSuccess?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPublishMessages() },
            title = {
                Text(
                    when {
                        isEditMode -> stringResource(R.string.artifact_publish_artifact_info_updated_success)
                        isContinuationMode -> stringResource(R.string.artifact_publish_update_version_success)
                        else -> stringResource(R.string.publish_success)
                    }
                )
            },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPublishMessages()
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}
