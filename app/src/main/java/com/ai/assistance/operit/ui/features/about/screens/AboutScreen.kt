package com.ai.assistance.operit.ui.features.about.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.updates.FullUpdateInstaller
import com.ai.assistance.operit.data.updates.SourceCandidate
import com.ai.assistance.operit.data.updates.UpdateManager
import com.ai.assistance.operit.data.updates.UpdateSourceRanking
import com.ai.assistance.operit.data.updates.UpdateStatus
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.updates.PatchUpdateInstaller
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.util.GithubReleaseUtil
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale

private const val GITHUB_PROJECT_URL = "https://github.com/AAswordman/Operit"

private enum class PatchUpdatePhase {
    DOWNLOADING_META,
    DOWNLOADING_PATCH,
    APPLYING_PATCH,
    VERIFYING_APK,
    READY_TO_INSTALL,
    ERROR
}

private data class PatchUpdateDialogState(
    val phase: PatchUpdatePhase,
    val message: String,
    val progressPercent: Int? = null,
    val readBytes: Long = 0L,
    val totalBytes: Long? = null,
    val speedBytesPerSec: Long = 0L,
    val chainScanCompleted: Int = 0,
    val chainScanTotal: Int = 0,
    val selectedMirror: String? = null,
    val sourceAttempt: Int = 0,
    val sourceTotal: Int = 0,
    /**
     * Per-source download failures collected while walking the candidate list, keyed by
     * source name. Surfacing these is what tells the user *which* mirror returned 403 or
     * timed out instead of a single opaque "update failed".
     */
    val sourceFailures: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)

private enum class FullUpdatePhase {
    DOWNLOADING_APK,
    READY_TO_INSTALL,
    ERROR
}

private data class FullUpdateDialogState(
    val phase: FullUpdatePhase,
    val message: String,
    val progressPercent: Int? = null,
    val readBytes: Long = 0L,
    val totalBytes: Long? = null,
    val speedBytesPerSec: Long = 0L,
    val selectedSource: String? = null,
    val sourceAttempt: Int = 0,
    val sourceTotal: Int = 0,
    val sourceFailures: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)


@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    val textColor = style.color.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                this.textSize = style.fontSize.value
                this.setTextColor(textColor)
                this.movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.text =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
                } else {
                    @Suppress("DEPRECATION") Html.fromHtml(html)
                }
        }
    )
}

private fun mapPatchStage(stage: PatchUpdateInstaller.Stage): PatchUpdatePhase {
    return when (stage) {
        PatchUpdateInstaller.Stage.DOWNLOADING_META -> PatchUpdatePhase.DOWNLOADING_META
        PatchUpdateInstaller.Stage.DOWNLOADING_PATCH -> PatchUpdatePhase.DOWNLOADING_PATCH
        PatchUpdateInstaller.Stage.APPLYING_PATCH -> PatchUpdatePhase.APPLYING_PATCH
        PatchUpdateInstaller.Stage.VERIFYING_APK -> PatchUpdatePhase.VERIFYING_APK
        PatchUpdateInstaller.Stage.READY_TO_INSTALL -> PatchUpdatePhase.READY_TO_INSTALL
    }
}

private fun reducePatchUpdateState(
    context: Context,
    previous: PatchUpdateDialogState?,
    event: PatchUpdateInstaller.ProgressEvent
): PatchUpdateDialogState {
    val base =
        previous
            ?: PatchUpdateDialogState(
                phase = PatchUpdatePhase.DOWNLOADING_META,
                message = ""
            )

    return when (event) {
        is PatchUpdateInstaller.ProgressEvent.StageChanged -> {
            base.copy(
                phase = mapPatchStage(event.stage),
                message = event.message,
                progressPercent = null,
                readBytes = 0L,
                totalBytes = null,
                speedBytesPerSec = 0L,
                chainScanCompleted = 0,
                chainScanTotal = 0
            )
        }
        is PatchUpdateInstaller.ProgressEvent.MirrorSelected -> {
            base.copy(
                selectedMirror = event.name,
                sourceAttempt = event.attempt,
                sourceTotal = event.total,
                message = context.getString(
                    R.string.patch_update_selected_mirror,
                    event.name
                )
            )
        }
        is PatchUpdateInstaller.ProgressEvent.SourceFailed -> {
            val failures = base.sourceFailures.toMutableMap()
            failures[event.name] = event.reason
            base.copy(
                sourceFailures = failures,
                message = context.getString(
                    R.string.update_source_failed_reason,
                    event.name,
                    event.reason
                )
            )
        }
        is PatchUpdateInstaller.ProgressEvent.ChainScanProgress -> {
            base.copy(
                phase = PatchUpdatePhase.VERIFYING_APK,
                chainScanCompleted = event.scanned,
                chainScanTotal = event.total
            )
        }
        is PatchUpdateInstaller.ProgressEvent.DownloadProgress -> {
            val percent =
                if (event.totalBytes != null && event.totalBytes > 0) {
                    ((event.readBytes * 100) / event.totalBytes).toInt().coerceIn(0, 100)
                } else {
                    null
                }
            base.copy(
                progressPercent = percent,
                readBytes = event.readBytes,
                totalBytes = event.totalBytes,
                speedBytesPerSec = event.speedBytesPerSec
            )
        }
    }
}

private fun mapFullUpdateStage(stage: FullUpdateInstaller.Stage): FullUpdatePhase {
    return when (stage) {
        FullUpdateInstaller.Stage.DOWNLOADING_APK -> FullUpdatePhase.DOWNLOADING_APK
        FullUpdateInstaller.Stage.READY_TO_INSTALL -> FullUpdatePhase.READY_TO_INSTALL
    }
}

private fun reduceFullUpdateState(
    context: Context,
    previous: FullUpdateDialogState?,
    event: FullUpdateInstaller.ProgressEvent
): FullUpdateDialogState {
    val base =
        previous
            ?: FullUpdateDialogState(
                phase = FullUpdatePhase.DOWNLOADING_APK,
                message = ""
            )

    return when (event) {
        is FullUpdateInstaller.ProgressEvent.StageChanged -> {
            base.copy(
                phase = mapFullUpdateStage(event.stage),
                message = event.message,
                progressPercent = null,
                readBytes = 0L,
                totalBytes = null,
                speedBytesPerSec = 0L
            )
        }

        is FullUpdateInstaller.ProgressEvent.SourceAttempt -> {
            base.copy(
                selectedSource = event.name,
                sourceAttempt = event.attempt,
                sourceTotal = event.total
            )
        }

        is FullUpdateInstaller.ProgressEvent.SourceFailed -> {
            val failures = base.sourceFailures.toMutableMap()
            failures[event.name] = event.reason
            base.copy(
                sourceFailures = failures,
                message = context.getString(
                    R.string.update_source_failed_reason,
                    event.name,
                    event.reason
                )
            )
        }

        is FullUpdateInstaller.ProgressEvent.DownloadProgress -> {
            val percent =
                if (event.totalBytes != null && event.totalBytes > 0) {
                    ((event.readBytes * 100) / event.totalBytes).toInt().coerceIn(0, 100)
                } else {
                    null
                }
            base.copy(
                progressPercent = percent,
                readBytes = event.readBytes,
                totalBytes = event.totalBytes,
                speedBytesPerSec = event.speedBytesPerSec
            )
        }
    }
}

private fun sortPatchMirrorNamesForDisplay(
    names: Collection<String>,
    results: Map<String, PatchUpdateInstaller.MirrorProbeSummary>
): List<String> {
    return names.sortedWith(
        compareBy<String> { name ->
            when (val summary = results[name]) {
                null -> 1
                else -> if (summary.ok) 0 else 2
            }
        }.thenByDescending { name ->
            results[name]?.speedBytesPerSec ?: Long.MIN_VALUE
        }.thenBy { name ->
            results[name]?.latencyMs ?: Long.MAX_VALUE
        }.thenBy { name ->
            name.lowercase(Locale.ROOT)
        }
    )
}

private fun sortFullMirrorNamesForDisplay(
    names: Collection<String>,
    results: Map<String, GithubReleaseUtil.ProbeResult>
): List<String> {
    return names.sortedWith(
        compareBy<String> { name ->
            when (val probe = results[name]) {
                null -> 1
                else -> if (probe.ok) 0 else 2
            }
        }.thenByDescending { name ->
            results[name]?.bytesPerSec ?: Long.MIN_VALUE
        }.thenBy { name ->
            results[name]?.latencyMs ?: Long.MAX_VALUE
        }.thenBy { name ->
            name.lowercase(Locale.ROOT)
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "${bytes} B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val value = bytes.toDouble()
    return when {
        value >= gb -> String.format(Locale.US, "%.2f GB", value / gb)
        value >= mb -> String.format(Locale.US, "%.2f MB", value / mb)
        else -> String.format(Locale.US, "%.2f KB", value / kb)
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "--/s"
    return "${formatBytes(bytesPerSec)}/s"
}

/**
 * Renders the per-source download failures gathered while walking the candidate list.
 *
 * Each line names the source and the concrete reason it died (HTTP 403, timeout, sha256
 * mismatch, ...), which is what lets the user tell a dead mirror apart from their own
 * network being down. The dialog is fixed-height, so a long list is capped and made
 * scrollable rather than pushing the buttons off screen.
 */
@Composable
private fun UpdateSourceFailureList(failures: Map<String, String>) {
    if (failures.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 140.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        failures.forEach { (name, reason) ->
            Text(
                text = stringResource(id = R.string.update_source_failed_reason, name, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}


@Composable
fun InfoItem(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp, top = 2.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(modifier = Modifier.padding(top = 4.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitleText: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(12.dp),
            color = iconTint.copy(alpha = 0.16f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitleContent != null) {
                Box(modifier = Modifier.padding(top = 2.dp)) { subtitleContent() }
            } else if (!subtitleText.isNullOrBlank()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navigateToUpdateHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val preferences = remember { UserPreferencesManager.getInstance(context) }
    val betaEnabled = preferences.betaPlanEnabled.collectAsState(initial = false).value

    // 获取UpdateManager实例
    val updateManager = remember { UpdateManager.getInstance(context) }

    // 监听更新状态
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Initial) }

    // 观察UpdateManager的LiveData
    DisposableEffect(updateManager) {
        val observer = androidx.lifecycle.Observer<UpdateStatus> { newStatus -> updateStatus = newStatus }
        updateManager.updateStatus.observeForever(observer)

        onDispose { updateManager.updateStatus.removeObserver(observer) }
    }

    // 显示更新对话框
    var showUpdateDialog by remember { mutableStateOf(false) }
    val patchUpdateStateFlow = remember { MutableStateFlow<PatchUpdateDialogState?>(null) }
    val patchUpdateState by patchUpdateStateFlow.collectAsState()
    var patchUpdateJob by remember { mutableStateOf<Job?>(null) }
    val fullUpdateStateFlow = remember { MutableStateFlow<FullUpdateDialogState?>(null) }
    val fullUpdateState by fullUpdateStateFlow.collectAsState()
    var fullUpdateJob by remember { mutableStateOf<Job?>(null) }
    var pendingFullUpdateMethod by remember { mutableStateOf<UpdateStatus.Available?>(null) }
    var pendingFullUpdate by remember { mutableStateOf<UpdateStatus.Available?>(null) }
    var pendingFullUpdateInApp by remember { mutableStateOf<UpdateStatus.Available?>(null) }
    var pendingPatchUpdate by remember { mutableStateOf<UpdateStatus.PatchAvailable?>(null) }

    // 添加开源许可对话框状态
    var showLicenseDialog by remember { mutableStateOf(false) }

    // 获取应用版本信息
    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: context.getString(R.string.about_version_unknown)
        } catch (e: PackageManager.NameNotFoundException) {
            context.getString(R.string.about_version_unknown)
        }
    }

    // 观察更新状态变化
    LaunchedEffect(updateStatus) {
        when (updateStatus) {
            is UpdateStatus.Available, is UpdateStatus.PatchAvailable, is UpdateStatus.UpToDate, is UpdateStatus.Error -> {
                showUpdateDialog = true
            }
            else -> {}
        }
    }

    // 检查更新
    fun checkForUpdates() {
        scope.launch { updateManager.checkForUpdates(appVersion) }
    }

    // 从指定源下载
    fun downloadFromUrl(downloadUrl: String) {
        // 打开浏览器下载
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(downloadUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        showUpdateDialog = false
    }

    /**
     * Run a patch update over an ordered source list.
     *
     * [sourceKeys] is fastest-first with the user's pick moved to the front by the
     * download-source dialog. The installer walks it per artifact, so a mirror dying
     * mid-download continues from the next source rather than failing the update.
     */
    fun startPatchUpdate(
        patchUrl: String,
        metaUrl: String,
        patchVersion: String,
        sourceKeys: List<String>,
        metaSha256: String?,
        patchSha256: String?
    ) {
        if (patchUpdateJob?.isActive == true) return
        showUpdateDialog = false

        patchUpdateStateFlow.value =
            PatchUpdateDialogState(
                phase = PatchUpdatePhase.DOWNLOADING_META,
                message = context.getString(R.string.patch_update_prepare_downloading, patchVersion),
                sourceTotal = sourceKeys.size
            )

        patchUpdateJob =
            scope.launch {
                ActivityLifecycleManager.forceKeepScreenOn(true)
                try {
                    val apk =
                        PatchUpdateInstaller.downloadAndPreparePatchUpdate(
                            context = context,
                            patchUrl = patchUrl,
                            metaUrl = metaUrl,
                            sourceKeys = sourceKeys,
                            metaSha256 = metaSha256,
                            patchSha256 = patchSha256
                        ) { event ->
                            patchUpdateStateFlow.update { current ->
                                if (current == null) {
                                    null
                                } else {
                                    reducePatchUpdateState(context, current, event)
                                }
                            }
                        }

                    patchUpdateStateFlow.update { current ->
                        (current ?: PatchUpdateDialogState(
                            phase = PatchUpdatePhase.READY_TO_INSTALL,
                            message = context.getString(R.string.full_update_ready_to_install)
                        )).copy(
                            phase = PatchUpdatePhase.READY_TO_INSTALL,
                            message = context.getString(R.string.full_update_ready_to_install)
                        )
                    }

                    withContext(Dispatchers.Main) {
                        PatchUpdateInstaller.installApk(context, apk)
                    }
                    patchUpdateStateFlow.value = null
                } catch (e: CancellationException) {
                    patchUpdateStateFlow.value = null
                } catch (e: Exception) {
                    AppLogger.e("AboutScreen", "patch update failed", e)
                    patchUpdateStateFlow.update { current ->
                        PatchUpdateDialogState(
                            phase = PatchUpdatePhase.ERROR,
                            message = context.getString(R.string.patch_update_failed_simple),
                            errorMessage = e.message ?: context.getString(R.string.unknown_error),
                            // Keep the per-source reasons collected while walking the list:
                            // that detail is the whole point of the failure message.
                            sourceFailures = current?.sourceFailures ?: emptyMap()
                        )
                    }
                } finally {
                    ActivityLifecycleManager.forceKeepScreenOn(false)
                    patchUpdateJob = null
                }
            }
    }

    fun startFullUpdateInApp(
        apkUrl: String,
        targetVersion: String,
        sourceKeys: List<String>,
        expectedSha256: String?,
        expectedSize: Long?
    ) {
        if (fullUpdateJob?.isActive == true) return
        showUpdateDialog = false

        fullUpdateStateFlow.value =
            FullUpdateDialogState(
                phase = FullUpdatePhase.DOWNLOADING_APK,
                message = context.getString(R.string.full_update_preparing, targetVersion),
                sourceTotal = sourceKeys.size
            )

        fullUpdateJob =
            scope.launch {
                try {
                    val apk =
                        FullUpdateInstaller.downloadAndPrepareUpdate(
                            context = context,
                            apkUrl = apkUrl,
                            sourceKeys = sourceKeys,
                            downloadMessage = context.getString(R.string.full_update_downloading),
                            readyMessage = context.getString(R.string.full_update_ready_to_install),
                            expectedSha256 = expectedSha256,
                            expectedSize = expectedSize
                        ) { event ->
                            fullUpdateStateFlow.update { current ->
                                if (current == null) {
                                    null
                                } else {
                                    reduceFullUpdateState(context, current, event)
                                }
                            }
                        }

                    withContext(Dispatchers.Main) {
                        PatchUpdateInstaller.installApk(context, apk)
                    }
                    fullUpdateStateFlow.value = null
                } catch (e: CancellationException) {
                    fullUpdateStateFlow.value = null
                } catch (e: Exception) {
                    AppLogger.e("AboutScreen", "full update failed", e)
                    val errorText = e.message ?: context.getString(R.string.unknown_error)
                    fullUpdateStateFlow.update { current ->
                        FullUpdateDialogState(
                            phase = FullUpdatePhase.ERROR,
                            message = context.getString(R.string.full_update_failed, errorText),
                            errorMessage = errorText,
                            // Preserve the per-source reasons gathered while walking the list.
                            sourceFailures = current?.sourceFailures ?: emptyMap()
                        )
                    }
                } finally {
                    fullUpdateJob = null
                }
            }
    }

    // 处理下载更新 - 自动测速并选择最快下载源
    fun handleDownload() {
        when (val status = updateStatus) {
            is UpdateStatus.Available -> {
                if (status.downloadUrl.isNotEmpty() && status.downloadUrl.endsWith(".apk")) {
                    showUpdateDialog = false
                    pendingFullUpdateMethod = status
                } else {
                    // 如果没有APK下载链接，则直接打开更新页面
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(status.updateUrl)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    showUpdateDialog = false
                }
            }
            is UpdateStatus.PatchAvailable -> {
                showUpdateDialog = false
                pendingPatchUpdate = status
            }
            else -> return
        }
    }

    // 显示开源许可对话框
    if (showLicenseDialog) {
        LicenseDialog(onDismiss = { showLicenseDialog = false })
    }

    if (patchUpdateState != null) {
        PatchUpdateProgressDialog(
            state = patchUpdateState!!,
            onCancel = {
                patchUpdateJob?.cancel()
                patchUpdateJob = null
                patchUpdateStateFlow.value = null
            }
        )
    }

    if (fullUpdateState != null) {
        FullUpdateProgressDialog(
            state = fullUpdateState!!,
            onCancel = {
                fullUpdateJob?.cancel()
                fullUpdateJob = null
                fullUpdateStateFlow.value = null
            }
        )
    }

    if (pendingFullUpdateMethod != null) {
        val status = pendingFullUpdateMethod!!
        FullUpdateMethodDialog(
            onDismiss = { pendingFullUpdateMethod = null },
            onBrowser = {
                pendingFullUpdateMethod = null
                pendingFullUpdate = status
            },
            onInApp = {
                pendingFullUpdateMethod = null
                pendingFullUpdateInApp = status
            }
        )
    }

    if (pendingFullUpdate != null) {
        val status = pendingFullUpdate!!
        DownloadSourceDialog(
            updateStatus = status,
            onDismiss = { pendingFullUpdate = null },
            onDownload = { sourceKeys ->
                pendingFullUpdate = null
                // Browser download hands off to an external browser; only the first
                // (preferred) source is used because the browser does its own retries.
                val sources = GithubReleaseUtil.getDownloadSources(status.downloadUrl)
                downloadFromUrl(sources.getValue(sourceKeys.first()))
            }
        )
    }

    if (pendingFullUpdateInApp != null) {
        val status = pendingFullUpdateInApp!!
        DownloadSourceDialog(
            updateStatus = status,
            onDismiss = { pendingFullUpdateInApp = null },
            onDownload = { sourceKeys ->
                pendingFullUpdateInApp = null
                startFullUpdateInApp(
                    apkUrl = status.downloadUrl,
                    targetVersion = status.newVersion,
                    sourceKeys = sourceKeys,
                    expectedSha256 = status.downloadSha256,
                    expectedSize = status.downloadSize
                )
            }
        )
    }

    if (pendingPatchUpdate != null) {
        val status = pendingPatchUpdate!!
        PatchDownloadSourceDialog(
            patchUrl = status.patchUrl,
            metaUrl = status.metaUrl,
            onDismiss = { pendingPatchUpdate = null },
            onDownload = { sourceKeys ->
                pendingPatchUpdate = null
                startPatchUpdate(
                    patchUrl = status.patchUrl,
                    metaUrl = status.metaUrl,
                    patchVersion = status.newVersion,
                    sourceKeys = sourceKeys,
                    metaSha256 = status.metaSha256,
                    patchSha256 = status.patchSha256
                )
            }
        )
    }

    // 更新对话框
    if (showUpdateDialog) {
        UpdateDialog(
            updateStatus = updateStatus,
            appVersion = appVersion,
            onDismiss = { showUpdateDialog = false },
            onConfirm = {
                if (updateStatus is UpdateStatus.Available || updateStatus is UpdateStatus.PatchAvailable) {
                    handleDownload()
                } else if (updateStatus !is UpdateStatus.Checking) {
                    showUpdateDialog = false
                }
            }
        )
    }

    CustomScaffold() { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_simple_foreground),
                                contentDescription = stringResource(R.string.app_logo_description),
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.about_version, appVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            item {
                val updateSubtitle = when (val status = updateStatus) {
                    is UpdateStatus.Available -> context.getString(R.string.new_version, appVersion, status.newVersion)
                    is UpdateStatus.PatchAvailable -> context.getString(R.string.new_version, appVersion, status.newVersion)
                    is UpdateStatus.UpToDate -> context.getString(R.string.already_latest_version, appVersion)
                    is UpdateStatus.Error -> status.message
                    is UpdateStatus.Checking -> context.getString(R.string.checking_updates)
                    else -> null
                }

                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Update,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(id = R.string.check_for_updates),
                        subtitleText = updateSubtitle,
                        trailing = {
                            if (updateStatus is UpdateStatus.Checking) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            when (updateStatus) {
                                is UpdateStatus.Available,
                                is UpdateStatus.PatchAvailable,
                                is UpdateStatus.UpToDate,
                                is UpdateStatus.Error -> showUpdateDialog = true
                                is UpdateStatus.Checking -> Unit
                                else -> checkForUpdates()
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 66.dp))

                    SettingsRow(
                        icon = Icons.Default.NewReleases,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        title = stringResource(id = R.string.beta_plan),
                        subtitleText = stringResource(id = R.string.beta_plan_desc),
                        trailing = {
                            Switch(
                                checked = betaEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { preferences.saveBetaPlanEnabled(enabled) }
                                }
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Language,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(id = R.string.project_url),
                        subtitleText = GITHUB_PROJECT_URL,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_PROJECT_URL)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 66.dp))

                    SettingsRow(
                        icon = Icons.Default.Star,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(id = R.string.star_on_github),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_PROJECT_URL)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 66.dp))

                    SettingsRow(
                        icon = Icons.Default.History,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(R.string.update_log),
                        onClick = { navigateToUpdateHistory() }
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 66.dp))

                    SettingsRow(
                        icon = Icons.Default.Source,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(id = R.string.open_source_licenses),
                        onClick = { showLicenseDialog = true }
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Email,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(id = R.string.contact),
                        subtitleText = stringResource(id = R.string.about_contact)
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 66.dp))

                    SettingsRow(
                        icon = Icons.Default.Person,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(id = R.string.developer),
                        subtitleContent = {
                            HtmlText(
                                html = stringResource(id = R.string.about_developer),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    )
                }
            }

            item {
                Text(
                    text = stringResource(id = R.string.about_copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDialog(
    updateStatus: UpdateStatus,
    appVersion: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            val icon = when (updateStatus) {
                is UpdateStatus.Available -> Icons.Default.Update
                is UpdateStatus.PatchAvailable -> Icons.Default.Update
                is UpdateStatus.Checking -> Icons.Default.Download
                is UpdateStatus.UpToDate -> Icons.Default.CheckCircle
                is UpdateStatus.Error -> Icons.Default.Error
                else -> Icons.Default.Update
            }
            Icon(icon, contentDescription = null)
        },
        title = {
            val titleText = when (updateStatus) {
                is UpdateStatus.Available -> stringResource(id = R.string.new_version_found)
                is UpdateStatus.PatchAvailable -> stringResource(id = R.string.new_version_found)
                is UpdateStatus.Checking -> stringResource(id = R.string.checking_updates)
                is UpdateStatus.UpToDate -> stringResource(id = R.string.check_complete)
                is UpdateStatus.Error -> stringResource(id = R.string.check_failed)
                else -> stringResource(id = R.string.update_check)
            }
            Text(text = titleText)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (val status = updateStatus) {
                    is UpdateStatus.Available -> {
                        Text(
                            text = "$appVersion -> ${status.newVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (status.releaseNotes.isNotEmpty() && !status.newVersion.contains("+")) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                stringResource(id = R.string.update_content),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(status.releaseNotes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is UpdateStatus.PatchAvailable -> {
                        Text(
                            text = "$appVersion -> ${status.newVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    is UpdateStatus.UpToDate -> {
                        Text(stringResource(id = R.string.already_latest_version, appVersion))
                    }
                    is UpdateStatus.Error -> {
                        Text(status.message)
                    }
                    else -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = updateStatus !is UpdateStatus.Checking
            ) {
                Text(
                    when (updateStatus) {
                        is UpdateStatus.Available -> stringResource(id = R.string.download)
                        is UpdateStatus.PatchAvailable -> stringResource(id = R.string.patch_update)
                        else -> stringResource(id = R.string.ok)
                    }
                )
            }
        },
        dismissButton = {
            if (updateStatus !is UpdateStatus.Checking) {
                TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.close)) }
            }
        }
    )
}

@Composable
private fun PatchUpdateProgressDialog(
    state: PatchUpdateDialogState,
    onCancel: () -> Unit
) {
    val isError = state.phase == PatchUpdatePhase.ERROR
    val showCancel = !isError

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.patch_update),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (state.phase) {
                    PatchUpdatePhase.DOWNLOADING_META,
                    PatchUpdatePhase.DOWNLOADING_PATCH -> {
                        if (state.selectedMirror != null) {
                            Text(
                                text = stringResource(
                                    id = R.string.patch_update_selected_short,
                                    state.selectedMirror
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (state.progressPercent != null) {
                            LinearProgressIndicator(
                                progress = state.progressPercent / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        val sizeText =
                            if (state.totalBytes != null && state.totalBytes > 0) {
                                "${formatBytes(state.readBytes)} / ${formatBytes(state.totalBytes)}"
                            } else {
                                formatBytes(state.readBytes)
                            }
                        Text(
                            text = "$sizeText · ${formatSpeed(state.speedBytesPerSec)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Show sources that already died while walking the list, so the
                        // user sees the update still progressing through candidates
                        // rather than appearing stuck on one failing mirror.
                        UpdateSourceFailureList(state.sourceFailures)
                    }

                    PatchUpdatePhase.APPLYING_PATCH,
                    PatchUpdatePhase.VERIFYING_APK,
                    PatchUpdatePhase.READY_TO_INSTALL -> {
                        if (state.phase == PatchUpdatePhase.VERIFYING_APK && state.chainScanTotal > 0) {
                            val progress =
                                if (state.chainScanTotal > 0) {
                                    state.chainScanCompleted.toFloat() / state.chainScanTotal.toFloat()
                                } else {
                                    0f
                                }
                            LinearProgressIndicator(
                                progress = progress.coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.patch_update_chain_progress,
                                    state.chainScanCompleted,
                                    state.chainScanTotal
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    text =
                                        if (state.phase == PatchUpdatePhase.READY_TO_INSTALL) {
                                            stringResource(id = R.string.patch_update_launching_installer)
                                        } else {
                                            stringResource(id = R.string.processing)
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    PatchUpdatePhase.ERROR -> {
                        Text(
                            text = state.errorMessage ?: stringResource(id = R.string.unknown_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        UpdateSourceFailureList(state.sourceFailures)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (showCancel) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(id = R.string.cancel))
                        }
                    } else if (isError) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(id = R.string.close))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullUpdateMethodDialog(
    onDismiss: () -> Unit,
    onBrowser: () -> Unit,
    onInApp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.select_update_method)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(id = R.string.select_update_method_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onInApp() },
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(id = R.string.update_in_app),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(id = R.string.update_in_app_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onBrowser() },
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(id = R.string.update_via_browser),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(id = R.string.update_via_browser_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun FullUpdateProgressDialog(
    state: FullUpdateDialogState,
    onCancel: () -> Unit
) {
    val isError = state.phase == FullUpdatePhase.ERROR
    val showCancel = !isError

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.full_update),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (state.phase) {
                    FullUpdatePhase.DOWNLOADING_APK -> {
                        if (state.selectedSource != null) {
                            Text(
                                text = stringResource(
                                    id = R.string.patch_update_selected_short,
                                    state.selectedSource
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (state.progressPercent != null) {
                            LinearProgressIndicator(
                                progress = state.progressPercent / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        val sizeText =
                            if (state.totalBytes != null && state.totalBytes > 0) {
                                "${formatBytes(state.readBytes)} / ${formatBytes(state.totalBytes)}"
                            } else {
                                formatBytes(state.readBytes)
                            }
                        Text(
                            text = "$sizeText · ${formatSpeed(state.speedBytesPerSec)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        UpdateSourceFailureList(state.sourceFailures)
                    }

                    FullUpdatePhase.READY_TO_INSTALL -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FullUpdatePhase.ERROR -> {
                        Text(
                            text = state.errorMessage ?: stringResource(id = R.string.unknown_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        UpdateSourceFailureList(state.sourceFailures)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (showCancel) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(id = R.string.cancel))
                        }
                    } else if (isError) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(id = R.string.close))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSourceDialog(
    updateStatus: UpdateStatus,
    onDismiss: () -> Unit,
    onDownload: (List<String>) -> Unit
) {
    val status = updateStatus as? UpdateStatus.Available
    val probeUrls = remember(status) {
        status?.let { GithubReleaseUtil.getDownloadSources(it.downloadUrl) } ?: emptyMap()
    }

    var probeResults by remember(probeUrls) {
        mutableStateOf<Map<String, GithubReleaseUtil.ProbeResult>>(emptyMap())
    }

    LaunchedEffect(probeUrls) {
        if (probeUrls.isEmpty()) {
            probeResults = emptyMap()
            return@LaunchedEffect
        }
        probeResults = emptyMap()
        coroutineScope {
            probeUrls.forEach { (name, url) ->
                launch {
                    val r = withContext(Dispatchers.IO) {
                        GithubReleaseUtil.probeMirrorUrls(mapOf(name to url))[name]
                    }
                    if (r != null) {
                        probeResults = probeResults.toMutableMap().apply { put(name, r) }
                    }
                }
            }
        }
    }

    /**
     * Build the ordered source-key list to download from: usable sources fastest first,
     * with [preferredKey] (the row the user tapped) moved to the front. Rejected sources
     * are excluded because they cannot serve the ranged download.
     */
    fun orderedSourceKeys(preferredKey: String?): List<String> {
        val candidates = probeUrls.map { (key, url) ->
            val probe = probeResults[key]
            val ok = probe?.ok == true
            SourceCandidate(
                key = key,
                url = url,
                speedBytesPerSec = probe?.bytesPerSec,
                latencyMs = probe?.latencyMs,
                probeError = if (ok) null else (probe?.error ?: "PROBE_FAILED")
            )
        }
        val ranked = UpdateSourceRanking.rank(candidates)
        if (preferredKey == null) return ranked.usable.map { it.key }
        return ranked.prioritized(preferredKey, failedKeys = emptySet()).map { it.key }
    }

    fun autoSelect() {
        val keys = orderedSourceKeys(preferredKey = null)
        if (keys.isEmpty()) return
        onDownload(keys)
    }

    val autoEnabled = probeResults.values.any { it.ok }
    val orderedProbeKeys = remember(probeUrls, probeResults) {
        sortFullMirrorNamesForDisplay(probeUrls.keys, probeResults)
    }

    @Composable
    fun MirrorSourceRow(
        title: String,
        desc: String?,
        icon: ImageVector,
        probe: GithubReleaseUtil.ProbeResult?,
        enabled: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (desc != null) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (probe == null && probeUrls.isNotEmpty()) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else if (probe != null) {
                val text = if (probe.ok) {
                    probe.latencyMs?.let { "${it}ms" } ?: "ok"
                } else {
                    probe.error ?: "fail"
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (probe.ok) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 64.dp, max = 140.dp)
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.select_download_source)) },
        text = {
            Column {
                Text(
                    stringResource(id = R.string.select_download_source_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn {
                    items(orderedProbeKeys) { name ->
                        val probe = probeResults[name]
                        val isGitHub = name == GithubReleaseUtil.GITHUB_SOURCE_KEY
                        val enabled = probe?.ok == true
                        MirrorSourceRow(
                            title = if (isGitHub) stringResource(id = R.string.github_source) else stringResource(id = R.string.mirror_download, name),
                            desc = if (isGitHub) stringResource(id = R.string.github_source_desc) else null,
                            icon = if (isGitHub) Icons.Default.Language else Icons.Default.Storage,
                            probe = probe,
                            enabled = enabled,
                            // Tapping a source prefers it but keeps the rest of the usable
                            // list behind it, so a source that dies mid-download still
                            // continues instead of failing the whole update.
                            onClick = { onDownload(orderedSourceKeys(preferredKey = name)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { autoSelect() }, enabled = autoEnabled) {
                    Text(stringResource(id = R.string.auto_select))
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchDownloadSourceDialog(
    patchUrl: String,
    metaUrl: String,
    onDismiss: () -> Unit,
    onDownload: (List<String>) -> Unit
) {
    val patchSources = remember(patchUrl) { GithubReleaseUtil.getDownloadSources(patchUrl) }
    val metaSources = remember(metaUrl) { GithubReleaseUtil.getDownloadSources(metaUrl) }

    val keys = remember(patchSources, metaSources) {
        val set = LinkedHashSet<String>()
        set.addAll(patchSources.keys)
        set.addAll(metaSources.keys)
        set.toList()
    }

    var probeResults by remember(patchUrl, metaUrl) {
        mutableStateOf<Map<String, PatchUpdateInstaller.MirrorProbeSummary>>(emptyMap())
    }

    LaunchedEffect(patchUrl, metaUrl) {
        probeResults = emptyMap()
        coroutineScope {
            keys.forEach { key ->
                launch {
                    val patchProbeUrl = patchSources.getValue(key)
                    val metaProbeUrl = metaSources.getValue(key)

                    val patchRes = withContext(Dispatchers.IO) {
                        GithubReleaseUtil.probeMirrorUrls(mapOf(key to patchProbeUrl))[key]
                    }
                    val metaRes = withContext(Dispatchers.IO) {
                        GithubReleaseUtil.probeMirrorUrls(mapOf(key to metaProbeUrl))[key]
                    }

                    val ok = patchRes?.ok == true && metaRes?.ok == true
                    val speed =
                        if (patchRes?.bytesPerSec != null && metaRes?.bytesPerSec != null) {
                            minOf(patchRes.bytesPerSec, metaRes.bytesPerSec)
                        } else {
                            null
                        }
                    val latency =
                        when {
                            patchRes?.latencyMs != null && metaRes?.latencyMs != null ->
                                maxOf(patchRes.latencyMs, metaRes.latencyMs)
                            patchRes?.latencyMs != null -> patchRes.latencyMs
                            else -> metaRes?.latencyMs
                        }
                    val error =
                        when {
                            patchRes?.ok != true -> patchRes?.error ?: "patch_failed"
                            metaRes?.ok != true -> metaRes?.error ?: "meta_failed"
                            else -> null
                        }

                    val summary =
                        PatchUpdateInstaller.MirrorProbeSummary(
                            ok = ok,
                            latencyMs = latency,
                            speedBytesPerSec = speed,
                            error = error
                        )

                    probeResults = probeResults.toMutableMap().apply { put(key, summary) }
                }
            }
        }
    }

    /**
     * Ordered source-key list to download from: usable sources fastest first, with
     * [preferredKey] moved to the front when the user taps a row. A source only counts as
     * usable when it can serve *both* the meta and the patch zip with range support.
     */
    fun orderedSourceKeys(preferredKey: String?): List<String> {
        val candidates = keys.map { key ->
            val summary = probeResults[key]
            SourceCandidate(
                key = key,
                // The URL is irrelevant for ranking; the installer resolves per artifact.
                url = "",
                speedBytesPerSec = summary?.speedBytesPerSec,
                latencyMs = summary?.latencyMs,
                probeError = if (summary?.ok == true) null else (summary?.error ?: "PROBE_FAILED")
            )
        }
        val ranked = UpdateSourceRanking.rank(candidates)
        if (preferredKey == null) return ranked.usable.map { it.key }
        return ranked.prioritized(preferredKey, failedKeys = emptySet()).map { it.key }
    }

    fun autoSelect() {
        val keys = orderedSourceKeys(preferredKey = null)
        if (keys.isEmpty()) return
        onDownload(keys)
    }

    val autoEnabled = probeResults.values.any { it.ok }
    val orderedKeys = remember(keys, probeResults) {
        sortPatchMirrorNamesForDisplay(keys, probeResults)
    }

    @Composable
    fun MirrorSourceRow(
        title: String,
        desc: String?,
        icon: ImageVector,
        summary: PatchUpdateInstaller.MirrorProbeSummary?,
        enabled: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (desc != null) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (summary == null && keys.isNotEmpty()) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else if (summary != null) {
                val text = if (summary.ok) {
                    summary.latencyMs?.let { "${it}ms" } ?: "ok"
                } else {
                    summary.error ?: "fail"
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (summary.ok) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 64.dp, max = 140.dp)
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.select_download_source)) },
        text = {
            Column {
                Text(
                    stringResource(id = R.string.patch_update_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn {
                    items(orderedKeys) { name ->
                        val summary = probeResults[name]
                        val isGitHub = name == GithubReleaseUtil.GITHUB_SOURCE_KEY
                        val title =
                            if (isGitHub) {
                                stringResource(id = R.string.github_source)
                            } else {
                                stringResource(id = R.string.mirror_download, name)
                            }
                        val desc =
                            if (isGitHub) {
                                stringResource(id = R.string.github_source_desc)
                            } else {
                                stringResource(id = R.string.china_mirror_desc)
                            }
                        val icon = if (isGitHub) Icons.Default.Language else Icons.Default.Storage
                        val enabled = summary?.ok == true
                        MirrorSourceRow(
                            title = title,
                            desc = desc,
                            icon = icon,
                            summary = summary,
                            enabled = enabled,
                            // Prefer the tapped source but keep the rest behind it so a
                            // mid-download failure continues from another source.
                            onClick = { onDownload(orderedSourceKeys(preferredKey = name)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { autoSelect() }, enabled = autoEnabled) {
                    Text(stringResource(id = R.string.auto_select))
                }
            }
        }
    )
}
