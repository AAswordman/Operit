package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.OperitChatArchive

internal object ChatArchiveImportPolicy {
    private val supportedVersions = setOf(2, 3, 4)

    fun validateHeader(archiveType: String, formatVersion: Int) {
        require(archiveType == OperitChatArchive.ARCHIVE_TYPE) {
            "Unsupported archive type: $archiveType"
        }
        require(formatVersion in supportedVersions) {
            "Unsupported archive version: $formatVersion"
        }
    }

    fun resolveFavorite(
        formatVersion: Int,
        archivedFavorite: Boolean?,
        localFavorite: Boolean,
    ): Boolean {
        return when (formatVersion) {
            2 -> localFavorite
            3, 4 -> requireNotNull(archivedFavorite) {
                "Archive v$formatVersion chat is missing isFavorite"
            }
            else -> throw IllegalArgumentException("Unsupported archive version: $formatVersion")
        }
    }
}
