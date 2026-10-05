package com.ai.assistance.operit.data.preferences

enum class OrientationReloadPolicy {
    ALWAYS,
    ASK,
    NEVER;

    companion object {
        fun fromValue(value: String?): OrientationReloadPolicy =
            entries.firstOrNull { it.name == value } ?: ASK
    }
}
