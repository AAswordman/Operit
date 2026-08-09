package com.ai.assistance.operit.data.persistence

internal object StorageProfileIdPolicy {
    fun isSafeMemorySpaceId(profileId: String): Boolean =
        profileId.isNotBlank() &&
            profileId.length <= 128 &&
            profileId != "." &&
            profileId != ".." &&
            '/' !in profileId &&
            '\\' !in profileId &&
            '\u0000' !in profileId
}
