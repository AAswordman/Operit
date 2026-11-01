package com.ai.assistance.operit.ui.features.management.databackup.themes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.themesharing.*
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ThemeTransferPanel {
    CLOSED,
    EXPORT,
    GENERATED,
    IMPORT,
    PREVIEW,
}

internal enum class ThemeTransferMethod {
    CODE,
    FILE,
}

internal data class ThemeTransferUiState(
    val panel: ThemeTransferPanel = ThemeTransferPanel.CLOSED,
    val method: ThemeTransferMethod = ThemeTransferMethod.CODE,
    val source: ThemeTransferSource? = null,
    val name: String = "",
    val author: String = "",
    val input: String = "",
    val generated: String = "",
    val prepared: PreparedTheme? = null,
    val previewValues: ThemePreferenceValues? = null,
    val targets: List<CharacterCard> = emptyList(),
    val selectedId: String? = null,
    val overwriteTargetId: String? = null,
    val busy: Boolean = false,
    val error: ThemeTransferError? = null,
    val pendingArchive: File? = null,
    val notification: Boolean = false,
)

internal class ThemeTransferViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val repository = ThemeTransferRepository(app)
    private val cache = File(app.cacheDir, "theme-transfer/${UUID.randomUUID()}").apply { mkdirs() }
    private var cleared = false
    private val mutable = MutableStateFlow(ThemeTransferUiState())
    val state = mutable.asStateFlow()

    fun openExport() {
        if (mutable.value.busy) return
        close()
        mutable.value = ThemeTransferUiState(panel = ThemeTransferPanel.EXPORT)
        operation {
            val source = withContext(Dispatchers.IO) { repository.source() }
            mutable.update { it.copy(source = source, name = source.name) }
        }
    }

    fun openImport() {
        if (mutable.value.busy) return
        close()
        mutable.value = ThemeTransferUiState(panel = ThemeTransferPanel.IMPORT)
        operation {
            val targets = withContext(Dispatchers.IO) { repository.targets() }
            mutable.update { it.copy(targets = targets) }
        }
    }

    fun name(value: String) {
        mutable.update { it.copy(name = value.take(80), error = null) }
    }

    fun author(value: String) {
        mutable.update { it.copy(author = value.take(80), error = null) }
    }

    fun input(value: String) {
        mutable.update {
            it.copy(
                input = value,
                error = if (value.length > 65536) ThemeTransferError.TOO_LARGE else null,
            )
        }
    }

    fun method(value: ThemeTransferMethod) {
        if (mutable.value.busy || mutable.value.method == value) return
        mutable.value.prepared?.close()
        mutable.update {
            it.copy(method = value, prepared = null, previewValues = null, error = null)
        }
    }

    fun generate() = operation {
        val state = mutable.value
        val source = requireNotNull(state.source)
        val code =
            withContext(Dispatchers.IO) {
                ThemeShareCode.encode(
                    ThemeTransferSchema.create(
                        state.name,
                        state.author,
                        source.values,
                        source.layout,
                        false,
                    )
                )
            }
        mutable.update { it.copy(generated = code, panel = ThemeTransferPanel.GENERATED) }
    }

    fun prepareExport() = operation {
        val state = mutable.value
        val file =
            withContext(Dispatchers.IO) {
                repository.createArchive(
                    requireNotNull(state.source),
                    state.name,
                    state.author,
                    cache,
                )
            }
        mutable.update { it.copy(pendingArchive = file) }
    }

    fun saveExport(uri: Uri?) {
        val archive = mutable.value.pendingArchive ?: return
        if (uri == null) {
            archive.delete()
            mutable.update { it.copy(pendingArchive = null) }
            return
        }
        operation {
            try {
                withContext(Dispatchers.IO) { repository.saveArchive(archive, uri) }
                mutable.update { it.copy(panel = ThemeTransferPanel.CLOSED, notification = true) }
            } finally {
                archive.delete()
                mutable.update { it.copy(pendingArchive = null) }
            }
        }
    }

    fun readFile(uri: Uri) = operation {
        mutable.value.prepared?.close()
        mutable.update { it.copy(prepared = null, previewValues = null) }
        val prepared = withContext(Dispatchers.IO) { repository.readArchive(uri, cache) }
        mutable.update { it.copy(prepared = prepared) }
    }

    fun preview() = operation {
        val state = mutable.value
        val prepared =
            if (state.method == ThemeTransferMethod.CODE) {
                withContext(Dispatchers.IO) { PreparedTheme(ThemeShareCode.decode(state.input)) }
            } else requireNotNull(state.prepared)
        val values = previewValues(prepared, state.selectedId)
        mutable.update {
            it.copy(prepared = prepared, previewValues = values, panel = ThemeTransferPanel.PREVIEW)
        }
    }

    fun select(id: String) = operation {
        val theme = requireNotNull(mutable.value.prepared)
        val values = previewValues(theme, id)
        mutable.update {
            it.copy(selectedId = id, previewValues = values, overwriteTargetId = null)
        }
    }

    private suspend fun previewValues(theme: PreparedTheme, id: String?): ThemePreferenceValues =
        withContext(Dispatchers.IO) {
            val current =
                if (id == null) ThemePreferenceValues.defaultVisual()
                else {
                    UserPreferencesManager.getInstance(app)
                        .resolveThemePreferenceSnapshot(characterCardId = id)
                        .values
                }
            var values = ThemeTransferSchema.merge(current, theme.document)
            if (theme.document.full) {
                theme.document.values.strings
                    .filterKeys { it in ThemeTransferSchema.assetKeys }
                    .forEach { (key, path) ->
                        val file = theme.assets.getValue(path)
                        values =
                            values.withString(
                                key,
                                if (key.endsWith("font_path")) file.absolutePath
                                else Uri.fromFile(file).toString(),
                            )
                    }
            }
            theme.scriptPreviewFile?.let { file ->
                values = values.withString(ThemePreferenceValues.SCRIPT_URI_KEY, Uri.fromFile(file).toString())
            }
            values
        }

    fun apply(overwrite: Boolean = false) = operation {
        val state = mutable.value
        val id = requireNotNull(if (overwrite) state.overwriteTargetId else state.selectedId)
        try {
            withContext(Dispatchers.IO) {
                repository.apply(requireNotNull(state.prepared), id, overwrite)
            }
            state.prepared?.close()
            mutable.update {
                it.copy(
                    panel = ThemeTransferPanel.CLOSED,
                    prepared = null,
                    overwriteTargetId = null,
                    notification = true,
                )
            }
        } catch (e: ThemeTransferException) {
            if (e.error != ThemeTransferError.OVERWRITE_REQUIRED) throw e
            mutable.update { it.copy(overwriteTargetId = id) }
        }
    }

    fun cancelOverwrite() {
        mutable.update { it.copy(overwriteTargetId = null) }
    }

    fun consumeNotification() {
        mutable.update { it.copy(notification = false) }
    }

    fun close() {
        if (mutable.value.busy) return
        mutable.value.prepared?.close()
        mutable.value.pendingArchive?.delete()
        mutable.update {
            it.copy(
                panel = ThemeTransferPanel.CLOSED,
                prepared = null,
                previewValues = null,
                pendingArchive = null,
                overwriteTargetId = null,
                error = null,
            )
        }
    }

    private fun operation(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("ThemeTransfer", "主题导入导出失败", e)
                mutable.update {
                    it.copy(
                        error =
                            if (e is ThemeTransferException) e.error
                            else ThemeTransferError.IO_ERROR
                    )
                }
            } finally {
                mutable.update { it.copy(busy = false) }
                if (cleared) cache.deleteRecursively()
            }
        }
    }

    override fun onCleared() {
        cleared = true
        // 后台导入保存完成后再清理缓存，避免销毁页面时删掉正在复制的素材。
        if (!mutable.value.busy) cache.deleteRecursively()
        super.onCleared()
    }
}
