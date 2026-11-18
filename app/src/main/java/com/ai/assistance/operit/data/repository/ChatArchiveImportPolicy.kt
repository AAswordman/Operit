package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.OperitChatArchive

internal object ChatArchiveImportPolicy {
    private val supportedVersions = setOf(2, 5)

    fun validateHeader(archiveType: String, formatVersion: Int) {
        require(archiveType == OperitChatArchive.ARCHIVE_TYPE) {
            "Unsupported archive type: $archiveType"
        }
        require(formatVersion in supportedVersions) {
            "Unsupported archive version: $formatVersion"
        }
    }

}
