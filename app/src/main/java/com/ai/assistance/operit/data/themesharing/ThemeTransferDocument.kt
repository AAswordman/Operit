package com.ai.assistance.operit.data.themesharing

import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.layout.ChatLayoutSettings
import java.io.File
import com.ai.assistance.operit.data.model.theme.ThemeScriptManifest

internal enum class ThemeTransferError {
    INVALID_CODE,
    INVALID_CONFIG,
    UNSUPPORTED_VERSION,
    MISSING_MANIFEST,
    INVALID_ARCHIVE,
    MISSING_ASSET,
    TOO_LARGE,
    IO_ERROR,
    CHARACTER_MISSING,
    OVERWRITE_REQUIRED,
}

internal class ThemeTransferException(val error: ThemeTransferError, cause: Throwable? = null) :
    Exception(error.name, cause)

internal data class ThemeTransferDocument(
    val name: String,
    val author: String,
    val values: ThemePreferenceValues,
    val fields: Set<String>,
    val layout: ChatLayoutSettings,
    val full: Boolean,
    val script: ThemeScriptManifest? = null,
)

internal class PreparedTheme(
    val document: ThemeTransferDocument,
    val directory: File? = null,
    val assets: Map<String, File> = emptyMap(),
    val scriptPreviewFile: File? = null,
) : AutoCloseable {
    override fun close() {
        directory?.deleteRecursively()
    }
}
