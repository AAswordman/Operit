package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.storage

import kotlinx.serialization.Serializable

@Serializable
internal data class UserscriptEntity(
    val id: Long = 0,
    val name: String,
    val namespace: String? = null,
    val version: String,
    val description: String? = null,
    val author: String? = null,
    val homepageUrl: String? = null,
    val sourceUrl: String? = null,
    val sourceDisplay: String? = null,
    val downloadUrl: String? = null,
    val updateUrl: String? = null,
    val runAt: String,
    val noFrames: Boolean,
    val enabled: Boolean,
    val sourceHash: String,
    val scriptFilePath: String,
    val installSourceType: String,
    val metadataJson: String = "",
    val grantsJson: String,
    val matchesJson: String,
    val includesJson: String,
    val excludesJson: String,
    val excludeMatchesJson: String,
    val connectsJson: String,
    val requiresJson: String,
    val resourcesJson: String,
    val installedAt: Long,
    val updatedAt: Long
)

@Serializable
internal data class UserscriptValueEntity(
    val userscriptId: Long,
    val storageKey: String,
    val valueJson: String,
    val updatedAt: Long
)

@Serializable
internal data class UserscriptResourceEntity(
    val id: Long = 0,
    val userscriptId: Long,
    val entryType: String,
    val resourceKey: String,
    val remoteUrl: String,
    val localPath: String,
    val mimeType: String? = null,
    val etag: String? = null,
    val lastModifiedHeader: String? = null,
    val updatedAt: Long
)

@Serializable
internal data class UserscriptLogEntity(
    val id: Long = 0,
    val userscriptId: Long? = null,
    val level: String,
    val message: String,
    val pageUrl: String? = null,
    val createdAt: Long
)
