package com.ai.assistance.operit.ui.features.management.resources.hub

import android.app.Application
import android.os.SystemClock
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.OfflineResources
import com.ai.assistance.operit.data.resources.models.*
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.storage.DataStorageRepository
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class HubState(
    val entries: List<HubEntry> = emptyList(), val checking: Boolean = true,
    val active: String? = null, val label: String = "", val deleting: Boolean = false,
    val completed: Long = 0, val total: Long = 0, val message: String = "", val failed: Boolean = false,
    val ubuntuInstalled: Boolean = false, val ubuntuPending: Boolean = false,
    val preview: ModelImportPreview? = null,
    val phase: ResourceDownloadPhase? = null, val bytesPerSecond: Double = 0.0, val transferId: String? = null,
)
/** 操作保存在 Activity 的 ViewModel 中，旋转屏幕不会丢失下载或导入预览。 */
internal class ResourceHubViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val repo = ResourceHubRepository(context)
    private val mutable = MutableStateFlow(HubState())
    val state = mutable.asStateFlow()
    private var task: Job? = null
    private var checking: Job? = null
    private var recoveryAttempted = false
    private val meter = DownloadRateMeter()
    private val phase: (ResourceDownloadPhase) -> Unit = { next ->
        meter.reset()
        mutable.update { it.copy(phase = next, bytesPerSecond = 0.0) }
    }
    private val progress: (Long, Long) -> Unit = { done, total ->
        val speed = meter.sample(SystemClock.elapsedRealtime(), done)
        mutable.update { it.copy(completed = done, total = total, bytesPerSecond = speed) }
    }
    init { refresh() }
    fun refresh() {
        if (task?.isActive == true || checking?.isActive == true) return
        checking = viewModelScope.launch {
            mutable.update { it.copy(checking = true) }
            try { readSnapshot() } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) { report(failure) } finally { mutable.update { it.copy(checking = false) } }
        }
    }
    fun onResume(maintenance: Boolean) {
        if (task?.isActive == true || maintenance) return
        if (!recoveryAttempted && OfflineResources.ubuntuDeletionPending(context)) {
            recoveryAttempted = true
            perform(OfflineResources.UBUNTU, R.string.resource_delete, deleting = true) { OfflineResources.resumePendingUbuntuDeletion(context) }
        } else refresh()
    }
    private suspend fun readSnapshot() {
        val snapshot = withContext(Dispatchers.IO) { repo.snapshot() }
        mutable.update { current -> current.copy(entries = snapshot.entries, ubuntuInstalled = snapshot.ubuntuInstalled, ubuntuPending = snapshot.ubuntuPending,
            message = if (snapshot.errors.isNotEmpty()) snapshot.errors.joinToString("\n") else current.message,
            failed = current.failed || snapshot.errors.isNotEmpty()) }
    }
    private fun perform(id: String, label: Int, deleting: Boolean = false, action: suspend () -> Unit) {
        if (task?.isActive == true) return
        mutable.update { it.copy(active = id, label = context.getString(label), deleting = deleting, completed = 0, total = 0, message = "", failed = false,
            phase = if (label == R.string.offline_resources_download) ResourceDownloadPhase.CONNECTING else null, bytesPerSecond = 0.0, transferId = id) }
        meter.reset()
        task = viewModelScope.launch {
            val ticker = launch {
                while (isActive) {
                    delay(1000)
                    val speed = meter.sample(SystemClock.elapsedRealtime(), state.value.completed)
                    mutable.update { it.copy(bytesPerSecond = speed) }
                }
            }
            try {
                checking?.cancelAndJoin()
                withContext(Dispatchers.IO) { action() }
                DataStorageRepository(context).invalidateCache()
                mutable.update { it.copy(message = context.getString(if (deleting) R.string.resource_deleted else R.string.offline_resources_done)) }
            } catch (cancel: CancellationException) {
                mutable.update { it.copy(message = context.getString(R.string.offline_resources_cancelled)) }
                throw cancel
            } catch (failure: Exception) { report(failure) } finally {
                ticker.cancel()
                withContext(NonCancellable) {
                    try {
                        withContext(Dispatchers.IO) {
                            if (state.value.entries.any { it.pack?.extension != null && it.id == id }) {
                                PackageManager.peekInstance()?.getAvailablePackages(forceRefresh = true)
                            }
                        }
                        readSnapshot()
                    } catch (failure: Exception) { report(failure) }
                    mutable.update { it.copy(active = null, checking = false, deleting = false, phase = null, bytesPerSecond = 0.0, transferId = null) }
                }
            }
        }
    }
    private fun report(failure: Exception) {
        AppLogger.e("ResourceHub", "离线资源操作失败", failure)
        mutable.update { it.copy(failed = true, message = context.getString(R.string.offline_resources_failed, failure.localizedMessage ?: failure.javaClass.simpleName)) }
    }
    fun cancel() { if (!state.value.deleting) task?.cancel() }
    fun download(entry: HubEntry, source: ModelDownloadSource) = perform(entry.id, R.string.offline_resources_download) {
        check(entry.downloadable)
        when {
            entry.legacy != null -> OfflineResources.download(context, entry.legacy.id, phase, progress)
            entry.pack != null -> PackResources.download(context, entry.pack.id, phase, progress)
            entry.model != null -> {
                val pinned = repo.models.catalog().firstOrNull { it.key == entry.model.key } ?: entry.model
                repo.models.download(pinned, source, phase, progress)
            }
        }
    }
    fun downloadTemplates() = perform("templates", R.string.offline_resources_download) {
        val missing = state.value.entries.filter { it.category == "template" && !it.ready && !it.bundled }
        val total = missing.sumOf(HubEntry::bytes)
        var completed = 0L
        for (entry in missing) {
            mutable.update { it.copy(transferId = entry.id) }
            PackResources.download(context, entry.id, phase) { done, _ -> progress(completed + done, total) }
            completed += entry.bytes
            readSnapshot()
        }
    }
    fun delete(entry: HubEntry, environment: Boolean = false) = perform(entry.id, R.string.resource_delete, deleting = true) {
        check(!entry.bundled && !entry.inUse)
        when {
            entry.legacy?.id == OfflineResources.UBUNTU -> {
                if (environment) { recoveryAttempted = true; OfflineResources.delete(context, entry.id) }
                else OfflineResources.deleteUbuntuArchive(context)
            }
            entry.legacy != null -> OfflineResources.delete(context, entry.id)
            entry.model != null -> repo.models.delete(entry.model)
            entry.pack != null -> PackResources.delete(context, entry.id)
        }
    }
    fun importResource(id: String, uri: Uri) = perform(id, R.string.offline_resources_import) {
        val entry = repo.snapshot().entries.first { it.id == id }
        check(!entry.bundled)
        when {
            entry.legacy != null -> OfflineResources.importZip(context, entry.id, uri, progress)
            entry.pack != null -> checkNotNull(context.contentResolver.openInputStream(uri)).use { PackResources.importArchive(context, entry.id, it, documentName(uri), progress) }
            entry.model != null -> createPreview(uri, entry.model.purpose, entry.model.name, entry.model.key)
        }
    }
    fun preview(uri: Uri, purpose: SpeechModelPurpose, name: String) = perform("model-import", R.string.offline_resources_import) { createPreview(uri, purpose, name) }
    private suspend fun createPreview(uri: Uri, purpose: SpeechModelPurpose, name: String, expectedKey: String? = null) {
        discardPreviewFiles()
        val preview = checkNotNull(context.contentResolver.openInputStream(uri)).use { repo.models.preview(it, documentName(uri), purpose, name, progress) }
        try {
            require(expectedKey == null || preview.model.key == expectedKey) { "导入模型与当前条目不一致，请通过探索 / 导入添加其他模型" }
            currentCoroutineContext().ensureActive()
            mutable.update { it.copy(preview = preview) }
        } catch (failure: Throwable) { preview.stage.deleteRecursively(); throw failure }
    }
    fun installPreview() {
        val preview = state.value.preview ?: return
        perform(preview.model.key, R.string.offline_resources_import) {
            try { repo.models.install(preview, progress) } finally { preview.stage.deleteRecursively(); mutable.update { it.copy(preview = null) } }
        }
    }
    fun discardPreview() {
        if (task?.isActive == true) return
        val preview = state.value.preview ?: return
        mutable.update { it.copy(preview = null) }
        viewModelScope.launch(Dispatchers.IO) { preview.stage.deleteRecursively() }
    }
    private fun discardPreviewFiles() {
        val preview = state.value.preview
        mutable.update { it.copy(preview = null) }
        preview?.stage?.deleteRecursively()
    }
    fun downloadRepository(input: String, source: ModelDownloadSource, revision: String, purpose: SpeechModelPurpose, name: String) = perform("repository", R.string.offline_resources_download) {
        val model = SpeechModelRepository(context).resolve(input, source, revision, purpose, name)
        val actualSource = ModelRepository.parse(input, source, revision).source
        repo.models.download(model, actualSource, phase, progress)
    }
    fun export(id: String, uri: Uri) = perform(id, R.string.resource_save_backup) {
        try {
            val entry = repo.snapshot().entries.first { it.id == id }
            when {
                entry.legacy != null -> OfflineResources.exportZip(context, entry.id, uri, progress)
                entry.model != null -> checkNotNull(context.contentResolver.openOutputStream(uri)).use { repo.models.export(entry.model, it, progress) }
                entry.pack != null -> checkNotNull(context.contentResolver.openOutputStream(uri)).use { PackResources.exportArchive(context, entry.id, it, progress) }
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                try { DocumentsContract.deleteDocument(context.contentResolver, uri) } catch (cleanup: Exception) { AppLogger.w("ResourceHub", "无法清理未完成的备份文档", cleanup) }
            }
            throw failure
        }
    }
    private fun documentName(uri: Uri): String = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: error("无法读取资源文件名称")
    override fun onCleared() {
        super.onCleared()
        state.value.preview?.stage?.deleteRecursively()
    }
}
