package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2ManifestCategory
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketScope
import com.ai.assistance.operit.ui.features.packages.market.ArtifactPublishClusterContext
import com.ai.assistance.operit.ui.features.packages.market.GitHubForgePublishService
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactSource
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactType
import com.ai.assistance.operit.ui.features.packages.market.ToolPkgLogoAsset
import com.ai.assistance.operit.ui.features.packages.market.PublishProgressStage
import com.ai.assistance.operit.ui.features.packages.market.isOperit2VersionAllowed
import com.ai.assistance.operit.ui.features.packages.market.sameArtifactRuntimePackageId
import com.ai.assistance.operit.ui.features.packages.screens.artifact.viewmodel.ArtifactMarketViewModel
import com.ai.assistance.operit.ui.features.packages.screens.artifact.viewmodel.ArtifactPublishDraft
import com.ai.assistance.operit.ui.common.icons.rememberLogoPainter
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ToolPkgArtifactMinifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val sourceFileName: String,
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
        sourceFileName = assetValue?.assetName.orEmpty().ifBlank { assetValue?.name.orEmpty() },
    )
}

fun com.ai.assistance.operit.data.api.MarketV2Entry.toArtifactPublishClusterContext(
    canEditEntry: Boolean = false
): ArtifactPublishClusterContext {
    val versionValue = latestVersion
    return ArtifactPublishClusterContext(
        entryId = id,
        projectId = versionValue?.projectId?.ifBlank { artifact?.projectId.orEmpty() }.orEmpty(),
        runtimePackageId = versionValue?.runtimePackageId.orEmpty(),
        lockedDisplayName = title,
        projectDisplayName = title,
        projectDescription = detail.ifBlank { description },
        marketDescription = description,
        marketDetail = detail,
        categoryId = categoryId,
        canEditEntry = canEditEntry
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
    val githubReleaseCatalog by viewModel.githubReleaseCatalog.collectAsState()
    val githubReleaseCatalogError by viewModel.githubReleaseCatalogError.collectAsState()
    val isLoadingGitHubReleaseCatalog by viewModel.isLoadingGitHubReleaseCatalog.collectAsState()

    val initialInfo = remember(editingEntry) { editingEntry?.toArtifactPublishEditInfo() }
    var mutablePublishContext by remember(isEditMode, publishContext) {
        mutableStateOf(if (isEditMode) null else publishContext)
    }
    val activePublishContext = if (isEditMode) null else mutablePublishContext
    val isContinuationMode = activePublishContext != null
    val lockedRuntimePackageId = initialInfo?.runtimePackageId?.ifBlank { initialInfo.normalizedId }.orEmpty()
    val lockedDisplayName = activePublishContext?.lockedDisplayName?.trim().orEmpty()
    val canEditContinuationEntry = activePublishContext?.canEditEntry ?: true
    val isDisplayNameLocked = !isEditMode && lockedDisplayName.isNotBlank() && !canEditContinuationEntry
    val isContinuationCategoryLocked = isContinuationMode && !canEditContinuationEntry
    val continuationDescription =
        stringResource(R.string.artifact_publish_continuation_description)
    val draftScopeKey =
        remember(
            isEditMode,
            activePublishContext?.entryId,
            activePublishContext?.runtimePackageId,
            activePublishContext?.projectId
        ) {
            if (isEditMode) {
                "edit-${editingEntry?.id.orEmpty()}"
            } else {
                listOf(
                    activePublishContext?.entryId.orEmpty(),
                    activePublishContext?.runtimePackageId.orEmpty(),
                    activePublishContext?.projectId.orEmpty()
                )
                    .firstOrNull { it.isNotBlank() }
                    ?.let { "entry-$it" }
                    ?: "fresh"
            }
        }
    val savedPublishDraft =
        remember(draftScopeKey) {
            if (isEditMode) null else viewModel.loadPublishDraft(activePublishContext)
        }

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

    var selectedPackageName by rememberSaveable(draftScopeKey) {
        mutableStateOf(savedPublishDraft?.selectedPackageName.orEmpty())
    }
    var displayName by rememberSaveable(draftScopeKey, initialInfo?.title, lockedDisplayName) {
        mutableStateOf(
            savedPublishDraft?.displayName
                ?: initialInfo?.title.orEmpty().ifBlank { lockedDisplayName }
        )
    }
    var description by rememberSaveable(draftScopeKey, activePublishContext?.marketDescription) {
        mutableStateOf(
            savedPublishDraft?.description
                ?: initialInfo?.description.orEmpty().ifBlank { activePublishContext?.marketDescription.orEmpty() }
        )
    }
    var detail by rememberSaveable(draftScopeKey, activePublishContext?.marketDetail) {
        mutableStateOf(
            savedPublishDraft?.detail
                ?: initialInfo?.detail.orEmpty().ifBlank { activePublishContext?.marketDetail.orEmpty() }
        )
    }
    var categoryId by rememberSaveable(draftScopeKey, activePublishContext?.categoryId) {
        mutableStateOf(
            savedPublishDraft?.categoryId
                ?: initialInfo?.categoryId.orEmpty().ifBlank { activePublishContext?.categoryId.orEmpty() }
        )
    }
    var allowPublicUpdates by rememberSaveable(draftScopeKey, initialInfo?.allowPublicUpdates) {
        mutableStateOf(savedPublishDraft?.allowPublicUpdates ?: initialInfo?.allowPublicUpdates ?: true)
    }
    var minifyArtifact by rememberSaveable(draftScopeKey) {
        mutableStateOf(savedPublishDraft?.minifyArtifact ?: false)
    }
    var useGitHubReleaseAsset by rememberSaveable(draftScopeKey) {
        mutableStateOf(savedPublishDraft?.useGitHubReleaseAsset ?: false)
    }
    var githubRepositoryUrl by rememberSaveable(draftScopeKey) {
        mutableStateOf(savedPublishDraft?.githubRepositoryUrl.orEmpty())
    }
    var selectedReleaseTag by rememberSaveable(draftScopeKey) {
        mutableStateOf(savedPublishDraft?.selectedReleaseTag.orEmpty())
    }
    var selectedReleaseAssetName by rememberSaveable(draftScopeKey) {
        mutableStateOf(savedPublishDraft?.selectedReleaseAssetName.orEmpty())
    }
    var version by rememberSaveable(draftScopeKey) {
        mutableStateOf(savedPublishDraft?.version ?: initialInfo?.version.orEmpty().ifBlank { "1.0.0" })
    }
    var minSupportedAppVersion by rememberSaveable(draftScopeKey) {
        mutableStateOf(savedPublishDraft?.minSupportedAppVersion ?: initialInfo?.minSupportedAppVersion.orEmpty())
    }
    var maxSupportedAppVersion by rememberSaveable(draftScopeKey) {
        mutableStateOf(savedPublishDraft?.maxSupportedAppVersion ?: initialInfo?.maxSupportedAppVersion.orEmpty())
    }
    var selectorExpanded by remember { mutableStateOf(false) }
    var releaseSelectorExpanded by remember { mutableStateOf(false) }
    var releaseAssetSelectorExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<MarketV2ManifestCategory>>(emptyList()) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showOperit2WarningDialog by remember { mutableStateOf(false) }
    var showSecondForgeConfirm by remember { mutableStateOf(false) }
    var showMarketPreview by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshPublishableArtifacts()
        MarketStatsApiService().getManifest().fold(
            onSuccess = { manifest -> categories = manifest.categories.filter { it.id.isNotBlank() } },
            onFailure = {}
        )
    }

    LaunchedEffect(draftScopeKey) {
        val draft = savedPublishDraft
        if (!isEditMode && draft?.useGitHubReleaseAsset == true && draft.githubRepositoryUrl.isNotBlank()) {
            viewModel.loadGitHubReleaseCatalog(draft.githubRepositoryUrl)
        }
    }

    LaunchedEffect(
        draftScopeKey,
        selectedPackageName,
        displayName,
        description,
        detail,
        categoryId,
        allowPublicUpdates,
        minifyArtifact,
        useGitHubReleaseAsset,
        githubRepositoryUrl,
        selectedReleaseTag,
        selectedReleaseAssetName,
        version,
        minSupportedAppVersion,
        maxSupportedAppVersion
    ) {
        if (!isEditMode) {
            viewModel.savePublishDraft(
                publishContext = activePublishContext,
                draft =
                    ArtifactPublishDraft(
                        selectedPackageName = selectedPackageName,
                        displayName = displayName,
                        description = description,
                        detail = detail,
                        categoryId = categoryId,
                        allowPublicUpdates = allowPublicUpdates,
                        minifyArtifact = minifyArtifact,
                        useGitHubReleaseAsset = useGitHubReleaseAsset,
                        githubRepositoryUrl = githubRepositoryUrl,
                        selectedReleaseTag = selectedReleaseTag,
                        selectedReleaseAssetName = selectedReleaseAssetName,
                        version = version,
                        minSupportedAppVersion = minSupportedAppVersion,
                        maxSupportedAppVersion = maxSupportedAppVersion
                    )
            )
        }
    }

    LaunchedEffect(draftScopeKey, publishSuccess) {
        if (!isEditMode && publishSuccess != null) {
            viewModel.clearPublishDraft(activePublishContext)
        }
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
    val toolPkgManifestVersion =
        if (selectedType == PublishArtifactType.PACKAGE) {
            selectedArtifact?.inferredVersion?.trim()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    val isToolPkgVersionLocked = !isEditMode && toolPkgManifestVersion != null
    val effectiveVersion =
        if (isToolPkgVersionLocked) {
            toolPkgManifestVersion.orEmpty()
        } else {
            version
        }
    val isPublishing = publishStage !in listOf(PublishProgressStage.IDLE, PublishProgressStage.COMPLETED)
    LaunchedEffect(selectedPackageName, selectedType, toolPkgManifestVersion, isEditMode) {
        if (!isEditMode && selectedType == PublishArtifactType.PACKAGE) {
            val lockedVersion = toolPkgManifestVersion ?: return@LaunchedEffect
            if (version != lockedVersion) {
                viewModel.clearPendingMarketRegistrationRetry()
                version = lockedVersion
            }
        }
    }
    val packageLogo by
        produceState<ToolPkgLogoAsset?>(
            initialValue = null,
            selectedPackageName,
            selectedArtifact?.sourceFile?.absolutePath,
            selectedType
        ) {
            value =
                if (selectedType == PublishArtifactType.PACKAGE && selectedArtifact != null) {
                    try {
                        withContext(Dispatchers.IO) {
                            ToolPkgArtifactMinifier.readToolPkgLogoAsset(selectedArtifact.sourceFile)
                        }
                    } catch (error: Exception) {
                        AppLogger.e("ArtifactPublishScreen", "Failed to load package logo", error)
                        null
                    }
                } else {
                    null
                }
        }
    // Snapshot the delegated state before smart-casting; Kotlin cannot smart-cast delegated properties.
    val displayedLogo = packageLogo
    val logoPainter =
        rememberLogoPainter(
            logoKey = "${selectedPackageName}:${displayedLogo?.fileName}:${displayedLogo?.bytes?.contentHashCode()}",
            bytes = displayedLogo?.bytes,
            mimeType = displayedLogo?.contentType,
            fileName = displayedLogo?.fileName,
            size = 64.dp
        )
    LaunchedEffect(selectedPackageName, selectedArtifact?.sourceFile?.absolutePath, selectedType) {
        showMarketPreview = false
    }
    val selectorDisplayName =
        if (isEditMode) {
            selectedArtifact?.displayName
                ?: initialInfo?.title.orEmpty().ifBlank {
                    initialInfo?.sourceFileName.orEmpty().ifBlank { lockedRuntimePackageId }
                }
        } else {
            selectedArtifact?.displayName.orEmpty()
        }
    val selectedGitHubRelease =
        githubReleaseCatalog?.releases?.firstOrNull { it.tag_name == selectedReleaseTag }
    val selectedGitHubReleaseAsset =
        selectedGitHubRelease?.assets?.firstOrNull { it.name == selectedReleaseAssetName }
    val publishSource =
        if (!useGitHubReleaseAsset) {
            PublishArtifactSource.DirectUpload(minifyArtifact = minifyArtifact)
        } else {
            val repository = githubReleaseCatalog?.repository
            if (repository != null && selectedGitHubRelease != null && selectedGitHubReleaseAsset != null) {
                PublishArtifactSource.GitHubReleaseAsset(
                    owner = repository.owner,
                    repository = repository.repository,
                    releaseTag = selectedGitHubRelease.tag_name,
                    assetName = selectedGitHubReleaseAsset.name
                )
            } else {
                null
            }
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text =
                        when {
                            isEditMode -> stringResource(R.string.artifact_publish_edit_artifact_description)
                            isContinuationMode -> continuationDescription
                            else -> stringResource(R.string.artifact_publish_info_description)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
                    // The translucent container cannot infer onSurfaceVariant; explicit colors prevent
                    // these labels from inheriting a mismatched foreground in dark themes.
                    Text(
                        text = stringResource(R.string.artifact_publish_current_artifact),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = initialInfo.title.ifBlank { selectorDisplayName },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (lockedDisplayName.isNotBlank()) {
                        Text(
                            text = stringResource(
                                R.string.artifact_publish_locked_plugin_name_hint,
                                lockedDisplayName
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = stringResource(R.string.artifact_publish_package_name_auto_inherited),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
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

        if (!isEditMode) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.artifact_publish_asset_source_title),
                    style = MaterialTheme.typography.titleSmall
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        modifier =
                            Modifier
                                .weight(1f)
                                .defaultMinSize(minWidth = 0.dp)
                                .heightIn(min = 56.dp),
                        selected = !useGitHubReleaseAsset,
                        onClick = {
                            viewModel.clearPendingMarketRegistrationRetry()
                            useGitHubReleaseAsset = false
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {},
                        label = {
                            Text(
                                text = stringResource(R.string.artifact_publish_asset_source_upload),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                    SegmentedButton(
                        modifier =
                            Modifier
                                .weight(1f)
                                .defaultMinSize(minWidth = 0.dp)
                                .heightIn(min = 56.dp),
                        selected = useGitHubReleaseAsset,
                        onClick = {
                            viewModel.clearPendingMarketRegistrationRetry()
                            useGitHubReleaseAsset = true
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {},
                        label = {
                            Text(
                                text = stringResource(R.string.artifact_publish_asset_source_github_release),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                }
                if (useGitHubReleaseAsset) {
                    OutlinedTextField(
                        value = githubRepositoryUrl,
                        onValueChange = {
                            viewModel.clearPendingMarketRegistrationRetry()
                            githubRepositoryUrl = it
                        },
                        label = { Text(stringResource(R.string.artifact_publish_github_repository_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            selectedReleaseTag = ""
                            selectedReleaseAssetName = ""
                            viewModel.loadGitHubReleaseCatalog(githubRepositoryUrl)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = githubRepositoryUrl.isNotBlank() && !isLoadingGitHubReleaseCatalog
                    ) {
                        if (isLoadingGitHubReleaseCatalog) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.artifact_publish_load_github_releases))
                    }
                    githubReleaseCatalogError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    githubReleaseCatalog?.let { catalog ->
                        ExposedDropdownMenuBox(
                            expanded = releaseSelectorExpanded,
                            onExpandedChange = { releaseSelectorExpanded = !releaseSelectorExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedGitHubRelease?.name.orEmpty().ifBlank { selectedReleaseTag },
                                onValueChange = {},
                                label = { Text(stringResource(R.string.artifact_publish_github_release)) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = releaseSelectorExpanded)
                                }
                            )
                            ExposedDropdownMenu(
                                expanded = releaseSelectorExpanded,
                                onDismissRequest = { releaseSelectorExpanded = false }
                            ) {
                                catalog.releases.forEach { release ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(release.name.orEmpty().ifBlank { release.tag_name })
                                                Text(
                                                    text = release.tag_name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.clearPendingMarketRegistrationRetry()
                                            selectedReleaseTag = release.tag_name
                                            selectedReleaseAssetName = ""
                                            releaseSelectorExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        selectedGitHubRelease?.let { release ->
                            ExposedDropdownMenuBox(
                                expanded = releaseAssetSelectorExpanded,
                                onExpandedChange = { releaseAssetSelectorExpanded = !releaseAssetSelectorExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedGitHubReleaseAsset?.name.orEmpty(),
                                    onValueChange = {},
                                    label = { Text(stringResource(R.string.artifact_publish_github_release_asset)) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    readOnly = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = releaseAssetSelectorExpanded)
                                    }
                                )
                                ExposedDropdownMenu(
                                    expanded = releaseAssetSelectorExpanded,
                                    onDismissRequest = { releaseAssetSelectorExpanded = false }
                                ) {
                                    release.assets.forEach { asset ->
                                        DropdownMenuItem(
                                            text = { Text(asset.name) },
                                            onClick = {
                                                viewModel.clearPendingMarketRegistrationRetry()
                                                selectedReleaseAssetName = asset.name
                                                releaseAssetSelectorExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedType == PublishArtifactType.PACKAGE && displayedLogo != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (logoPainter != null) {
                        ComposeImage(
                            painter = logoPainter,
                            contentDescription = stringResource(R.string.artifact_publish_logo_preview),
                            modifier = Modifier.size(56.dp).padding(4.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.artifact_publish_logo_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(
                                R.string.artifact_publish_logo_from_package,
                                displayedLogo.fileName
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(onClick = { showMarketPreview = true }) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.artifact_publish_logo_market_preview))
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
                if (categories.isNotEmpty() && !isContinuationCategoryLocked) {
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
                enabled = categories.isNotEmpty() && !isContinuationCategoryLocked,
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
        if (!isEditMode && !useGitHubReleaseAsset) {
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
                            text =
                                if (selectedType == PublishArtifactType.PACKAGE) {
                                    stringResource(R.string.artifact_publish_encrypt_market_toolpkg_title)
                                } else {
                                    stringResource(R.string.artifact_publish_encrypt_title)
                                },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text =
                                if (selectedType == PublishArtifactType.PACKAGE) {
                                    stringResource(R.string.artifact_publish_encrypt_market_toolpkg_desc)
                                } else {
                                    stringResource(R.string.artifact_publish_encrypt_desc)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = minifyArtifact,
                        onCheckedChange = {
                            viewModel.clearPendingMarketRegistrationRetry()
                            minifyArtifact = it
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = effectiveVersion,
            onValueChange = {
                if (!isEditMode && !isToolPkgVersionLocked) {
                    viewModel.clearPendingMarketRegistrationRetry()
                    version = it
                }
            },
            label = { Text(stringResource(R.string.version_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = isEditMode || isToolPkgVersionLocked,
            supportingText = {
                when {
                    isEditMode -> Text(stringResource(R.string.artifact_publish_published_version_readonly))
                    isToolPkgVersionLocked -> Text(stringResource(R.string.artifact_publish_toolpkg_version_readonly))
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
                        color = MaterialTheme.colorScheme.onErrorContainer
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
                            selectedArtifact != null &&
                                publishSource != null &&
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
                        Text(stringResource(R.string.version_colon, effectiveVersion))
                        if (selectedType == PublishArtifactType.PACKAGE && displayedLogo != null) {
                            Text(
                                stringResource(
                                    R.string.artifact_publish_logo_confirmation,
                                    displayedLogo.fileName
                                )
                            )
                        }
                        Text(
                            stringResource(
                                R.string.artifact_publish_asset_source_confirmation,
                                if (useGitHubReleaseAsset) {
                                    stringResource(R.string.artifact_publish_asset_source_github_release)
                                } else {
                                    stringResource(R.string.artifact_publish_asset_source_upload)
                                }
                            )
                        )
                        if (!useGitHubReleaseAsset) {
                            Text(
                                stringResource(
                                    if (selectedType == PublishArtifactType.PACKAGE) {
                                        R.string.artifact_publish_encrypt_market_toolpkg_confirmation
                                    } else {
                                        R.string.artifact_publish_encrypt_confirmation
                                    },
                                    if (minifyArtifact) {
                                        stringResource(R.string.enabled)
                                    } else {
                                        stringResource(R.string.disabled)
                                    }
                                )
                            )
                        }
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
                            val selectedSource = publishSource
                            if (selectedSource != null) {
                                viewModel.requestPublish(
                                    packageName = selectedPackageName,
                                    displayName = displayName,
                                    description = description,
                                    detail = detail,
                                    categoryId = categoryId,
                                    allowPublicUpdates = allowPublicUpdates,
                                    version = effectiveVersion,
                                    minSupportedAppVersion = minSupportedAppVersion.ifBlank { null },
                                    maxSupportedAppVersion = maxSupportedAppVersion.ifBlank { GitHubForgePublishService.DEFAULT_MAX_SUPPORTED_APP_VERSION },
                                    publishContext = activePublishContext,
                                    source = selectedSource
                                )
                            }
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

    if (showMarketPreview && selectedType == PublishArtifactType.PACKAGE) {
        MarketPublishPreviewDialog(
            title = displayName,
            description = description,
            detail = detail,
            version = effectiveVersion,
            author = selectedArtifact?.author?.firstOrNull().orEmpty(),
            logoAsset = displayedLogo,
            onDismiss = { showMarketPreview = false }
        )
    }
}

/*
private fun ArtifactPublishLogoCard(
    logoPainter: androidx.compose.ui.graphics.painter.Painter?,
    selectedLogoAsset: ToolPkgLogoAsset?,
    packageLogo: ToolPkgLogoAsset?,
    isLoading: Boolean,
    errorMessage: String?,
    onPreview: () -> Unit,
    onChoose: () -> Unit,
    onUsePackageLogo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    if (logoPainter != null) {
                        ComposeImage(
                            painter = logoPainter,
                            contentDescription = stringResource(R.string.artifact_publish_logo_preview),
                            modifier = Modifier.padding(6.dp)
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.artifact_publish_logo_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.artifact_publish_logo_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val selectedFileName = selectedLogoAsset?.fileName
                    val packageFileName = packageLogo?.fileName
                    if (selectedFileName != null) {
                        Text(
                            text = stringResource(R.string.artifact_publish_logo_selected, selectedFileName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (packageFileName != null) {
                        Text(
                            text = stringResource(R.string.artifact_publish_logo_from_package, packageFileName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onChoose,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.artifact_publish_logo_choose))
                }
                if (selectedLogoAsset != null) {
                    OutlinedButton(
                        onClick = onUsePackageLogo,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.artifact_publish_logo_use_package))
                    }
                }
            }

            OutlinedButton(
                onClick = onPreview,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.artifact_publish_logo_market_preview))
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
*/
