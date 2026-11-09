package com.ai.assistance.operit.services.core

internal fun resolveTranscriptRoleName(
    override: String?,
    fallback: String,
): String = override?.trim()?.takeIf(String::isNotEmpty) ?: fallback
