package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview

internal fun locatorVisibleEntries(
    entries: List<ChatMessageLocatorPreview>,
): List<ChatMessageLocatorPreview> =
    entries.mapIndexedNotNull { index, entry ->
        if (entry.resolvedDisplayMode.isCollaborationEvent) null
        else entry.copy(messageIndex = entry.messageIndex ?: index)
    }
